# AGENTS.md

Embedded Git smart-HTTP server on Spring Boot 3.5.16 + JGit 7.8.0 (Java 17).
Single Maven module, no tests, no lint/formatter config, no CI.

## Commands

- Build: `mvn -B -DskipTests package` -> `target/git-server-0.0.1.jar`
- Run: `mvn spring-boot:run` or `java -jar target/git-server-0.0.1.jar`
- There is no test suite; `mvn test` runs nothing. Verify manually with:
  `git clone http://localhost:8080/git/demo.git /tmp/clone-check`
- Default port is 8080. If startup fails, check `ss -ltnp | grep 8080`.

## URLs and behavior (most common confusion)

- Git protocol is under `/git/{repo}.git/...`. The `.git` suffix is required;
  `/git/demo` returns 404 by design.
- `GET /` is **not** a Git web UI. It lists repositories and has a create form.
  `GET /repo/{name}` shows file paths/sizes plus an upload form; there is no file
  content, diff, or history browsing.
- A demo bare repo `repos/demo.git` (branch `main`) is seeded on startup.
- `repos/` is runtime data and is gitignored. Delete it to reseed on next start;
  set `app.git.seed-demo=false` to disable.

## Architecture

- `GitServlet` is registered via `ServletRegistrationBean` on `/git/*`
  (`GitServerConfig.java:62`), not through a Spring MVC controller.
- `FileRepositoryResolver` maps the URL path to `app.git.root/<name>` as a bare
  `FileRepository` and rejects path traversal. The servlet opens/closes the repo
  per request.
- `UploadPackFactory` handles clone/fetch; `ReceivePackFactory` handles push.
  Push is enabled and unauthenticated by default; set `app.git.allow-push=false`
  to disable. No Spring Security is wired up.
- Web write paths: `POST /repos` (`RepositoryListController`) creates a bare repo;
  `POST /repo/{name}/upload` (`RepositoryController`) commits uploaded files via
  `RepositoryAdminService`, which does temp clone -> write -> commit -> push back.
  Writes are serialized per repo with a `ReentrantLock`.
- `RepositoryAdminService.canonicalName` appends `.git`, so `/repo/demo` and
  `/repo/demo.git` both work. Git protocol URLs still require `.git`.
- Add business logic in the factories via `PreReceiveHook`, `PostReceiveHook`,
  `AdvertiseRefsHook`, not in the list controller.
- Config binds from `app.git.*` in `application.yml` into `GitProperties`.

## Toolchain gotchas

- JGit 7.x targets `jakarta.servlet`; JGit 6.x targets `javax.servlet`. Keep
  JGit 7.x with Spring Boot 3.x. Downgrading to 6.x breaks servlet compatibility.
- `ServiceNotEnabledException` and `ServiceNotAuthorizedException` live in
  `org.eclipse.jgit.transport.resolver`, not `org.eclipse.jgit.transport`.
- Depend on `org.eclipse.jgit.http.server`; it pulls `org.eclipse.jgit` transitively.
- `DemoRepositorySeeder` creates a temp non-bare repo, commits, then clones it
  bare. If you replace it, do not hand-build trees with `TreeFormatter` without
  handling Git tree entry ordering.
- Web create/upload and `git push` are unauthenticated. Do not expose them as-is.
- Upload paths are validated in `RepositoryAdminService.safeResolve`: reject
  absolute paths, `..`, `.git`, and anything escaping the work tree.
- Multipart limits live under `spring.servlet.multipart` (20MB file / 200MB request).
- WSL2: the app binds `*:8080`. If a Windows browser on `localhost:8080` shows a
  different service, use the WSL IP (`hostname -I`).
