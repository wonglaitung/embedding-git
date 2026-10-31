package com.example.gitserver.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import com.example.gitserver.service.RepositoryAdminService;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class RepositoryListController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RepositoryAdminService adminService;

    public RepositoryListController(RepositoryAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String list(@RequestParam(required = false) String ok,
                       @RequestParam(required = false) String error) throws IOException {
        Path root = adminService.getRoot();
        Files.createDirectories(root);

        List<RepositoryView> repositories;
        try (Stream<Path> stream = Files.list(root)) {
            repositories = stream
                    .filter(path -> Files.isDirectory(path) && Files.exists(path.resolve("HEAD")))
                    .sorted()
                    .map(this::describe)
                    .toList();
        }

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return render(baseUrl, repositories, ok, error);
    }

    @PostMapping("/repos")
    public String create(@RequestParam String name, RedirectAttributes redirect) {
        try {
            String repository = adminService.createRepository(name);
            redirect.addAttribute("ok", "已创建仓库 " + repository);
            return "redirect:/repo/" + repository;
        } catch (Exception e) {
            redirect.addAttribute("error", messageOf(e));
            return "redirect:/";
        }
    }

    private RepositoryView describe(Path repositoryDir) {
        try (Repository repository = adminService.open(repositoryDir)) {
            ObjectId head = repository.resolve(Constants.HEAD);
            String summary = "(空仓库)";
            Instant committedAt = null;

            if (head != null) {
                try (RevWalk walk = new RevWalk(repository)) {
                    RevCommit commit = walk.parseCommit(head);
                    summary = commit.getShortMessage();
                    committedAt = Instant.ofEpochSecond(commit.getCommitTime());
                }
            }

            return new RepositoryView(repositoryDir.getFileName().toString(),
                    repository.getBranch(), summary, committedAt);
        } catch (IOException e) {
            return new RepositoryView(repositoryDir.getFileName().toString(),
                    "?", "(无法读取: " + e.getMessage() + ")", null);
        }
    }

    private String render(String baseUrl, List<RepositoryView> repositories, String ok, String error) {
        StringBuilder rows = new StringBuilder();
        if (repositories.isEmpty()) {
            rows.append("<tr><td colspan=\"5\" class=\"empty\">还没有仓库，先在下方创建一个。</td></tr>");
        }
        for (RepositoryView repo : repositories) {
            String cloneUrl = baseUrl + "/git/" + repo.name();
            rows.append("<tr>")
                    .append("<td class=\"name\"><a href=\"/repo/").append(Html.escape(repo.name())).append("\">")
                    .append(Html.escape(repo.name())).append("</a></td>")
                    .append("<td>").append(Html.escape(repo.branch())).append("</td>")
                    .append("<td>").append(Html.escape(repo.summary())).append("</td>")
                    .append("<td>").append(repo.committedAt() == null ? "-" : FMT.format(repo.committedAt()))
                    .append("</td>")
                    .append("<td><code>git clone ").append(Html.escape(cloneUrl)).append("</code></td>")
                    .append("</tr>");
        }

        String body = """
                <h1>Git 仓库</h1>
                """ + Html.banner(ok, error) + """
                <p class="muted">仓库通过 Git smart HTTP 协议提供。点击仓库名可上传文件或目录；浏览器不能浏览文件内容。</p>
                <table>
                  <thead>
                    <tr><th>仓库</th><th>分支</th><th>最近提交</th><th>提交时间</th><th>Clone</th></tr>
                  </thead>
                  <tbody>
                """ + rows + """
                  </tbody>
                </table>

                <div class="card">
                  <h2>新建仓库</h2>
                  <form method="post" action="/repos">
                    <label for="name">仓库名（可自动补 .git）</label>
                    <input id="name" type="text" name="name" placeholder="my-project"
                           pattern="[A-Za-z0-9][A-Za-z0-9._-]{0,63}" required>
                    <button type="submit">创建</button>
                  </form>
                </div>
                """;

        return Html.page("Git 仓库", body);
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record RepositoryView(String name, String branch, String summary, Instant committedAt) {
    }
}
