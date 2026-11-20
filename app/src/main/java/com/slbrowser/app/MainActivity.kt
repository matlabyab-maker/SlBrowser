package com.slbrowser.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.MimeTypeMap
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ScaleGestureDetector
import android.content.ClipData
import android.content.ClipboardManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
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
    private val tabViews = mutableListOf<WebView>()
    private val tabTitles = mutableListOf<String>()
    private var currentTab = 0
    private val web: WebView get() = tabViews[currentTab]
    private lateinit var webContainer: FrameLayout
    private lateinit var pageProgress: ProgressBar
    private lateinit var modeButton: Button
    private var desktopMode = false
    private data class PendingDownload(val url: String, val userAgent: String?, val disposition: String?, val mimeType: String?)
    private var pendingDownload: PendingDownload? = null
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("slbrowser_state", MODE_PRIVATE) }
    private val history = mutableListOf<String>()
    private val bookmarks = mutableListOf<String>()
    private var autoSelect = false
    private var fullScreen = false
    private var pageScale = 1.0f
    private lateinit var headerBar: LinearLayout
    private lateinit var navBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private val fileRequest = 701

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history.addAll(readList("history"))
        desktopMode = prefs.getBoolean("desktop_mode", false)
        bookmarks.addAll(readList("bookmarks"))
        buildInterface()
        val last = prefs.getString("last_url", null)
        // The first tab is created by buildInterface; load the last visited page.
        if (!last.isNullOrBlank()) web.loadUrl(last)
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
        headerBar = header
        root.addView(header)

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val navItems = listOf(
            "Back" to { if (web.canGoBack()) web.goBack() else toast("No previous page") },
            "Forward" to { if (web.canGoForward()) web.goForward() else toast("No next page") },
            "Past" to { pasteFromClipboard() },
            "Full Screen" to { toggleFullScreen() },
            "History" to { showHistory() },
            "Bookmarks" to { showBookmarks() },
            "Downloads" to { showDownloadsInfo() },
            "Mode" to { showModeChooser() }
        )
        navItems.forEach { (label, action) ->
            val b = makeButton(label, 1, 48, action)
            b.textSize = 10.5f
            navBar.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        modeButton = navBar.getChildAt(7) as Button
        modeButton.text = if (desktopMode) "Desktop" else "Mobile"
        root.addView(navBar)

        pageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(25, 118, 210))
        }
        root.addView(pageProgress, LinearLayout.LayoutParams(MATCH, dp(4)))
        webContainer = FrameLayout(this)
        root.addView(webContainer, LinearLayout.LayoutParams(MATCH, 0, 1f))
        createTab("https://www.google.com", switchTo = true)

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
        bottom.addView(makeButton("Auto Select", 1, 64) { toggleAutoSelect() }, LinearLayout.LayoutParams(0, dp(64), 1.05f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Save", 1, 64) { savePageText() }, LinearLayout.LayoutParams(0, dp(64), 0.9f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("File", 1, 64) { chooseFiles() }, LinearLayout.LayoutParams(0, dp(64), 0.9f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Answer", 1, 64) { answerDialog() }, LinearLayout.LayoutParams(0, dp(64), 1.15f).apply { setMargins(2, 0, 2, 0) })
        bottomBar = bottom
        root.addView(bottom)
        setContentView(root)
    }

    private fun configureWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = prefs.getBoolean("javascript_enabled", true)
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = prefs.getBoolean("images_enabled", true)
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = 100
            userAgentString = if (desktopMode) desktopUserAgent() else WebSettings.getDefaultUserAgent(this@MainActivity)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(PageBridge(), "SlBrowserBridge")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                if (request?.isForMainFrame != true) return false
                val target = request.url.toString()
                val path = Uri.parse(target).lastPathSegment?.lowercase(Locale.ROOT).orEmpty()
                val downloadable = listOf(".pdf", ".zip", ".rar", ".7z", ".apk", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".txt", ".mp3", ".mp4", ".m4a", ".wav")
                if (downloadable.any { path.endsWith(it) }) {
                    startDownload(PendingDownload(target, WebSettings.getDefaultUserAgent(this@MainActivity), null, null))
                    return true
                }
                return false
            }

            override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(v, url, favicon)
                if (tabViews.indexOf(view) == currentTab) {
                    pageProgress.progress = 0
                    pageProgress.visibility = View.VISIBLE
                    status.text = "Loading page…"
                }
            }
            override fun onPageFinished(v: WebView?, url: String?) {
                super.onPageFinished(v, url)
                val index = tabViews.indexOf(view)
                if (index >= 0 && !url.isNullOrBlank()) {
                    tabTitles[index] = view.title?.takeIf { it.isNotBlank() } ?: url
                    if (index == currentTab) {
                        address.setText(url)
                        prefs.edit().putString("last_url", url).apply()
                        status.text = view.title ?: url
                        pageProgress.progress = 100
                        pageProgress.visibility = View.GONE
                        if (autoSelect) injectAutoSelect()
                    }
                    addHistory(url)
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, newProgress: Int) {
                if (tabViews.indexOf(view) == currentTab) {
                    pageProgress.progress = newProgress.coerceIn(0, 100)
                    pageProgress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                    if (newProgress < 100) status.text = "Loading page… $newProgress%"
                }
            }
            override fun onShowFileChooser(
                webView: WebView?,
                callback: android.webkit.ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    startActivityForResult(intent, fileRequest)
                    true
                } catch (_: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    toast("Unable to open device files")
                    false
                }
            }

            override fun onCreateWindow(v: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val newView = createTab("about:blank", switchTo = true)
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = newView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onReceivedTitle(v: WebView?, title: String?) {
                val index = tabViews.indexOf(view)
                if (index >= 0 && !title.isNullOrBlank()) tabTitles[index] = title
            }
        }
        val pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pageScale = (pageScale * detector.scaleFactor).coerceIn(0.6f, 1.0f)
                view.evaluateJavascript("document.documentElement.style.zoom='${pageScale}'", null)
                return true
            }
        })
        view.setOnTouchListener { _, event -> pinch.onTouchEvent(event); false }
        view.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(PendingDownload(url, userAgent, contentDisposition, mimeType))
        })
    }

    private fun createTab(url: String, switchTo: Boolean): WebView {
        val view = WebView(this)
        tabViews.add(view)
        tabTitles.add("New tab")
        configureWebView(view)
        if (switchTo || tabViews.size == 1) {
            currentTab = tabViews.lastIndex
            webContainer.removeAllViews()
            webContainer.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
            if (::address.isInitialized) address.setText(if (url == "about:blank") "" else url)
        }
        if (url != "about:blank") view.loadUrl(url)
        return view
    }

    private fun switchTab(index: Int) {
        if (index !in tabViews.indices) return
        currentTab = index
        webContainer.removeAllViews()
        webContainer.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        address.setText(web.url ?: "")
        status.text = tabTitles[index]
        updateNavigationButtons()
    }

    private fun closeTab(index: Int) {
        if (tabViews.size <= 1) {
            web.loadUrl("https://www.google.com")
            return
        }
        val closing = tabViews.removeAt(index)
        tabTitles.removeAt(index)
        closing.stopLoading()
        closing.destroy()
        if (index < currentTab) currentTab--
        else if (index == currentTab) currentTab = minOf(index, tabViews.lastIndex)
        switchTab(currentTab)
    }

    private fun updateNavigationButtons() {
        // Back and Forward remain clickable; their actions safely check WebView history.
    }

    private fun makeButton(label: String, minWidthDp: Int, heightDp: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            minHeight = dp(heightDp)
            minimumHeight = dp(heightDp)
            minWidth = dp(minWidthDp)
            gravity = Gravity.TOP or Gravity.LEFT
            textSize = 14f
            setPadding(9, 5, 3, 2)
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
                0 -> createTab("https://www.google.com", switchTo = true)
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

    private fun desktopUserAgent(): String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun showModeChooser() {
        val modes = arrayOf("Mobile mode", "Desktop mode")
        AlertDialog.Builder(this).setTitle("Choose website mode")
            .setSingleChoiceItems(modes, if (desktopMode) 1 else 0) { dialog, which ->
                dialog.dismiss()
                setDesktopMode(which == 1)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setDesktopMode(enabled: Boolean) {
        desktopMode = enabled
        prefs.edit().putBoolean("desktop_mode", enabled).apply()
        tabViews.forEach { tab ->
            tab.settings.userAgentString = if (enabled) desktopUserAgent() else WebSettings.getDefaultUserAgent(this)
            tab.settings.useWideViewPort = true
            tab.settings.loadWithOverviewMode = enabled
        }
        if (::modeButton.isInitialized) modeButton.text = if (enabled) "Desktop" else "Mobile"
        web.reload()
        status.text = if (enabled) "Desktop mode enabled" else "Mobile mode enabled"
    }

    private fun showSettings() {
        val options = arrayOf("Zoom in", "Zoom out", "Reset zoom", "Desktop mode", "Mobile mode", "Search without images", "Disable JavaScript", "Clear browsing data")
        AlertDialog.Builder(this).setTitle("Settings").setItems(options) { _, which ->
            when (which) {
                0 -> web.evaluateJavascript("document.body.style.zoom = (parseFloat(document.body.style.zoom || '1') + 0.1)", null)
                1 -> web.evaluateJavascript("document.body.style.zoom = Math.max(0.1, parseFloat(document.body.style.zoom || '1') - 0.1)", null)
                2 -> web.evaluateJavascript("document.body.style.zoom = '1'", null)
                3 -> setDesktopMode(true)
                4 -> setDesktopMode(false)
                5 -> {
                    val enabled = !prefs.getBoolean("images_enabled", true)
                    prefs.edit().putBoolean("images_enabled", enabled).apply()
                    tabViews.forEach { it.settings.loadsImagesAutomatically = enabled }
                    toast(if (enabled) "Images enabled" else "Image-free browsing enabled")
                    web.reload()
                }
                6 -> {
                    val enabled = !prefs.getBoolean("javascript_enabled", true)
                    prefs.edit().putBoolean("javascript_enabled", enabled).apply()
                    tabViews.forEach { it.settings.javaScriptEnabled = enabled }
                    toast(if (enabled) "JavaScript enabled" else "JavaScript disabled; some sites may not work")
                    web.reload()
                }
                7 -> AlertDialog.Builder(this).setMessage("Clear cookies, cache and browsing history?")
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
        val labels = tabViews.indices.map { i ->
            "${if (i == currentTab) "✓ " else ""}${i + 1}. ${tabTitles[i].take(45)}"
        }.toMutableList()
        labels.add("＋ New tab")
        AlertDialog.Builder(this).setTitle("Tabs (${tabViews.size})")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == tabViews.size) createTab("https://www.google.com", true)
                else {
                    val actions = arrayOf("Open this tab", "Close this tab")
                    AlertDialog.Builder(this).setTitle(labels[which]).setItems(actions) { _, action ->
                        if (action == 0) switchTab(which) else closeTab(which)
                    }.show()
                }
            }.setNegativeButton("Done", null).show()
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
        val labels = values.map { compactAddress(it) }
        AlertDialog.Builder(this).setTitle(title).setItems(labels.toTypedArray()) { _, index ->
            if (open) web.loadUrl(values[index])
            else AlertDialog.Builder(this).setMessage(compactAddress(values[index])).setPositiveButton("Open") { _, _ -> web.loadUrl(values[index]) }
                .setNegativeButton("Close", null).show()
        }.setNegativeButton("Close", null).show()
    }

    private fun compactAddress(value: String): String {
        return try {
            val uri = Uri.parse(value)
            val host = uri.host ?: value
            val path = uri.path.orEmpty().take(36)
            (host + path).take(64)
        } catch (_: Exception) { value.take(64) }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrEmpty()) { toast("Clipboard is empty"); return }
        web.evaluateJavascript("""(function(){let e=document.activeElement;if(!e||!(e.isContentEditable||/^(TEXTAREA|INPUT)$/.test(e.tagName)))return 'NO_FIELD';e.focus();const v=${JSONObject.quote(text)};if(e.isContentEditable){document.execCommand('insertText',false,v);}else{const a=e.selectionStart??e.value.length,b=e.selectionEnd??e.value.length;e.setRangeText(v,a,b,'end');e.dispatchEvent(new Event('input',{bubbles:true}));}return 'PASTED';})()""") { r -> toast(if (r?.contains("PASTED") == true) "Pasted" else "Tap a text field first") }
    }

    private fun toggleFullScreen() {
        fullScreen = !fullScreen
        headerBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        navBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        bottomBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        status.visibility = if (fullScreen) View.GONE else View.VISIBLE
        window.decorView.systemUiVisibility = if (fullScreen) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) else View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun showDownloadsInfo() {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query())
        val rows = mutableListOf<Pair<Long, String>>()
        cursor?.use {
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val title = it.getString(titleCol) ?: "Download"
                val state = when (it.getInt(statusCol)) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    DownloadManager.STATUS_RUNNING -> "Downloading"
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    else -> "Pending"
                }
                rows.add(id to "$title — $state")
            }
        }
        val labels = rows.map { it.second }.toMutableList()
        labels.add("Open device Downloads folder")
        AlertDialog.Builder(this).setTitle("Downloads").setItems(labels.toTypedArray()) { _, which ->
            if (which == rows.size) {
                try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                catch (_: Exception) { toast("Open the Downloads folder in your file manager") }
            } else {
                try {
                    val uri = manager.getUriForDownloadedFile(rows[which].first)
                    if (uri != null) startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    else toast("This download is not ready yet")
                } catch (_: Exception) { toast("Unable to open downloaded file") }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun startDownload(item: PendingDownload) {
        if (Build.VERSION.SDK_INT in 23..28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = item
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 808)
            return
        }
        try {
            val name = guessName(item.disposition, item.url)
            val ext = MimeTypeMap.getFileExtensionFromUrl(item.url)
            val mime = item.mimeType?.takeIf { it.isNotBlank() }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val request = DownloadManager.Request(Uri.parse(item.url))
                .setTitle(name)
                .setDescription("Downloading with SlBrowser")
                .setMimeType(mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (Build.VERSION.SDK_INT >= 24) request.setAllowedOverMetered(true)
            item.userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(item.url)?.let { request.addRequestHeader("Cookie", it) }
            web.url?.let { request.addRequestHeader("Referer", it) }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            status.text = "Download started: $name"
            toast("Download started")
        } catch (e: Exception) {
            status.text = "Download failed to start"
            toast("Unable to start download: ${e.message ?: "unknown error"}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 808) {
            val item = pendingDownload
            pendingDownload = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && item != null) startDownload(item)
            else toast("Storage permission is required for downloads on this Android version")
        }
    }

    private fun chooseFiles() {
        web.evaluateJavascript("""
            (function(){
              const inputs=[...document.querySelectorAll('input[type=file]:not(:disabled)')];
              if(!inputs.length) return 'NO_FILE_INPUT';
              const shown=inputs.filter(el=>{const r=el.getBoundingClientRect();return r.width>0&&r.height>0;});
              const target=shown[shown.length-1]||inputs[inputs.length-1];
              target.multiple=true;
              target.click();
              return 'OPENED';
            })()
        """.trimIndent()) { result ->
            when (result?.trim('"')) {
                "NO_FILE_INPUT" -> toast("This page has no file upload field")
                "OPENED" -> status.text = "Select a file to upload"
                else -> toast("Could not open website upload")
            }
        }
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
        if (requestCode == fileRequest) {
            val callback = fileChooserCallback
            fileChooserCallback = null
            val result = if (resultCode == RESULT_OK && data != null)
                WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            callback?.onReceiveValue(result)
            if (result != null && result.isNotEmpty()) status.text = "File selected for website upload"
            else if (resultCode != RESULT_OK) status.text = "File selection cancelled"
            return
        }
    }

    private fun answerDialog() {
        web.evaluateJavascript("""
            (function(){
              const visible=e=>{if(!e||e.disabled)return false;const r=e.getBoundingClientRect();return r.width>0&&r.height>0;};
              const editors=[...document.querySelectorAll('textarea:not(:disabled),input[type=text]:not(:disabled),[contenteditable=true],[role=textbox]')].filter(visible);
              const active=document.activeElement;
              let editor=editors.includes(active)?active:null;
              if(!editor) editor=[...editors].reverse().find(e=>(e.innerText||e.value||'').trim().length>0);
              if(!editor)return 'NO_FIELD';
              if(!(editor.innerText||editor.value||'').trim())return 'EMPTY';
              const form=editor.closest('form');
              const rx=/send|submit|ارسال|فرستادن/i;
              let btn=form?[...form.querySelectorAll('button,[role=button],input[type=submit]')].filter(visible).find(e=>rx.test([e.innerText,e.textContent,e.getAttribute('aria-label'),e.title,e.value].join(' '))):null;
              if(!btn){let p=editor.parentElement;for(let i=0;p&&i<5&&!btn;i++,p=p.parentElement)btn=[...p.querySelectorAll('button,[role=button]')].filter(visible).find(e=>rx.test([e.innerText,e.textContent,e.getAttribute('aria-label'),e.title].join(' ')));}
              if(btn){btn.click();return 'SENT';}
              if(form&&form.requestSubmit){form.requestSubmit();return 'SENT';}
              return 'NO_SEND';
            })()
        """.trimIndent()) { result ->
            when (result?.trim('"')) {
                "SENT" -> { status.text = "Website send action activated"; toast("Send") }
                "EMPTY" -> toast("Write a message first")
                "NO_FIELD" -> toast("No message field found")
                else -> toast("Website Send button not found")
            }
        }
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
        if (fullScreen) { toggleFullScreen(); return }
        if (web.canGoBack()) web.goBack()
        else if (tabViews.size > 1) closeTab(currentTab)
        else super.onBackPressed()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (tabViews.isNotEmpty()) prefs.edit().putString("last_url", web.url).apply()
        tabViews.forEach { it.stopLoading(); it.destroy() }
        super.onDestroy()
    }
}
