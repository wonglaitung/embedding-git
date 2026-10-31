package com.example.gitserver.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.example.gitserver.config.GitProperties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RepositoryAdminService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAdminService.class);
    private static final String DEFAULT_BRANCH = "main";

    private final Path root;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public RepositoryAdminService(GitProperties properties) {
        this.root = properties.getRoot().toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    public String createRepository(String rawName) throws IOException, GitAPIException {
        String name = normalizeName(rawName);
        Path directory = root.resolve(name).normalize();

        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("非法仓库名：" + rawName);
        }

        ReentrantLock lock = lock(name);
        lock.lock();
        try {
            if (Files.exists(directory)) {
                throw new IllegalArgumentException("仓库已存在：" + name);
            }
            Files.createDirectories(root);
            try (Git ignored = Git.init()
                    .setBare(true)
                    .setDirectory(directory.toFile())
                    .setInitialBranch(DEFAULT_BRANCH)
                    .call()) {
                // bare repository created on disk
            }
            log.info("Created repository {}", directory);
            return name;
        } finally {
            lock.unlock();
        }
    }

    public void commitUpload(String repoName, List<UploadedFile> files, String message, PersonIdent author)
            throws IOException, GitAPIException, URISyntaxException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("没有收到文件");
        }

        Path bareDirectory = resolveExisting(repoName);
        ReentrantLock lock = lock(repoName);
        lock.lock();

        Path workTree = Files.createTempDirectory("git-upload-");
        try {
            String branch;
            try (Repository bare = open(bareDirectory)) {
                branch = bare.getBranch();
            }
            if (branch == null || branch.isBlank()) {
                branch = DEFAULT_BRANCH;
            }

            try (Git git = prepareWorkTree(workTree, bareDirectory, branch)) {
                String localBranch = git.getRepository().getBranch();
                for (UploadedFile file : files) {
                    Path target = safeResolve(workTree, file.path());
                    Files.createDirectories(target.getParent());
                    Files.write(target, file.content());
                }

                git.add().addFilepattern(".").call();
                git.commit()
                        .setMessage(message)
                        .setAuthor(author)
                        .setCommitter(author)
                        .call();
                git.push()
                        .setRemote("origin")
                        .add("refs/heads/" + localBranch + ":refs/heads/" + localBranch)
                        .call();
            }
            log.info("Committed {} uploaded file(s) to {}", files.size(), repoName);
        } finally {
            deleteRecursively(workTree);
            lock.unlock();
        }
    }

    public void deleteFiles(String repoName, List<String> paths, String message, PersonIdent author)
            throws IOException, GitAPIException, URISyntaxException {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("没有指定要删除的文件");
        }

        Path bareDirectory = resolveExisting(repoName);
        ReentrantLock lock = lock(repoName);
        lock.lock();

        Path workTree = Files.createTempDirectory("git-delete-");
        try {
            String branch;
            try (Repository bare = open(bareDirectory)) {
                branch = bare.getBranch();
            }
            if (branch == null || branch.isBlank()) {
                branch = DEFAULT_BRANCH;
            }

            try (Git git = prepareWorkTree(workTree, bareDirectory, branch)) {
                String localBranch = git.getRepository().getBranch();
                RmCommand rm = git.rm();
                for (String path : paths) {
                    String cleanPath = normalizePath(path);
                    Path target = safeResolve(workTree, cleanPath);
                    if (Files.isDirectory(target)) {
                        throw new IllegalArgumentException("不能删除目录：" + cleanPath);
                    }
                    if (!Files.deleteIfExists(target)) {
                        throw new IllegalArgumentException("文件不存在：" + cleanPath);
                    }
                    rm.addFilepattern(cleanPath);
                }
                rm.call();
                git.commit()
                        .setMessage(message)
                        .setAuthor(author)
                        .setCommitter(author)
                        .call();
                git.push()
                        .setRemote("origin")
                        .add("refs/heads/" + localBranch + ":refs/heads/" + localBranch)
                        .call();
            }
            log.info("Deleted {} file(s) from {}", paths.size(), repoName);
        } finally {
            deleteRecursively(workTree);
            lock.unlock();
        }
    }

    public static String canonicalName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return name.endsWith(".git") ? name : name + ".git";
    }

    public Path resolveExisting(String repoName) {
        Path directory = root.resolve(repoName).normalize();
        if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("仓库不存在：" + repoName);
        }
        return directory;
    }

    public Repository open(Path directory) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(directory.toFile())
                .setBare()
                .build();
    }

    private Git prepareWorkTree(Path workTree, Path bareDirectory, String branch)
            throws IOException, GitAPIException, URISyntaxException {
        boolean empty;
        try (Repository bare = open(bareDirectory)) {
            empty = bare.resolve(Constants.HEAD) == null;
        }

        if (empty) {
            Git git = Git.init()
                    .setDirectory(workTree.toFile())
                    .setInitialBranch(branch)
                    .call();
            git.remoteAdd()
                    .setName("origin")
                    .setUri(new URIish(bareDirectory.toUri().toString()))
                    .call();
            return git;
        }

        return Git.cloneRepository()
                .setURI(bareDirectory.toUri().toString())
                .setDirectory(workTree.toFile())
                .call();
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("文件路径为空");
        }
        String raw = rawPath.trim();
        if (raw.startsWith("/") || raw.startsWith("\\") || raw.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("非法文件路径：" + rawPath);
        }
        String cleaned = raw.replace('\\', '/');
        for (String segment : cleaned.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || segment.equalsIgnoreCase(".git")) {
                throw new IllegalArgumentException("非法文件路径：" + rawPath);
            }
        }
        return cleaned;
    }

    private Path safeResolve(Path base, String rawPath) {
        String cleaned = normalizePath(rawPath);
        Path target = base.resolve(cleaned).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法文件路径：" + rawPath);
        }
        return target;
    }

    public static String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("仓库名不能为空");
        }
        String name = rawName.trim();
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || name.endsWith(".")) {
            throw new IllegalArgumentException("仓库名只能包含字母、数字、点、下划线、短横线，且不能以点结尾");
        }
        return name + ".git";
    }

    private ReentrantLock lock(String key) {
        return locks.computeIfAbsent(key, ignored -> new ReentrantLock());
    }

    private void deleteRecursively(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up {}", directory, e);
        }
    }
}
