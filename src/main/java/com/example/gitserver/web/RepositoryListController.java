package com.example.gitserver.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import com.example.gitserver.config.GitProperties;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class RepositoryListController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final GitProperties properties;

    public RepositoryListController(GitProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String list(HttpServletRequest request) throws IOException {
        Path root = properties.getRoot().toAbsolutePath().normalize();
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
        return render(baseUrl, repositories);
    }

    private RepositoryView describe(Path repositoryDir) {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repositoryDir.toFile())
                .setBare()
                .build()) {

            ObjectId head = repository.resolve(Constants.HEAD);
            String summary = "(empty repository)";
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
                    "?", "(unreadable: " + e.getMessage() + ")", null);
        }
    }

    private String render(String baseUrl, List<RepositoryView> repositories) {
        StringBuilder rows = new StringBuilder();
        if (repositories.isEmpty()) {
            rows.append("<tr><td colspan=\"4\" class=\"empty\">No repositories yet.</td></tr>");
        }
        for (RepositoryView repo : repositories) {
            String cloneUrl = baseUrl + "/git/" + repo.name();
            rows.append("<tr>")
                    .append("<td class=\"name\">").append(escape(repo.name())).append("</td>")
                    .append("<td>").append(escape(repo.branch())).append("</td>")
                    .append("<td>").append(escape(repo.summary())).append("</td>")
                    .append("<td>").append(repo.committedAt() == null ? "-" : FMT.format(repo.committedAt()))
                    .append("</td>")
                    .append("<td><code>git clone ").append(escape(cloneUrl)).append("</code></td>")
                    .append("</tr>");
        }

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Git repositories</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 1100px; padding: 0 1rem; }
                    h1 { font-size: 1.4rem; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { text-align: left; padding: .6rem .8rem; border-bottom: 1px solid #e5e7eb; }
                    th { background: #f9fafb; font-size: .8rem; text-transform: uppercase; color: #6b7280; }
                    td.name { font-weight: 600; }
                    code { background: #f3f4f6; padding: .15rem .4rem; border-radius: 4px; }
                    .empty { color: #9ca3af; text-align: center; }
                  </style>
                </head>
                <body>
                  <h1>Git repositories</h1>
                  <p>Repositories are served over the Git smart HTTP protocol. Clone with the command shown below.</p>
                  <table>
                    <thead>
                      <tr><th>Repository</th><th>Branch</th><th>Last commit</th><th>Committed at</th><th>Clone</th></tr>
                    </thead>
                    <tbody>
                """ + rows + """
                    </tbody>
                  </table>
                </body>
                </html>
                """;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record RepositoryView(String name, String branch, String summary, Instant committedAt) {
    }
}
