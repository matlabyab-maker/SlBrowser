package com.slbrowser.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("slbrowser_state", MODE_PRIVATE) }
    private val history = mutableListOf<String>()
    private val bookmarks = mutableListOf<String>()
    private var autoSelect = false
    private val fileRequest = 701

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history.addAll(readList("history"))
        bookmarks.addAll(readList("bookmarks"))
        buildInterface()
        configureWebView()
        val last = prefs.getString("last_url", null)
        web.loadUrl(last ?: "https://www.google.com")
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 6)
        }
        header.addView(TextView(this).apply {
            text = "SlBrowser"
            textSize = 19f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP, MATCH))

        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 15f
            hint = "Enter address or search"
            setTextColor(Color.BLACK)
            setPadding(12, 4, 12, 4)
        }
        header.addView(address, LinearLayout.LayoutParams(0, dp(50), 1f))
        header.addView(makeButton("Go", 50) { navigate(address.text.toString()) })
        header.addView(makeButton("Tabs", 62) { showTabs() })
        header.addView(makeButton("Menu", 58) { showMainMenu() })
        root.addView(header)

        val nav = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navRow.addView(makeButton("Back", 82) { if (web.canGoBack()) web.goBack() })
        navRow.addView(makeButton("Forward", 94) { if (web.canGoForward()) web.goForward() })
        navRow.addView(makeButton("History", 88) { showHistory() })
        navRow.addView(makeButton("Bookmarks", 112) { showBookmarks() })
        navRow.addView(makeButton("Downloads", 112) { showDownloadsInfo() })
        nav.addView(navRow)
        root.addView(nav)

        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(MATCH, 0, 1f))

        status = TextView(this).apply {
            text = "Ready"
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(8, 2, 8, 2)
        }
        root.addView(status)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(5, 5, 5, 7)
        }
        bottom.addView(makeButton("File", 1, 64) { chooseFiles() }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Answer", 1, 64) { answerDialog() }, LinearLayout.LayoutParams(0, dp(64), 1.15f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Save", 1, 64) { savePageText() }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Auto Select", 1, 64) { toggleAutoSelect() }, LinearLayout.LayoutParams(0, dp(64), 1.2f).apply { setMargins(2, 0, 2, 0) })
        root.addView(bottom)
        setContentView(root)
    }

    private fun configureWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = 100
            userAgentString = WebSettings.getDefaultUserAgent(this@MainActivity)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(PageBridge(), "SlBrowserBridge")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    address.setText(url)
                    prefs.edit().putString("last_url", url).apply()
                    addHistory(url)
                    status.text = view?.title ?: url
                    if (autoSelect) injectAutoSelect()
                }
            }
        }
        web.webChromeClient = WebChromeClient()
        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessName(contentDisposition, url))
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                toast("Download started")
            } catch (_: Exception) { toast("Unable to start download") }
        })
    }

    private fun makeButton(label: String, minWidthDp: Int, heightDp: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            minHeight = dp(heightDp)
            minimumHeight = dp(heightDp)
            minWidth = dp(minWidthDp)
            setPadding(8, 2, 8, 2)
            setOnClickListener { action() }
        }

    private fun makeButton(label: String, minWidthDp: Int, action: () -> Unit): Button =
        makeButton(label, minWidthDp, 54, action)

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val target = if (value.startsWith("http://") || value.startsWith("https://")) value
        else if (value.contains(".") && !value.contains(" ")) "https://$value"
        else "https://www.google.com/search?q=" + Uri.encode(value)
        web.loadUrl(target)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
        address.clearFocus()
    }

    private fun showMainMenu() {
        val items = arrayOf("New Tab", "Desktop Site", "Mobile Site", "Bookmarks", "History", "Downloads", "Settings", "Reload")
        AlertDialog.Builder(this).setTitle("SlBrowser Menu").setItems(items) { _, which ->
            when (which) {
                0 -> web.loadUrl("https://www.google.com")
                1 -> setDesktopMode(true)
                2 -> setDesktopMode(false)
                3 -> showBookmarks()
                4 -> showHistory()
                5 -> showDownloadsInfo()
                6 -> showSettings()
                7 -> web.reload()
            }
        }.show()
    }

    private fun setDesktopMode(enabled: Boolean) {
        val ua = if (enabled) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        } else WebSettings.getDefaultUserAgent(this)
        web.settings.userAgentString = ua
        web.settings.useWideViewPort = enabled
        web.settings.loadWithOverviewMode = enabled
        web.reload()
    }

    private fun showSettings() {
        val options = arrayOf("Zoom in", "Zoom out", "Reset zoom", "Desktop mode", "Mobile mode", "Clear browsing data")
        AlertDialog.Builder(this).setTitle("Settings").setItems(options) { _, which ->
            when (which) {
                0 -> web.evaluateJavascript("document.body.style.zoom = (parseFloat(document.body.style.zoom || '1') + 0.1)", null)
                1 -> web.evaluateJavascript("document.body.style.zoom = Math.max(0.1, parseFloat(document.body.style.zoom || '1') - 0.1)", null)
                2 -> web.evaluateJavascript("document.body.style.zoom = '1'", null)
                3 -> setDesktopMode(true)
                4 -> setDesktopMode(false)
                5 -> AlertDialog.Builder(this).setMessage("Clear cookies, cache and browsing history?")
                    .setPositiveButton("Clear") { _, _ ->
                        CookieManager.getInstance().removeAllCookies(null)
                        web.clearCache(true); web.clearHistory()
                        history.clear(); saveList("history", history)
                        toast("Browsing data cleared")
                    }.setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    private fun showTabs() {
        AlertDialog.Builder(this).setTitle("Tabs")
            .setMessage("1 active tab\\n\\nMulti-tab WebView management will be added in a later milestone.")
            .setPositiveButton("OK", null).show()
    }

    private fun showHistory() = showUrlList("History", history.asReversed(), false)
    private fun showBookmarks() {
        val choices = arrayOf("Open saved bookmark", "Add current page", "Manage saved bookmarks")
        AlertDialog.Builder(this).setTitle("Bookmarks").setItems(choices) { _, which ->
            when (which) {
                0 -> showUrlList("Bookmarks", bookmarks, true)
                1 -> {
                    val url = web.url ?: return@setItems
                    if (!bookmarks.contains(url)) bookmarks.add(url)
                    saveList("bookmarks", bookmarks); toast("Bookmark saved")
                }
                2 -> showUrlList("Saved Bookmarks", bookmarks, true)
            }
        }.show()
    }

    private fun showUrlList(title: String, values: List<String>, open: Boolean) {
        if (values.isEmpty()) { toast("No items"); return }
        AlertDialog.Builder(this).setTitle(title).setItems(values.toTypedArray()) { _, index ->
            if (open) web.loadUrl(values[index])
            else AlertDialog.Builder(this).setMessage(values[index]).setPositiveButton("Open") { _, _ -> web.loadUrl(values[index]) }
                .setNegativeButton("Close", null).show()
        }.setNegativeButton("Close", null).show()
    }

    private fun showDownloadsInfo() {
        AlertDialog.Builder(this).setTitle("Downloads")
            .setMessage("Downloads are saved in the device Downloads folder. Android Download Manager handles transfer notifications.")
            .setPositiveButton("OK", null).show()
    }

    private fun chooseFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, fileRequest)
    }

    @Deprecated("Legacy result callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 702 && resultCode == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.use { stream ->
                    stream.write(pendingText.toByteArray(Charsets.UTF_8))
                }
                toast("Text file saved")
            } catch (_: Exception) {
                toast("Unable to save text file")
            } finally {
                pendingText = ""
            }
            return
        }
        if (requestCode == fileRequest && resultCode == RESULT_OK && data != null) {
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
            data.data?.let { if (!uris.contains(it)) uris.add(it) }
            if (uris.isNotEmpty()) {
                // The selected documents are reported here. A full WebChromeClient
                // file chooser bridge is planned for the next implementation stage.
                toast("${uris.size} file(s) selected. Website upload integration is the next stage.")
            }
        }
    }

    private fun answerDialog() {
        val input = EditText(this).apply {
            hint = "Type your message"
            minLines = 3
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(this).setTitle("Answer").setView(input)
            .setPositiveButton("Send") { _, _ ->
                val message = input.text.toString()
                if (message.isNotBlank()) {
                    val quoted = JSONObject.quote(message)
                    web.evaluateJavascript("""
                        (function(){
                          const el=document.querySelector('textarea, [contenteditable="true"], input[type="text"]');
                          if(!el) return 'No editable message field found';
                          el.focus();
                          if(el.isContentEditable){el.textContent=$quoted;}
                          else {const setter=Object.getOwnPropertyDescriptor(el.constructor.prototype,'value')?.set; if(setter) setter.call(el,$quoted); else el.value=$quoted;}
                          el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:$quoted}));
                          return 'Message inserted. Review and press the website Send button.';
                        })()
                    """.trimIndent()) { result -> status.text = result?.trim('"') ?: "Message inserted" }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun savePageText() {
        web.evaluateJavascript("document.body ? document.body.innerText : document.documentElement.innerText") { result ->
            val text = decodeJsString(result)
            if (text.isBlank()) { toast("No page text found"); return@evaluateJavascript }
            val title = (web.title ?: "WebPage").replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val name = "${stamp}_${title}.txt"
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, name)
            }
            pendingText = text
            startActivityForResult(intent, 702)
        }
    }

    private var pendingText: String = ""

    private fun toggleAutoSelect() {
        autoSelect = !autoSelect
        if (autoSelect) injectAutoSelect()
        else web.evaluateJavascript("document.getElementById('__slbrowser_pick_style')?.remove(); document.getElementById('__slbrowser_pick_hint')?.remove();", null)
        status.text = if (autoSelect) "Auto Select: ON — tap a page section" else "Auto Select: OFF"
        toast(status.text.toString())
    }

    private fun injectAutoSelect() {
        web.evaluateJavascript("""
            (function(){
              if(document.getElementById('__slbrowser_pick_style')) return;
              const s=document.createElement('style'); s.id='__slbrowser_pick_style';
              s.textContent='body.__sl_pick_mode *:hover{outline:2px dashed #555!important;cursor:crosshair!important}';
              document.head.appendChild(s); document.body.classList.add('__sl_pick_mode');
              document.addEventListener('click', function(e){
                if(!document.body.classList.contains('__sl_pick_mode')) return;
                const el=e.target.closest('article,section,main,[role="dialog"],[role="article"],div') || e.target;
                const txt=(el.innerText||el.textContent||'').trim();
                if(!txt) return;
                e.preventDefault(); e.stopPropagation();
                window.SlBrowserBridge.onSelected(txt, document.title || 'Selected_Section');
              }, true);
            })();
        """.trimIndent(), null)
    }

    inner class PageBridge {
        @JavascriptInterface fun onSelected(text: String, title: String) {
            runOnUiThread {
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val safe = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(35).ifBlank { "Selected_Section" }
                val filename = "${stamp}_${safe}.txt"
                saveTextDocument(filename, text)
            }
        }
    }

    private fun saveTextDocument(filename: String, text: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        pendingText = text
        startActivityForResult(intent, 702)
    }

    private fun addHistory(url: String) {
        if (history.lastOrNull() != url) {
            history.add(url)
            while (history.size > 300) history.removeAt(0)
            saveList("history", history)
        }
    }

    private fun readList(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return try { org.json.JSONTokener(raw).nextValue() as? String ?: "" } catch (_: Exception) { "" }
    }

    private fun guessName(disposition: String?, url: String): String {
        val fromHeader = Regex("filename\\*=UTF-8''([^;]+)|filename=\"?([^\";]+)").find(disposition ?: "")
        val name = fromHeader?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            ?: Uri.parse(url).lastPathSegment ?: "download"
        return Uri.decode(name).replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        prefs.edit().putString("last_url", web.url).apply()
        super.onDestroy()
    }
}
