package com.ivy.desktop.google

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The pages the browser shows during "Connect Google Drive", from the small web server in [GoogleConnect] */
internal object Pages {
    fun message(text: String): String = page(body = "<p>${escape(text)}</p>")

    /**
     * Google's file picker, showing backup files (.zip and .json) and searching for ivywallet-uae. The file chosen
     * is sent to [answerPath], and the app may open it from then on. Closing the picker sends nothing chosen.
     */
    fun picker(config: GoogleConfig, token: AccessToken, answerPath: String): String {
        val settings = buildJsonObject {
            put("token", token.value)
            put("apiKey", config.apiKey)
            put("appId", config.projectNumber)
            put("answer", answerPath)
        }.toString().replace("<", "\\u003c")
        return page(
            body = """
                <p id="status">Opening Google Drive…</p>
                <script>
                const settings = $settings;
                function show(text) { document.getElementById('status').textContent = text; }
                function answer(file) {
                  return fetch(settings.answer, { method: 'POST', body: JSON.stringify(file) });
                }
                function chosen(data) {
                  if (data.action === google.picker.Action.PICKED) {
                    const file = { id: data.docs[0].id, name: data.docs[0].name };
                    answer(file).then(
                      () => show('Done. Ivy Wallet UAE is opening ' + file.name + ' now, and gets each new backup ' +
                        'by itself. You can close this tab.'),
                      () => show("Couldn't reach Ivy Wallet UAE. Is it still open? Connect Google Drive again " +
                        'from the app.'));
                  } else if (data.action === google.picker.Action.CANCEL) {
                    answer({}).catch(() => {});
                    show('No backup chosen. You can close this tab, and connect Google Drive again from the app.');
                  }
                }
                function openPicker() {
                  const view = new google.picker.DocsView(google.picker.ViewId.DOCS)
                    .setMimeTypes('application/zip,application/x-zip-compressed,application/json')
                    .setMode(google.picker.DocsViewMode.LIST);
                  if (typeof view.setQuery === 'function') view.setQuery('ivywallet-uae');
                  new google.picker.PickerBuilder()
                    .setAppId(settings.appId)
                    .setOAuthToken(settings.token)
                    .setDeveloperKey(settings.apiKey)
                    .addView(view)
                    .setTitle('Choose your Ivy Wallet UAE backup')
                    .setCallback(chosen)
                    .build()
                    .setVisible(true);
                }
                function start() {
                  gapi.load('picker', {
                    callback: openPicker,
                    onerror: () => show("Couldn't open Google Drive's file picker. Try again from Ivy Wallet UAE."),
                  });
                }
                </script>
                <script src="https://apis.google.com/js/api.js" onload="start()"
                  onerror="show('Couldn\'t reach Google Drive. Is this computer online?')"></script>
            """.trimIndent(),
        )
    }

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
