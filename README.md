# git-server

一个把 Git 服务端能力直接嵌入业务进程的示例：基于 **Spring Boot 3.5.16 + JGit 7.8.0**，对外提供 Git 的 **smart HTTP 协议**，支持 `git clone` / `fetch` / `pull` / `push`。

不需要安装原生 `git` 服务端，也不需要额外部署 Gitea/GitLab；Git 协议端点就跑在 Spring Boot 的 Tomcat 里。

## 环境要求

- JDK 17+
- Maven 3.6.3+
- Git 2.34+（仅用于验证客户端）

## 快速开始

```bash
# 构建
mvn -B -DskipTests package

# 运行（二选一）
mvn spring-boot:run
java -jar target/git-server-0.0.1-SNAPSHOT.jar
```

启动后会监听 `8080`，并自动创建示例仓库 `repos/demo.git`。

```bash
# 克隆
git clone http://localhost:8080/git/demo.git
```

## 访问地址

| 地址 | 说明 |
| --- | --- |
| `http://localhost:8080/` | 仓库列表页（简单 HTML 表格，**不是** Git Web UI） |
| `http://localhost:8080/git/demo.git` | Git 协议端点，仓库名必须带 `.git` |
| `http://localhost:8080/git/demo` | 返回 404，属于正常行为 |

浏览器无法直接浏览仓库文件或提交历史；仓库只能通过 Git 客户端访问。`/` 页面只列出仓库名、默认分支、最近提交和 clone 命令。

## 配置

配置位于 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080

app:
  git:
    root: ./repos      # 裸仓库根目录
    seed-demo: true    # 启动时自动创建 demo.git
    allow-push: true   # 是否允许 push
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.git.root` | `./repos` | 裸仓库所在目录，URL 中的 `{repo}.git` 会映射到该目录下 |
| `app.git.seed-demo` | `true` | 启动时若 `demo.git` 不存在则生成示例仓库 |
| `app.git.allow-push` | `true` | 关闭后 `git push` 返回 `service not enabled` |

`repos/` 属于运行时数据，已被 `.gitignore` 忽略。删除后可重新生成示例仓库。

## 目录结构

```
src/main/java/com/example/gitserver/
├── GitServerApplication.java          # 启动类
├── config/
│   ├── GitProperties.java             # app.git.* 配置绑定
│   └── GitServerConfig.java           # 注册 GitServlet 与各 Factory
├── git/
│   ├── FileRepositoryResolver.java    # URL 路径 -> 裸仓库（含路径穿越防护）
│   └── DemoRepositorySeeder.java      # 启动时生成 demo.git
└── web/
    └── RepositoryListController.java  # GET / 仓库列表页
src/main/resources/application.yml
```

## 工作原理

- `GitServlet` 通过 `ServletRegistrationBean` 挂载到 `/git/*`，实现 Git smart HTTP 协议（`info/refs`、`git-upload-pack`、`git-receive-pack`）。
- `FileRepositoryResolver` 把请求路径解析成 `app.git.root` 下的裸仓库，并拒绝 `../` 越界访问。
- `UploadPackFactory` 负责 clone/fetch，`ReceivePackFactory` 负责 push。
- 业务钩子建议加在 Factory 里，例如：
  - `PreReceiveHook`：push 权限校验、引用名校验
  - `PostReceiveHook`：推送后发事件、触发索引或流水线
  - `AdvertiseRefsHook`：按权限过滤可见引用

## 验证

```bash
# clone
git clone http://localhost:8080/git/demo.git /tmp/demo
ls /tmp/demo

# push 后再 clone，确认同步
cd /tmp/demo
echo hello > client.txt
git add client.txt
git commit -m "add client.txt"
git push origin main

git clone http://localhost:8080/git/demo.git /tmp/demo2
ls /tmp/demo2
```

## 已知限制

- **没有鉴权**：push 默认对所有人开放，仅适合本地开发/原型。生产环境应关闭 `allow-push`，并在 `/git/*` 前接入 Spring Security 或网关鉴权。
- **不是 Web UI**：不支持浏览器浏览文件、diff、提交历史。
- **存储为本地裸仓库**：单机文件系统，多实例共享需要额外的分布式锁与共享存储。
- **无 Git LFS 服务端**：大文件需自行实现 LFS batch API。
- **无测试套件**：`mvn test` 不会执行任何用例，按上面的命令手动验证。
- JGit 对 push 的 pack 不做 delta 压缩，长期使用建议定期执行 GC。
