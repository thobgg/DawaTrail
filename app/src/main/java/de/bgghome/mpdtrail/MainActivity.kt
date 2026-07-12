package de.bgghome.mpdtrail

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge: App läuft hinter Statusbar und Navigationsleiste
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        // Statusbar-Icons hell (weiß) — passt zu dunklem App-Hintergrund
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            databaseEnabled                  = true
            loadWithOverviewMode             = true
            useWideViewPort                  = true
            builtInZoomControls              = false
            displayZoomControls              = false
            setSupportZoom(false)
            allowFileAccessFromFileURLs      = false // Sicherheit: file://-Seite darf NICHT cross-origin
            allowUniversalAccessFromFileURLs = false // Dawarich-Calls laufen über die native Brücke (AndroidNet)
            mixedContentMode                 = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode                        = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean = false
        }

        // JS-Konsole (console.log / Fehler) ins Logcat spiegeln → Debugging on-device.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                Log.d("MPDTrailJS", "${cm.message()}  (${cm.sourceId()}:${cm.lineNumber()})")
                return true
            }
        }

        // GPX-Export: die WebView reicht Dateiname + XML herüber, Android schreibt
        // sie in den Cache und öffnet den System-Teilen-Dialog. Eigener Name
        // (nicht "AndroidBridge"), damit das JS-Back-Objekt nicht überschrieben wird.
        webView.addJavascriptInterface(ShareBridge(), "AndroidShare")

        // Native HTTP-GET-Brücke: da die file://-Seite kein Cross-Origin mehr darf,
        // holt Android die Dawarich-Daten (Same-Origin-Policy bleibt scharf).
        webView.addJavascriptInterface(NetBridge(), "AndroidNet")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "window.AndroidBridge && window.AndroidBridge.onBackPressed()"
                ) { result ->
                    if (result != "true" && webView.canGoBack()) webView.goBack()
                    else if (result != "true") finish()
                }
            }
        })

        webView.loadUrl("file:///android_asset/mpd-trail.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    /** Schreibt die GPX-Datei in den Cache und startet den Teilen-Dialog. */
    private fun shareGpxFile(filename: String, xml: String) {
        try {
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val safe = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "track.gpx" }
            val file = File(dir, safe)
            file.writeText(xml)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "GPX teilen"))
        } catch (_: Exception) {
            // Fehler still schlucken — der Nutzer sieht schlicht keinen Dialog.
        }
    }

    /** JS-Brücke für den GPX-Export. Methoden laufen auf einem Bridge-Thread. */
    inner class ShareBridge {
        @JavascriptInterface
        fun shareGpx(filename: String, xml: String) {
            runOnUiThread { shareGpxFile(filename, xml) }
        }
    }

    // ─── Native HTTP-GET (Dawarich) ────────────────────────────────────
    /** Ergebnis async ins JS zurückgeben: window.__netCb(cbId, jsonString). */
    private fun postToJs(cbId: String, json: String) {
        val quoted = org.json.JSONObject.quote(json)
        runOnUiThread {
            webView.evaluateJavascript("window.__netCb && window.__netCb('$cbId', $quoted)", null)
        }
    }

    private fun httpGet(url: String): Pair<Int, String> {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 15000; c.readTimeout = 20000
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }

    /** JS-Brücke: GET auf eine volle URL, Netzwerk im Hintergrund-Thread. */
    inner class NetBridge {
        @JavascriptInterface
        fun get(url: String, cbId: String) {
            Thread {
                val out = org.json.JSONObject()
                try {
                    val r = httpGet(url)
                    out.put("status", r.first); out.put("body", r.second)
                } catch (e: Exception) {
                    out.put("error", e.message ?: "error")
                }
                postToJs(cbId, out.toString())
            }.start()
        }
    }
}
