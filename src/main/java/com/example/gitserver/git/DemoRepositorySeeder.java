package com.example.gitserver.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.example.gitserver.config.GitProperties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoRepositorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRepositorySeeder.class);

    private static final String README = """
            # Demo repository

            This repository is served by an embedded JGit HTTP server running
            inside a Spring Boot process.

            Try it:

                git clone http://localhost:8080/git/demo.git

            Files in this repository are plain text so you can verify the clone.
            """;

    private static final String GUIDE = """
            # Sync guide

            1. Clone the repository.
            2. Edit files locally.
            3. Commit and push (push is enabled in this prototype).
            """;

    private static final String NOTES = """
            server-side file list
            ---------------------
            README.md
            docs/guide.md
            data/notes.txt
            """;

    private final GitProperties properties;

    public DemoRepositorySeeder(GitProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path root = properties.getRoot().toAbsolutePath().normalize();
        Files.createDirectories(root);

        if (!properties.isSeedDemo()) {
            return;
        }

        Path target = root.resolve("demo.git");
        if (Files.exists(target.resolve("HEAD"))) {
            log.info("Demo repository already exists at {}", target);
            return;
        }

        Path workTree = Files.createTempDirectory("git-seed-");
        try {
            seedWorkTree(workTree);
            cloneBare(workTree, target);
            log.info("Seeded demo repository at {} (clone: http://localhost:8080/git/demo.git)", target);
        } finally {
            deleteRecursively(workTree);
        }
    }

    private void seedWorkTree(Path dir) throws Exception {
        try (Git git = Git.init()
                .setDirectory(dir.toFile())
                .setInitialBranch("main")
                .call()) {

            Files.writeString(dir.resolve("README.md"), README, StandardCharsets.UTF_8);

            Path docs = Files.createDirectories(dir.resolve("docs"));
            Files.writeString(docs.resolve("guide.md"), GUIDE, StandardCharsets.UTF_8);

            Path data = Files.createDirectories(dir.resolve("data"));
            Files.writeString(data.resolve("notes.txt"), NOTES, StandardCharsets.UTF_8);

            PersonIdent author = new PersonIdent("Git Server", "git@example.com");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("Initial commit")
                    .setAuthor(author)
                    .setCommitter(author)
                    .call();
        }
    }

    private void cloneBare(Path source, Path target) throws Exception {
        try (Git ignored = Git.cloneRepository()
                .setURI(source.toUri().toString())
                .setDirectory(target.toFile())
                .setBare(true)
                .call()) {
            // closing the clone is enough; the bare repository is now on disk
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete {}", path, e);
                }
            });
        }
    }
}
