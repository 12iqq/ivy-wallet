package com.ivy.desktop.google

/** The page the browser shows at the end of "Connect Google Drive", from the small web server in [GoogleConnect] */
internal object Pages {
    fun message(text: String): String = page(body = "<p>${escape(text)}</p>")

    private fun page(body: String): String = Template.replace("{{body}}", body)

    private val Template = """
        <!doctype html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Ivy Wallet UAE</title>
        <style>
        body { font-family: "Segoe UI", system-ui, sans-serif; margin: 64px auto; max-width: 560px; padding: 0 24px;
          color: #1f1f1f; background: #ffffff; font-size: 17px; line-height: 1.5; }
        h1 { font-size: 26px; color: #2e7d32; }
        @media (prefers-color-scheme: dark) { body { color: #e6e6e6; background: #121212; } h1 { color: #66bb6a; } }
        </style>
        </head>
        <body>
        <h1>Ivy Wallet UAE</h1>
        {{body}}
        </body>
        </html>
    """.trimIndent()

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
