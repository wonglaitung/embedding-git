package com.example.gitserver.web;

public final class Html {

    private Html() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String page(String title, String body) {
        return page(title, body, null);
    }

    public static String page(String title, String body, String version) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>""" + escape(title) + """
                </title>
                  <style>
                    :root {
                      color-scheme: light dark;
                      --bg: #f6f7fb;
                      --surface: #ffffff;
                      --surface-2: #f1f3f9;
                      --text: #111827;
                      --muted: #6b7280;
                      --border: #e5e7eb;
                      --brand: #4f46e5;
                      --brand-2: #7c3aed;
                      --brand-soft: #eef2ff;
                      --ok: #047857;
                      --ok-bg: #ecfdf5;
                      --ok-border: #a7f3d0;
                      --err: #b91c1c;
                      --err-bg: #fef2f2;
                      --err-border: #fecaca;
                      --shadow: 0 1px 2px rgba(16, 24, 40, .04), 0 10px 30px rgba(16, 24, 40, .06);
                      --topbar: rgba(255, 255, 255, .78);
                      --mono: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace;
                    }
                    @media (prefers-color-scheme: dark) {
                      :root {
                        --bg: #0b1020;
                        --surface: #111827;
                        --surface-2: #0f172a;
                        --text: #e5e7eb;
                        --muted: #94a3b8;
                        --border: #1f2937;
                        --brand: #818cf8;
                        --brand-2: #a78bfa;
                        --brand-soft: #1e1b4b;
                        --ok: #6ee7b7;
                        --ok-bg: #052e2b;
                        --ok-border: #065f46;
                        --err: #fca5a5;
                        --err-bg: #3f1d1d;
                        --err-border: #7f1d1d;
                        --shadow: 0 1px 2px rgba(0, 0, 0, .4), 0 10px 30px rgba(0, 0, 0, .35);
                        --topbar: rgba(11, 16, 32, .72);
                      }
                    }
                    * { box-sizing: border-box; }
                    html { -webkit-text-size-adjust: 100%; }
                    body {
                      margin: 0;
                      background: radial-gradient(1200px 600px at 50% -10%, var(--brand-soft), transparent 60%), var(--bg);
                      color: var(--text);
                      font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans SC", sans-serif;
                      line-height: 1.5;
                      min-height: 100vh;
                      display: flex;
                      flex-direction: column;
                    }
                    a { color: var(--brand); text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    .wrap { width: 100%; max-width: 1120px; margin: 0 auto; padding: 0 24px; }
                    .topbar {
                      position: sticky; top: 0; z-index: 10;
                      background: var(--topbar);
                      backdrop-filter: saturate(180%) blur(14px);
                      border-bottom: 1px solid var(--border);
                    }
                    .topbar-inner { display: flex; align-items: center; justify-content: space-between; height: 62px; }
                    .brand { display: inline-flex; align-items: center; gap: 10px; font-weight: 700; color: var(--text); letter-spacing: -.01em; }
                    .brand:hover { text-decoration: none; }
                    .brand-mark {
                      width: 30px; height: 30px; border-radius: 9px; display: grid; place-items: center;
                      background: linear-gradient(135deg, var(--brand), var(--brand-2)); color: #fff;
                      box-shadow: 0 6px 16px rgba(79, 70, 229, .35);
                    }
                    .nav a { color: var(--muted); font-size: .9rem; font-weight: 500; }
                    .nav a:hover { color: var(--text); text-decoration: none; }
                    .main { flex: 1; padding-top: 34px; padding-bottom: 48px; }
                    .footer { display: flex; justify-content: space-between; gap: 12px; color: var(--muted); font-size: .78rem; padding-bottom: 28px; }
                    .footer .version { font-family: var(--mono); }
                    h1 { font-size: 1.7rem; letter-spacing: -.02em; margin: 0; }
                    h2 { font-size: 1rem; margin: 0; }
                    .muted { color: var(--muted); }
                    .small { font-size: .82rem; }
                    .mono { font-family: var(--mono); }
                    .hero { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 22px; }
                    .hero p { margin: 8px 0 0; max-width: 640px; }
                    .stat-card {
                      flex: none; min-width: 108px; text-align: center; padding: 14px 18px;
                      background: var(--surface); border: 1px solid var(--border); border-radius: 14px; box-shadow: var(--shadow);
                    }
                    .stat-card strong { display: block; font-size: 1.6rem; letter-spacing: -.03em; }
                    .stat-card span { color: var(--muted); font-size: .78rem; }
                    .card {
                      background: var(--surface); border: 1px solid var(--border);
                      border-radius: 16px; box-shadow: var(--shadow); padding: 20px;
                    }
                    .banner { padding: 12px 16px; border-radius: 12px; margin-bottom: 16px; font-size: .9rem; border: 1px solid; }
                    .banner.ok { background: var(--ok-bg); color: var(--ok); border-color: var(--ok-border); }
                    .banner.error { background: var(--err-bg); color: var(--err); border-color: var(--err-border); }
                    .create-card { margin-bottom: 22px; }
                    .create-form { display: flex; align-items: flex-end; gap: 12px; }
                    .create-field { flex: 1; }
                    label { display: block; margin: 12px 0 6px; font-size: .82rem; font-weight: 600; color: var(--muted); }
                    input[type=text] {
                      width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 10px;
                      background: var(--surface-2); color: var(--text); font: inherit; font-size: .9rem;
                      transition: border-color .15s, box-shadow .15s;
                    }
                    input[type=text]:focus {
                      outline: none; border-color: var(--brand);
                      box-shadow: 0 0 0 3px var(--brand-soft);
                      background: var(--surface);
                    }
                    .hint { color: var(--muted); font-size: .78rem; margin: 10px 0 0; }
                    .btn {
                      display: inline-flex; align-items: center; justify-content: center; gap: 8px;
                      padding: 10px 16px; border-radius: 10px; border: 1px solid transparent;
                      font: inherit; font-size: .88rem; font-weight: 600; cursor: pointer;
                      transition: transform .12s, background .15s, border-color .15s, color .15s;
                      white-space: nowrap;
                    }
                    .btn:hover { transform: translateY(-1px); }
                    .btn:active { transform: translateY(0); }
                    .btn-primary {
                      background: linear-gradient(135deg, var(--brand), var(--brand-2));
                      color: #fff; box-shadow: 0 8px 18px rgba(79, 70, 229, .25);
                    }
                    .btn-ghost { background: var(--surface); border-color: var(--border); color: var(--text); }
                    .btn-ghost:hover { border-color: var(--brand); color: var(--brand); }
                    .btn-block { width: 100%; }
                    .badge {
                      display: inline-flex; align-items: center; padding: 2px 9px; border-radius: 999px;
                      background: var(--surface-2); border: 1px solid var(--border);
                      color: var(--muted); font-size: .72rem; font-weight: 600; font-family: var(--mono);
                    }
                    .repo-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
                    .repo-card {
                      background: var(--surface); border: 1px solid var(--border); border-radius: 16px;
                      box-shadow: var(--shadow); padding: 18px; display: flex; flex-direction: column; gap: 10px;
                      transition: transform .15s, border-color .15s, box-shadow .15s;
                    }
                    .repo-card:hover { transform: translateY(-2px); border-color: var(--brand); box-shadow: 0 14px 34px rgba(79, 70, 229, .12); }
                    .repo-card-head { display: flex; align-items: center; gap: 10px; }
                    .repo-icon {
                      width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center;
                      background: var(--brand-soft); color: var(--brand); flex: none;
                    }
                    .repo-name { font-weight: 650; font-size: 1rem; color: var(--text); overflow-wrap: anywhere; }
                    .repo-name:hover { color: var(--brand); text-decoration: none; }
                    .repo-commit { margin: 0; font-size: .88rem; color: var(--text); overflow-wrap: anywhere; }
                    .clone-row {
                      display: flex; align-items: center; gap: 8px; margin-top: auto;
                      background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; padding: 6px 6px 6px 10px;
                    }
                    .clone-row code {
                      flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
                      font-family: var(--mono); font-size: .76rem; color: var(--muted);
                    }
                    .clone-row .btn { padding: 6px 10px; font-size: .76rem; }
                    .empty-state { grid-column: 1 / -1; text-align: center; padding: 48px 24px; color: var(--muted); }
                    .empty-state svg { color: var(--border); margin-bottom: 12px; }
                    .crumbs { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: .85rem; margin-bottom: 14px; }
                    .crumbs a { color: var(--muted); }
                    .crumbs a:hover { color: var(--brand); }
                    .repo-header { margin-bottom: 22px; }
                    .repo-title { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
                    .repo-title .repo-icon { width: 42px; height: 42px; border-radius: 12px; }
                    .repo-title h1 { font-size: 1.55rem; overflow-wrap: anywhere; }
                    .repo-meta { display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; margin-top: 10px; color: var(--muted); font-size: .85rem; }
                    .repo-meta code { font-family: var(--mono); background: var(--surface-2); border: 1px solid var(--border); padding: 1px 7px; border-radius: 6px; }
                    .detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 20px; align-items: start; }
                    .files-card, .upload-card { padding: 18px; }
                    .card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
                    .tree, .tree ul { list-style: none; margin: 0; padding: 0; }
                    .tree ul { padding-left: 18px; border-left: 1px dashed var(--border); margin-left: 8px; }
                    .tree-item { margin: 1px 0; }
                    .file-row, .dir-row {
                      display: flex; align-items: center; gap: 8px; padding: 7px 9px; border-radius: 8px; font-size: .88rem;
                    }
                    .file-row:hover, .dir-row:hover { background: var(--surface-2); }
                    .dir-row { font-weight: 600; }
                    .file-icon { color: var(--muted); flex: none; display: inline-flex; }
                    .folder-icon { color: var(--brand); flex: none; display: inline-flex; }
                    .file-name { overflow-wrap: anywhere; }
                    .file-size { margin-left: auto; padding-left: 12px; color: var(--muted); font-family: var(--mono); font-size: .74rem; flex: none; }
                    .empty-files { color: var(--muted); text-align: center; padding: 30px 10px; font-size: .9rem; }
                    .dropzone {
                      display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center;
                      padding: 26px 16px; border: 2px dashed var(--border); border-radius: 14px;
                      background: var(--surface-2); cursor: pointer; transition: border-color .15s, background .15s;
                    }
                    .dropzone:hover, .dropzone.dragging { border-color: var(--brand); background: var(--brand-soft); }
                    .dropzone-icon { color: var(--brand); }
                    .dropzone strong { font-size: .9rem; }
                    .dropzone span.muted { font-size: .78rem; }
                    .picker-row { display: flex; gap: 8px; margin-top: 10px; }
                    .picker-row .btn { flex: 1; padding: 8px 12px; font-size: .8rem; }
                    input[type=file][hidden] { display: none; }
                    .upload-form .btn-primary { margin-top: 16px; }
                    @media (max-width: 880px) {
                      .detail-grid { grid-template-columns: 1fr; }
                      .hero { flex-direction: column; }
                      .create-form { flex-direction: column; align-items: stretch; }
                    }
                    @media (max-width: 560px) {
                      .wrap { padding: 0 16px; }
                      h1 { font-size: 1.45rem; }
                      .repo-grid { grid-template-columns: 1fr; }
                    }
                  </style>
                </head>
                <body>
                  <header class="topbar">
                    <div class="wrap topbar-inner">
                      <a class="brand" href="/">
                        <span class="brand-mark">
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="6" cy="6" r="2.5"></circle>
                            <circle cx="18" cy="6" r="2.5"></circle>
                            <circle cx="12" cy="18" r="2.5"></circle>
                            <path d="M8.5 6h7M6 8.5v3a3 3 0 0 0 3 3h1.5M18 8.5v3a3 3 0 0 1-3 3h-1.5"></path>
                          </svg>
                        </span>
                        Git Server
                      </a>
                      <nav class="nav"><a href="/">仓库</a></nav>
                    </div>
                  </header>
                  <main class="wrap main">
                """ + body + """
                  </main>
                  <footer class="wrap footer">
                    <span>Embedded Git smart HTTP · Spring Boot + JGit</span>
                    <span class="version">v""" + escape(version == null ? "" : version) + """
                </span>
                  </footer>
                </body>
                </html>
                """;
    }

    public static String banner(String ok, String error) {
        StringBuilder html = new StringBuilder();
        if (ok != null && !ok.isBlank()) {
            html.append("<div class=\"banner ok\">").append(escape(ok)).append("</div>");
        }
        if (error != null && !error.isBlank()) {
            html.append("<div class=\"banner error\">").append(escape(error)).append("</div>");
        }
        return html.toString();
    }

    public static String repoIcon() {
        return """
                <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
                </svg>
                """;
    }

    public static String fileIcon() {
        return """
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <path d="M14 2v6h6"></path>
                </svg>
                """;
    }

    public static String folderIcon() {
        return """
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
                </svg>
                """;
    }
}
