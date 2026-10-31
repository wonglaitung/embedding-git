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
import org.springframework.beans.factory.annotation.Value;
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
    private final String version;

    public RepositoryListController(RepositoryAdminService adminService,
                                    @Value("${app.version:dev}") String version) {
        this.adminService = adminService;
        this.version = version;
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
        StringBuilder cards = new StringBuilder();
        if (repositories.isEmpty()) {
            cards.append("""
                    <div class="empty-state">
                      <svg width="46" height="46" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                           stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
                        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
                      </svg>
                      <h2>还没有仓库</h2>
                      <p class="muted">用上方的表单创建第一个仓库。</p>
                    </div>
                    """);
        }

        for (RepositoryView repo : repositories) {
            String cloneUrl = baseUrl + "/git/" + repo.name();
            cards.append("<article class=\"repo-card\">")
                    .append("<div class=\"repo-card-head\">")
                    .append("<span class=\"repo-icon\">").append(Html.repoIcon()).append("</span>")
                    .append("<a class=\"repo-name\" href=\"/repo/").append(Html.escape(repo.name())).append("\">")
                    .append(Html.escape(repo.name())).append("</a>")
                    .append("<span class=\"badge\">").append(Html.escape(repo.branch())).append("</span>")
                    .append("</div>")
                    .append("<p class=\"repo-commit\">").append(Html.escape(repo.summary())).append("</p>")
                    .append("<p class=\"muted small\">")
                    .append(repo.committedAt() == null ? "尚未提交" : FMT.format(repo.committedAt()))
                    .append("</p>")
                    .append("<div class=\"clone-row\"><code>git clone ")
                    .append(Html.escape(cloneUrl)).append("</code>")
                    .append("<button class=\"btn btn-ghost\" type=\"button\" data-copy=\"")
                    .append(Html.escape(cloneUrl)).append("\">复制</button></div>")
                    .append("</article>");
        }

        String body = """
                <section class="hero">
                  <div>
                    <h1>Git 仓库</h1>
                    <p class="muted">通过 Git smart HTTP 提供仓库，也可以直接在网页上新建仓库、上传文件或目录。</p>
                  </div>
                  <div class="stat-card">
                    <strong>""" + repositories.size() + """
                </strong>
                    <span>个仓库</span>
                  </div>
                </section>
                """ + Html.banner(ok, error) + """
                <section class="card create-card">
                  <form class="create-form" method="post" action="/repos">
                    <div class="create-field">
                      <label for="name">新建仓库</label>
                      <input id="name" type="text" name="name" placeholder="my-project"
                             pattern="[A-Za-z0-9][A-Za-z0-9._-]{0,63}" required>
                    </div>
                    <button class="btn btn-primary" type="submit">创建仓库</button>
                  </form>
                  <p class="hint">仓库名会自动补 .git，只允许字母、数字、点、下划线、短横线。</p>
                </section>
                <div class="repo-grid">
                """ + cards + """
                </div>
                <script>
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
                </script>
                """;

        return Html.page("Git 仓库", body, version);
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record RepositoryView(String name, String branch, String summary, Instant committedAt) {
    }
}
