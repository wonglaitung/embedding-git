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
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>""" + escape(title) + """
                </title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 1100px; padding: 0 1rem; color: #111827; }
                    h1 { font-size: 1.4rem; }
                    a { color: #2563eb; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { text-align: left; padding: .6rem .8rem; border-bottom: 1px solid #e5e7eb; }
                    th { background: #f9fafb; font-size: .8rem; text-transform: uppercase; color: #6b7280; }
                    td.name { font-weight: 600; }
                    code { background: #f3f4f6; padding: .15rem .4rem; border-radius: 4px; }
                    .empty { color: #9ca3af; text-align: center; }
                    .muted { color: #6b7280; font-size: .9rem; }
                    .banner { padding: .7rem 1rem; border-radius: 6px; margin: 1rem 0; }
                    .banner.ok { background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; }
                    .banner.error { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }
                    .card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 1rem 1.2rem; margin: 1.2rem 0; }
                    .card h2 { font-size: 1rem; margin: 0 0 .8rem; }
                    label { display: block; margin: .6rem 0 .2rem; font-size: .85rem; color: #374151; }
                    input[type=text], input[type=file] { width: 100%; box-sizing: border-box; padding: .45rem .55rem; border: 1px solid #d1d5db; border-radius: 6px; }
                    button { margin-top: .9rem; padding: .5rem 1rem; border: 0; border-radius: 6px; background: #2563eb; color: #fff; cursor: pointer; }
                    button:hover { background: #1d4ed8; }
                    .row { display: flex; gap: 1rem; flex-wrap: wrap; }
                    .row > div { flex: 1 1 220px; }
                  </style>
                </head>
                <body>
                """ + body + """
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
}
