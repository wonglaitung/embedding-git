package com.example.gitserver.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;

import jakarta.servlet.http.HttpServletRequest;

public class FileRepositoryResolver implements RepositoryResolver<HttpServletRequest> {

    private final Path root;

    public FileRepositoryResolver(Path root) {
        this.root = root;
    }

    @Override
    public Repository open(HttpServletRequest request, String name)
            throws RepositoryNotFoundException, ServiceNotEnabledException, ServiceNotAuthorizedException {
        Path repositoryDir = root.resolve(name).normalize();

        if (!repositoryDir.startsWith(root) || !Files.isDirectory(repositoryDir)) {
            throw new RepositoryNotFoundException(name);
        }

        try {
            return new FileRepositoryBuilder()
                    .setGitDir(repositoryDir.toFile())
                    .setBare()
                    .build();
        } catch (IOException e) {
            throw new RepositoryNotFoundException(name, e);
        }
    }
}
