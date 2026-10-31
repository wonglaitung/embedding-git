package com.example.gitserver.config;

import com.example.gitserver.git.FileRepositoryResolver;

import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class GitServerConfig {

    private static final Logger log = LoggerFactory.getLogger(GitServerConfig.class);

    @Bean
    RepositoryResolver<HttpServletRequest> repositoryResolver(GitProperties properties) {
        return new FileRepositoryResolver(properties.getRoot().toAbsolutePath().normalize());
    }

    @Bean
    UploadPackFactory<HttpServletRequest> uploadPackFactory() {
        return (request, repository) -> new UploadPack(repository);
    }

    @Bean
    ReceivePackFactory<HttpServletRequest> receivePackFactory(GitProperties properties) {
        return (request, repository) -> {
            if (!properties.isAllowPush()) {
                throw new ServiceNotEnabledException();
            }
            ReceivePack receivePack = new ReceivePack(repository);
            receivePack.setPreReceiveHook((pack, commands) ->
                    commands.forEach(command -> log.info("push {} {} -> {}",
                            repository.getIdentifier(),
                            command.getOldId() == null ? "0000000" : command.getOldId().name(),
                            command.getNewId().name())));
            return receivePack;
        };
    }

    @Bean
    ServletRegistrationBean<GitServlet> gitServlet(
            RepositoryResolver<HttpServletRequest> repositoryResolver,
            UploadPackFactory<HttpServletRequest> uploadPackFactory,
            ReceivePackFactory<HttpServletRequest> receivePackFactory) {

        GitServlet servlet = new GitServlet();
        servlet.setRepositoryResolver(repositoryResolver);
        servlet.setUploadPackFactory(uploadPackFactory);
        servlet.setReceivePackFactory(receivePackFactory);

        ServletRegistrationBean<GitServlet> registration = new ServletRegistrationBean<>(servlet, "/git/*");
        registration.setName("gitServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
