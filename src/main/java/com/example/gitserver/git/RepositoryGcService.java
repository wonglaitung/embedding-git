package com.example.gitserver.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.example.gitserver.config.GitProperties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.git", name = "gc-enabled", havingValue = "true", matchIfMissing = true)
public class RepositoryGcService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryGcService.class);

    private final Path root;

    public RepositoryGcService(GitProperties properties) {
        this.root = properties.getRoot().toAbsolutePath().normalize();
    }

    @Scheduled(cron = "${app.git.gc-cron:0 0 3 * * *}")
    public void scheduledGc() {
        if (!Files.isDirectory(root)) {
            return;
        }
        log.info("Starting repository GC under {}", root);

        int succeeded = 0;
        int failed = 0;
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> repositories = stream
                    .filter(path -> Files.isDirectory(path) && Files.exists(path.resolve("HEAD")))
                    .toList();
            for (Path repository : repositories) {
                try {
                    gc(repository);
                    succeeded++;
                } catch (Exception e) {
                    failed++;
                    log.warn("GC failed for {}", repository, e);
                }
            }
        } catch (IOException e) {
            log.warn("Could not scan repositories for GC", e);
            return;
        }
        log.info("Repository GC finished: {} succeeded, {} failed", succeeded, failed);
    }

    private void gc(Path repositoryDirectory) throws Exception {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repositoryDirectory.toFile())
                .setBare()
                .build();
             Git git = Git.wrap(repository)) {
            git.gc().call();
        }
        log.info("GC completed for {}", repositoryDirectory.getFileName());
    }
}
