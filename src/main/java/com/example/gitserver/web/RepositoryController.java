package com.example.gitserver.web;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.example.gitserver.service.RepositoryAdminService;
import com.example.gitserver.service.UploadedFile;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class RepositoryController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RepositoryAdminService adminService;

    public RepositoryController(RepositoryAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping(value = "/repo/{name}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String detail(@PathVariable String name,
                         @RequestParam(required = false) String ok,
                         @RequestParam(required = false) String error) {
        String repoName = RepositoryAdminService.canonicalName(name);
        try {
            Path directory = adminService.resolveExisting(repoName);
            try (Repository repository = adminService.open(directory)) {
                return render(repoName, repository, ok, error);
            }
        } catch (Exception e) {
            return Html.page("仓库不存在", """
                    <h1>仓库不存在</h1>
                    <p class="muted">""" + Html.escape(messageOf(e)) + """
                    </p>
                    <p><a href="/">返回仓库列表</a></p>
                    """);
        }
    }

    @PostMapping("/repo/{name}/upload")
    public String upload(@PathVariable String name,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         @RequestParam(value = "paths", required = false) List<String> paths,
                         @RequestParam(defaultValue = "Upload files via web") String message,
                         @RequestParam(defaultValue = "Web Uploader") String author,
                         RedirectAttributes redirect) {
        String repoName = RepositoryAdminService.canonicalName(name);
        try {
            if (files == null || files.isEmpty()) {
                throw new IllegalArgumentException("请选择要上传的文件或目录");
            }

            List<UploadedFile> uploads = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file.isEmpty()) {
                    continue;
                }
                String path = paths != null && paths.size() == files.size()
                        ? paths.get(i)
                        : file.getOriginalFilename();
                uploads.add(new UploadedFile(path, file.getBytes()));
            }
            if (uploads.isEmpty()) {
                throw new IllegalArgumentException("没有有效的文件");
            }

            String commitMessage = message == null || message.isBlank() ? "Upload files via web" : message;
            String authorName = author == null || author.isBlank() ? "Web Uploader" : author;
            adminService.commitUpload(repoName, uploads, commitMessage,
                    new PersonIdent(authorName, "web@example.com"));
            redirect.addAttribute("ok", "已提交 " + uploads.size() + " 个文件");
        } catch (Exception e) {
            redirect.addAttribute("error", messageOf(e));
        }
        return "redirect:/repo/" + repoName;
    }

    private String render(String name, Repository repository, String ok, String error) throws IOException {
        String branch = repository.getBranch();
        String summary = "(空仓库)";
        String shortId = "-";
        String committedAt = "-";
        List<FileView> files = new ArrayList<>();

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head != null) {
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(head);
                summary = commit.getShortMessage();
                shortId = commit.abbreviate(8).name();
                committedAt = FMT.format(Instant.ofEpochSecond(commit.getCommitTime()));

                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    while (treeWalk.next()) {
                        long size = -1;
                        try {
                            size = repository.open(treeWalk.getObjectId(0)).getSize();
                        } catch (IOException ignored) {
                            // size stays unknown
                        }
                        files.add(new FileView(treeWalk.getPathString(), size));
                    }
                }
            }
            files.sort(Comparator.comparing(FileView::path));
        }

        StringBuilder rows = new StringBuilder();
        if (files.isEmpty()) {
            rows.append("<tr><td colspan=\"2\" class=\"empty\">空仓库，上传文件后会生成第一个提交。</td></tr>");
        }
        for (FileView file : files) {
            rows.append("<tr><td>").append(Html.escape(file.path())).append("</td><td>")
                    .append(file.size() < 0 ? "-" : formatSize(file.size()))
                    .append("</td></tr>");
        }

        String cloneUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/git/" + name).build().toUriString();

        String meta = "分支 <code>" + Html.escape(branch) + "</code> · 最近提交 <code>"
                + Html.escape(shortId) + "</code> " + Html.escape(summary)
                + " · " + Html.escape(committedAt);
        String cloneLine = "Clone：<code>git clone " + Html.escape(cloneUrl) + "</code>";

        String body = """
                <p><a href="/">← 返回仓库列表</a></p>
                <h1>""" + Html.escape(name) + """
                </h1>
                """ + Html.banner(ok, error) + """
                <p class="muted">""" + meta + """
                </p>
                <p class="muted">""" + cloneLine + """
                </p>

                <div class="card">
                  <h2>文件</h2>
                  <table>
                    <thead><tr><th>路径</th><th>大小</th></tr></thead>
                    <tbody>""" + rows + """
                </tbody>
                  </table>
                </div>

                <div class="card">
                  <h2>上传文件或目录</h2>
                  <form id="uploadForm" method="post" action="/repo/""" + Html.escape(name) + """
                /upload" enctype="multipart/form-data">
                    <div class="row">
                      <div>
                        <label for="filePicker">文件（可多选）</label>
                        <input id="filePicker" type="file" name="files" multiple>
                      </div>
                      <div>
                        <label for="dirPicker">目录（保留相对路径）</label>
                        <input id="dirPicker" type="file" name="files" webkitdirectory multiple>
                      </div>
                      <div>
                        <label for="message">提交信息</label>
                        <input id="message" type="text" name="message" value="Upload files via web">
                      </div>
                      <div>
                        <label for="author">提交人</label>
                        <input id="author" type="text" name="author" value="Web Uploader">
                      </div>
                    </div>
                    <button type="submit">上传并提交</button>
                  </form>
                  <p class="muted">目录上传会保留子目录结构。文件会以一次提交写入当前分支。</p>
                </div>

                <script>
                  document.getElementById('uploadForm').addEventListener('submit', function () {
                    this.querySelectorAll('input[name="paths"]').forEach(function (node) { node.remove(); });
                    var inputs = [document.getElementById('filePicker'), document.getElementById('dirPicker')];
                    for (var i = 0; i < inputs.length; i++) {
                      var selected = inputs[i].files;
                      for (var j = 0; j < selected.length; j++) {
                        var hidden = document.createElement('input');
                        hidden.type = 'hidden';
                        hidden.name = 'paths';
                        hidden.value = selected[j].webkitRelativePath || selected[j].name;
                        this.appendChild(hidden);
                      }
                    }
                  });
                </script>
                """;

        return Html.page(name, body);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record FileView(String path, long size) {
    }
}
