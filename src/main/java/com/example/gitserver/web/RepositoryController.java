package com.example.gitserver.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.example.gitserver.service.RepositoryAdminService;
import com.example.gitserver.service.UploadedFile;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Controller
public class RepositoryController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RepositoryAdminService adminService;
    private final String version;

    public RepositoryController(RepositoryAdminService adminService,
                                @Value("${app.version:dev}") String version) {
        this.adminService = adminService;
        this.version = version;
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
                    <div class="card" style="text-align:center;padding:42px 24px;">
                      <h1>仓库不存在</h1>
                      <p class="muted">""" + Html.escape(messageOf(e)) + """
                    </p>
                      <p><a class="btn btn-primary" href="/">返回仓库列表</a></p>
                    </div>
                    """, version);
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

    @PostMapping("/repo/{name}/delete")
    public String delete(@PathVariable String name,
                         @RequestParam String path,
                         RedirectAttributes redirect) {
        String repoName = RepositoryAdminService.canonicalName(name);
        try {
            adminService.deleteFiles(repoName, List.of(path), "Delete " + path,
                    new PersonIdent("Web Uploader", "web@example.com"));
            redirect.addAttribute("ok", "已删除 " + path);
        } catch (Exception e) {
            redirect.addAttribute("error", messageOf(e));
        }
        return "redirect:/repo/" + repoName;
    }

    @GetMapping("/repo/{name}/raw/{*path}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String name,
                                                          @PathVariable String path) {
        String repoName = RepositoryAdminService.canonicalName(name);
        String cleanPath = normalizeRepoPath(path);
        String fileName = cleanPath.substring(cleanPath.lastIndexOf('/') + 1);

        Path directory;
        try {
            directory = adminService.resolveExisting(repoName);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "仓库不存在", e);
        }

        ObjectId blobId;
        long size;
        try (Repository repository = adminService.open(directory)) {
            ObjectId head = repository.resolve(Constants.HEAD);
            if (head == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "仓库为空");
            }
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(head);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, cleanPath, commit.getTree())) {
                    if (treeWalk == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在：" + cleanPath);
                    }
                    if (treeWalk.getFileMode(0) == FileMode.TREE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录不能直接下载");
                    }
                    blobId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(blobId);
                    size = loader.getSize();
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文件失败", e);
        }

        StreamingResponseBody stream = output -> {
            try (Repository repository = adminService.open(directory)) {
                repository.open(blobId).copyTo(output);
            }
        };

        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(size)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(stream);
    }

    private static String normalizeRepoPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径为空");
        }
        String cleaned = rawPath.replace('\\', '/').replaceAll("^/+", "");
        for (String segment : cleaned.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || segment.equalsIgnoreCase(".git")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件路径：" + rawPath);
            }
        }
        return cleaned;
    }

    private String render(String name, Repository repository, String ok, String error) throws IOException {
        String branch = repository.getBranch();
        String summary = "(空仓库)";
        String shortId = "-";
        String committedAt = "尚未提交";
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

        StringBuilder tree = new StringBuilder();
        if (files.isEmpty()) {
            tree.append("<div class=\"empty-files\">空仓库，上传文件后会生成第一个提交。</div>");
        } else {
            tree.append("<ul class=\"tree\">");
            renderTree(name, buildTree(files), tree);
            tree.append("</ul>");
        }

        String cloneUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/git/" + name).build().toUriString();
        String copyButton = "<button class=\"btn btn-ghost\" type=\"button\" data-copy=\""
                + Html.escape(cloneUrl) + "\">复制</button>";

        String body = """
                <nav class="crumbs">
                  <a href="/">仓库</a><span>/</span><span>""" + Html.escape(name) + """
                </span>
                </nav>
                <section class="repo-header">
                  <div class="repo-title">
                    <span class="repo-icon">""" + Html.repoIcon() + """
                </span>
                    <h1>""" + Html.escape(name) + """
                </h1>
                    <span class="badge">""" + Html.escape(branch) + """
                </span>
                  </div>
                  <div class="repo-meta">
                    <span>最近提交 <code>""" + Html.escape(shortId) + """
                </code> """ + Html.escape(summary) + """
                </span>
                    <span>""" + Html.escape(committedAt) + """
                </span>
                  </div>
                  <div class="clone-row" style="max-width:640px;margin-top:14px;">
                    <code>git clone&#32;""" + Html.escape(cloneUrl) + """
                </code>
                    """ + copyButton + """
                  </div>
                </section>
                """ + Html.banner(ok, error) + """
                <div class="detail-grid">
                  <section class="card files-card">
                    <div class="card-head">
                      <h2>文件</h2>
                      <span class="muted small">""" + files.size() + """
                &#32;个文件</span>
                    </div>
                    """ + tree + """
                  </section>

                  <aside class="card upload-card upload-form">
                    <div class="card-head"><h2>上传文件或目录</h2></div>
                    <form id="uploadForm" method="post" action="/repo/""" + Html.escape(name) + """
                /upload" enctype="multipart/form-data">
                      <label class="dropzone" id="dropzone">
                        <span class="dropzone-icon">
                          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                               stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M12 16V4"></path>
                            <path d="M7 9l5-5 5 5"></path>
                            <path d="M20 16.5A3.5 3.5 0 0 0 18.5 10h-.6A6 6 0 0 0 6 11.5 3.5 3.5 0 0 0 4 18.5"></path>
                          </svg>
                        </span>
                        <strong id="dropzone-title">拖拽文件到此处，或点击选择</strong>
                        <span class="muted" id="dropzone-sub">目录请使用下方按钮</span>
                        <input id="filePicker" type="file" name="files" multiple hidden>
                      </label>
                      <input id="dirPicker" type="file" name="files" webkitdirectory multiple hidden>
                      <div class="picker-row">
                        <button type="button" class="btn btn-ghost" id="pickFiles">选择文件</button>
                        <button type="button" class="btn btn-ghost" id="pickDir">选择目录</button>
                      </div>
                      <label for="message">提交信息</label>
                      <input id="message" type="text" name="message" value="Upload files via web">
                      <label for="author">提交人</label>
                      <input id="author" type="text" name="author" value="Web Uploader">
                      <button class="btn btn-primary btn-block" type="submit">上传并提交</button>
                    </form>
                    <p class="hint">目录上传会保留子目录结构，所有文件以一次提交写入当前分支。</p>
                  </aside>
                </div>
                <script>
                  (function () {
                    var form = document.getElementById('uploadForm');
                    var dropzone = document.getElementById('dropzone');
                    var filePicker = document.getElementById('filePicker');
                    var dirPicker = document.getElementById('dirPicker');
                    var title = document.getElementById('dropzone-title');
                    var sub = document.getElementById('dropzone-sub');

                    function updateTitle() {
                      var count = filePicker.files.length + dirPicker.files.length;
                      if (count > 0) {
                        title.textContent = '已选择 ' + count + ' 个文件';
                        sub.textContent = '点击可重新选择';
                      } else {
                        title.textContent = '拖拽文件到此处，或点击选择';
                        sub.textContent = '目录请使用下方按钮';
                      }
                    }

                    filePicker.addEventListener('change', updateTitle);
                    dirPicker.addEventListener('change', updateTitle);
                    document.getElementById('pickFiles').addEventListener('click', function () { filePicker.click(); });
                    document.getElementById('pickDir').addEventListener('click', function () { dirPicker.click(); });

                    ['dragenter', 'dragover'].forEach(function (name) {
                      dropzone.addEventListener(name, function (event) {
                        event.preventDefault();
                        dropzone.classList.add('dragging');
                      });
                    });
                    ['dragleave', 'drop'].forEach(function (name) {
                      dropzone.addEventListener(name, function (event) {
                        event.preventDefault();
                        dropzone.classList.remove('dragging');
                      });
                    });
                    dropzone.addEventListener('drop', function (event) {
                      if (event.dataTransfer && event.dataTransfer.files.length) {
                        try {
                          filePicker.files = event.dataTransfer.files;
                          updateTitle();
                        } catch (e) {
                          sub.textContent = '该浏览器不支持拖拽，请点击选择';
                        }
                      }
                    });

                    form.addEventListener('submit', function () {
                      form.querySelectorAll('input[name="paths"]').forEach(function (node) { node.remove(); });
                      [filePicker, dirPicker].forEach(function (input) {
                        for (var i = 0; i < input.files.length; i++) {
                          var file = input.files[i];
                          var hidden = document.createElement('input');
                          hidden.type = 'hidden';
                          hidden.name = 'paths';
                          hidden.value = file.webkitRelativePath || file.name;
                          form.appendChild(hidden);
                        }
                      });
                    });

                    document.querySelectorAll('[data-copy]').forEach(function (button) {
                      button.addEventListener('click', function () {
                        var text = button.getAttribute('data-copy');
                        var done = function () {
                          var original = button.textContent;
                          button.textContent = '已复制';
                          setTimeout(function () { button.textContent = original; }, 1200);
                        };
                        if (navigator.clipboard && navigator.clipboard.writeText) {
                          navigator.clipboard.writeText(text).then(done).catch(function () {});
                        }
                      });
                    });

                    document.querySelectorAll('form.delete-form').forEach(function (form) {
                      form.addEventListener('submit', function (event) {
                        var name = form.getAttribute('data-name') || '';
                        if (!window.confirm('确定删除 “' + name + '” 吗？')) {
                          event.preventDefault();
                        }
                      });
                    });
                  })();
                </script>
                """;

        return Html.page(name, body, version);
    }

    private static Node buildTree(List<FileView> files) {
        Node root = new Node("", "", true);
        for (FileView file : files) {
            String[] parts = file.path().split("/");
            Node current = root;
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                boolean leaf = i == parts.length - 1;
                if (path.length() > 0) {
                    path.append('/');
                }
                path.append(parts[i]);
                Node child = current.children.get(parts[i]);
                if (child == null) {
                    child = new Node(parts[i], path.toString(), !leaf);
                    current.children.put(parts[i], child);
                }
                if (leaf) {
                    child.size = file.size();
                }
                current = child;
            }
        }
        return root;
    }

    private static void renderTree(String repoName, Node node, StringBuilder out) {
        List<Node> directories = new ArrayList<>();
        List<Node> files = new ArrayList<>();
        for (Node child : node.children.values()) {
            (child.directory ? directories : files).add(child);
        }
        directories.sort(Comparator.comparing(child -> child.name, String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(child -> child.name, String.CASE_INSENSITIVE_ORDER));

        for (Node directory : directories) {
            out.append("<li class=\"tree-item\"><div class=\"dir-row\"><span class=\"folder-icon\">")
                    .append(Html.folderIcon())
                    .append("</span><span class=\"file-name\">").append(Html.escape(directory.name))
                    .append("</span></div><ul>");
            renderTree(repoName, directory, out);
            out.append("</ul></li>");
        }
        for (Node file : files) {
            String href = "/repo/" + repoName + "/raw/"
                    + UriUtils.encodePath(file.path, StandardCharsets.UTF_8);
            out.append("<li class=\"tree-item\"><div class=\"file-row\"><span class=\"file-icon\">")
                    .append(Html.fileIcon())
                    .append("</span><a class=\"file-name file-link\" href=\"").append(href)
                    .append("\">").append(Html.escape(file.name)).append("</a>")
                    .append("<span class=\"file-size\">")
                    .append(file.size < 0 ? "-" : formatSize(file.size))
                    .append("</span><a class=\"file-download\" href=\"").append(href)
                    .append("\" download title=\"下载\">").append(Html.downloadIcon())
                    .append("</a><form class=\"delete-form\" method=\"post\" action=\"/repo/")
                    .append(Html.escape(repoName)).append("/delete\" data-name=\"")
                    .append(Html.escape(file.name)).append("\"><input type=\"hidden\" name=\"path\" value=\"")
                    .append(Html.escape(file.path)).append("\"><button class=\"file-delete\" type=\"submit\"")
                    .append(" title=\"删除\">").append(Html.trashIcon())
                    .append("</button></form></div></li>");
        }
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

    private static final class Node {
        private final String name;
        private final String path;
        private final boolean directory;
        private final Map<String, Node> children = new TreeMap<>();
        private long size = -1;

        private Node(String name, String path, boolean directory) {
            this.name = name;
            this.path = path;
            this.directory = directory;
        }
    }
}
