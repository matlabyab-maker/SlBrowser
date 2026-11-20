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
import android.content.ContentValues
import android.provider.MediaStore
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
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
    private data class VideoCandidate(val url: String, val title: String, val mime: String, val kind: String)
    private val videoCandidates = linkedMapOf<String, VideoCandidate>()
    private val videoBlinkHandler = Handler(Looper.getMainLooper())
    private var videoBlinking = false
    private lateinit var videoLinksButton: Button
    private var blobTempFile: File? = null
    private var blobOutput: FileOutputStream? = null
    private var blobDisplayName: String = "download.bin"
    private var blobMimeType: String = "application/octet-stream"
    private var blobBytes: Long = 0L
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
        try {
            YoutubeDL.getInstance().init(this)
        } catch (e: Exception) {
            android.util.Log.e("SlBrowser", "Video extractor initialization failed", e)
        }
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
            setPadding(8, 1, 8, 0)
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
            setPadding(0, 0, 0, 0)
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
        bottom.addView(makeButton("Auto Select", 1, 64) { toggleAutoSelect() }, LinearLayout.LayoutParams(0, dp(64), 0.95f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Save", 1, 64) { savePageText() }, LinearLayout.LayoutParams(0, dp(64), 0.9f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("File", 1, 64) { chooseFiles() }, LinearLayout.LayoutParams(0, dp(64), 0.9f).apply { setMargins(2, 0, 2, 0) })
        bottom.addView(makeButton("Answer", 1, 64) { answerDialog() }, LinearLayout.LayoutParams(0, dp(64), 1.0f).apply { setMargins(2, 0, 2, 0) })
        videoLinksButton = makeButton("Video Links", 1, 64) { showVideoLinks() }
        bottom.addView(videoLinksButton, LinearLayout.LayoutParams(0, dp(64), 1.1f).apply { setMargins(2, 0, 2, 0) })
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
        view.addJavascriptInterface(DownloadBridge(), "SlBrowserDownloadBridge")
        view.addJavascriptInterface(VideoScanBridge(), "SlBrowserVideoBridge")
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
                    installGeneratedDownloadCapture(view)
                    scanPageForVideos(view)
                    view.postDelayed(object : Runnable {
                        override fun run() {
                            if (!this@MainActivity.isFinishing && !this@MainActivity.isDestroyed && tabViews.contains(view) && view.url == url) {
                                scanPageForVideos(view)
                                view.postDelayed(this, 2500)
                            }
                        }
                    }, 2500)
                }
            }
            override fun shouldInterceptRequest(v: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                val u = request?.url?.toString() ?: return null
                val lower = u.lowercase(Locale.ROOT)
                val path = Uri.parse(u).path?.lowercase(Locale.ROOT).orEmpty()
                if (isVideoResource(lower, path)) addVideoCandidate(u, "Video stream", request?.requestHeaders?.get("Accept") ?: "", videoKind(u))
                return super.shouldInterceptRequest(v, request)
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
            val parsed = Uri.parse(url)
            when (parsed.scheme?.lowercase(Locale.ROOT)) {
                "blob", "data" -> downloadPageGeneratedUrl(view, url, contentDisposition, mimeType)
                "http", "https" -> startDownload(PendingDownload(url, userAgent, contentDisposition, mimeType))
                "content", "file" -> copyLocalUriToDownloads(parsed, contentDisposition, mimeType)
                else -> toast("Unsupported download link type")
            }
        })
    }

    private fun isVideoResource(url: String, path: String): Boolean {
        return listOf(".mp4", ".m4v", ".webm", ".mov", ".mkv", ".3gp", ".ts", ".m3u8", ".mpd", ".ism/manifest")
            .any { path.contains(it) || url.contains(it) }
    }

    private fun videoKind(url: String): String {
        val p = Uri.parse(url).path?.lowercase(Locale.ROOT).orEmpty()
        return when {
            p.contains(".m3u8") -> "HLS playlist"
            p.contains(".mpd") -> "MPEG-DASH manifest"
            p.contains(".webm") -> "WebM"
            p.contains(".mkv") -> "Matroska"
            p.contains(".mov") -> "QuickTime"
            p.contains(".ts") -> "MPEG-TS segment"
            else -> "Video"
        }
    }

    private fun shortVideoLabel(raw: String): String {
        val u = Uri.parse(raw)
        val host = u.host ?: "Video"
        val file = u.lastPathSegment?.substringBefore('?')?.takeIf { it.isNotBlank() }
        return (file ?: host).let { if (it.length > 34) it.take(31) + "…" else it }
    }

    private fun addVideoCandidate(url: String, title: String, mime: String, kind: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (url.length > 12000 || url.contains("googlevideo.com/videoplayback") && url.contains("expire=") ) {
            // Signed URLs are retained exactly as received; only their visible label is shortened.
        }
        runOnUiThread {
            if (!videoCandidates.containsKey(url)) {
                videoCandidates[url] = VideoCandidate(url, shortVideoLabel(url), mime, kind)
                videoLinksButton.text = "Video Links •"
                startVideoBlink()
            }
        }
    }

    private fun startVideoBlink() {
        if (videoBlinking) return
        videoBlinking = true
        val task = object : Runnable {
            var lit = false
            override fun run() {
                if (!videoBlinking || videoCandidates.isEmpty()) {
                    videoLinksButton.alpha = 1f
                    videoBlinking = false
                    return
                }
                lit = !lit
                videoLinksButton.alpha = if (lit) 1f else 0.35f
                videoBlinkHandler.postDelayed(this, 550)
            }
        }
        videoBlinkHandler.post(task)
    }

    private fun scanPageForVideos(view: WebView) {
        view.evaluateJavascript("""
          (function(){
            const out = new Map();
            const add = (u,t,m) => { try { if(u && /^https?:/i.test(u)) out.set(u,{url:u,title:t||'',mime:m||''}); } catch(e){} };
            document.querySelectorAll('video,audio').forEach(v=>{
              add(v.currentSrc || v.src, v.getAttribute('title') || document.title, v.getAttribute('type') || '');
              v.querySelectorAll('source').forEach(s=>add(s.src, document.title, s.type));
            });
            document.querySelectorAll('source[src]').forEach(s=>add(s.src,document.title,s.type));
            try { performance.getEntriesByType('resource').forEach(e=>{
              const u=e.name||'';
              if(/\\.(mp4|m4v|webm|mov|mkv|3gp|m3u8|mpd|ts)(\\?|$)/i.test(u)) add(u,document.title,'');
            }); } catch(e){}
            out.forEach(x=>window.SlBrowserVideoBridge.found(x.url,x.title,x.mime));
            return out.size;
          })();
        """.trimIndent(), null)
    }

    inner class VideoScanBridge {
        @JavascriptInterface fun found(url: String, title: String, mime: String) {
            addVideoCandidate(url, title, mime, videoKind(url))
        }
    }

    private fun showVideoLinks() {
        videoBlinking = false
        videoBlinkHandler.removeCallbacksAndMessages(null)
        videoLinksButton.alpha = 1f
        videoLinksButton.text = "Video Links"
        val items = videoCandidates.values.toList()
        val labels = items.map { "${it.title}  •  ${it.kind}" }.toMutableList()
        labels.add(0, "Extract links from this page (yt-dlp)")
        AlertDialog.Builder(this).setTitle("Video Links (${items.size})")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    analyzeCurrentPageWithExtractor()
                } else {
                    val item = items[which - 1]
                    AlertDialog.Builder(this).setTitle("Ready to download?")
                        .setMessage("${item.title}\n${item.kind}\n\nThe original URL is preserved.")
                        .setPositiveButton("Download") { _, _ ->
                            startDownload(PendingDownload(item.url, WebSettings.getDefaultUserAgent(this), null, item.mime))
                        }
                        .setNeutralButton("Copy link") { _, _ ->
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Video URL", item.url))
                            toast("Original video link copied")
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Close", null).show()
    }

    private fun analyzeCurrentPageWithExtractor() {
        val pageUrl = web.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (pageUrl == null) {
            toast("Open a video webpage first")
            return
        }
        status.text = "Analyzing video page..."
        Thread {
            try {
                val info = YoutubeDL.getInstance().getInfo(pageUrl)
                val title = info.title?.takeIf { it.isNotBlank() } ?: "Online video"
                val request = YoutubeDLRequest(pageUrl)
                request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                request.addOption("--merge-output-format", "mp4")
                request.addOption("--no-mtime")
                runOnUiThread {
                    status.text = "Video source found: $title"
                    AlertDialog.Builder(this)
                        .setTitle("Video source found")
                        .setMessage("$title\n\nSource page analyzed successfully. The extractor will choose the best available compatible video/audio streams and merge them when required.")
                        .setPositiveButton("Download video") { _, _ -> startExtractorDownload(request, title) }
                        .setNeutralButton("Copy page link") { _, _ ->
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Video page", pageUrl))
                            toast("Page link copied")
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Video extraction failed"
                    AlertDialog.Builder(this).setTitle("Could not extract video")
                        .setMessage(e.message ?: "This site may not be supported, or the video may require login, DRM, or an unavailable extractor.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }.start()
    }

    private fun startExtractorDownload(request: YoutubeDLRequest, title: String) {
        val downloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SlBrowser")
        if (!downloads.exists()) downloads.mkdirs()
        request.addOption("-o", File(downloads, "%(title).120B.%(ext)s").absolutePath)
        val processId = "slbrowser-video-${System.currentTimeMillis()}"
        status.text = "Starting video download..."
        Thread {
            try {
                YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                    runOnUiThread {
                        status.text = "Video: ${progress.toInt()}% • ${line.take(90)}"
                    }
                    kotlin.Unit
                }
                runOnUiThread {
                    status.text = "Video download completed: $title"
                    toast("Video download completed")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Video download failed"
                    toast("Video download failed: ${e.message ?: "unknown error"}")
                }
            }
        }.start()
    }

    private fun installGeneratedDownloadCapture(view: WebView) {
        view.evaluateJavascript("""
            (function(){
              if (window.__slGeneratedDownloadCapture) return;
              window.__slGeneratedDownloadCapture = true;
              document.addEventListener('click', function(event) {
                const a = event.target && event.target.closest ? event.target.closest('a[href]') : null;
                if (!a) return;
                const href = a.href || '';
                if (!href.startsWith('blob:') && !href.startsWith('data:')) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                const name = a.getAttribute('download') || 'download';
                (async function(){
                  try {
                    const response = await fetch(href);
                    const blob = await response.blob();
                    window.SlBrowserDownloadBridge.beginGeneratedDownload(name, blob.type || 'application/octet-stream');
                    const reader = blob.stream().getReader();
                    while (true) {
                      const part = await reader.read();
                      if (part.done) break;
                      const bytes = part.value;
                      let binary = '';
                      for (let i=0; i<bytes.length; i+=0x8000) {
                        binary += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i+0x8000, bytes.length)));
                      }
                      window.SlBrowserDownloadBridge.appendGeneratedDownload(btoa(binary));
                    }
                    window.SlBrowserDownloadBridge.finishGeneratedDownload();
                  } catch (e) {
                    window.SlBrowserDownloadBridge.failGeneratedDownload(String(e));
                  }
                })();
              }, true);
            })();
        """.trimIndent(), null)
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
        val uri = Uri.parse(item.url)
        if (uri.scheme != "http" && uri.scheme != "https") {
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "blob", "data" -> downloadPageGeneratedUrl(web, item.url, item.disposition, item.mimeType)
                "content", "file" -> copyLocalUriToDownloads(uri, item.disposition, item.mimeType)
                else -> toast("Unsupported download link type")
            }
            return
        }
        if (Build.VERSION.SDK_INT in 23..28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = item
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 808)
            return
        }
        try {
            val name = safeDownloadName(guessName(item.disposition, item.url))
            val ext = MimeTypeMap.getFileExtensionFromUrl(item.url)
            val mime = item.mimeType?.takeIf { it.isNotBlank() }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val request = DownloadManager.Request(uri)
                .setTitle(name)
                .setDescription("SlBrowser download")
                .setMimeType(mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (Build.VERSION.SDK_INT >= 24) request.setAllowedOverMetered(true)
            item.userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(item.url)?.let { request.addRequestHeader("Cookie", it) }
            web.url?.let { request.addRequestHeader("Referer", it) }
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            prefs.edit().putLong("last_download_id", id).apply()
            status.text = "Download queued: $name"
            toast("Download started")
        } catch (e: Exception) {
            status.text = "Download failed to start"
            toast("Download error: ${e.message ?: "unknown error"}")
        }
    }

    private fun downloadPageGeneratedUrl(view: WebView, url: String, disposition: String?, mime: String?) {
        val name = safeDownloadName(guessName(disposition, url))
        val quotedUrl = JSONObject.quote(url)
        val quotedName = JSONObject.quote(name)
        val quotedMime = JSONObject.quote(mime ?: "application/octet-stream")
        view.evaluateJavascript("""
            (async function(){
              try {
                const response = await fetch($quotedUrl, {credentials:'include'});
                if (!response.ok && response.status !== 0) throw new Error('HTTP ' + response.status);
                const blob = await response.blob();
                const name = $quotedName;
                const type = blob.type || $quotedMime || 'application/octet-stream';
                window.SlBrowserDownloadBridge.beginGeneratedDownload(name, type);
                const reader = blob.stream().getReader();
                while (true) {
                  const part = await reader.read();
                  if (part.done) break;
                  const bytes = part.value;
                  let binary = '';
                  for (let i=0; i<bytes.length; i+=0x8000) {
                    binary += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i+0x8000, bytes.length)));
                  }
                  window.SlBrowserDownloadBridge.appendGeneratedDownload(btoa(binary));
                }
                window.SlBrowserDownloadBridge.finishGeneratedDownload();
                return 'OK';
              } catch(e) {
                window.SlBrowserDownloadBridge.failGeneratedDownload(String(e));
                return 'ERROR:' + String(e);
              }
            })()
        """.trimIndent()) { result ->
            val value = result?.trim('"') ?: ""
            if (value.startsWith("ERROR:")) {
                status.text = "Generated download failed"
                toast("Cannot download this generated file: ${value.removePrefix("ERROR:")}")
            } else if (value == "OK") {
                status.text = "Generated file saved"
            }
        }
    }

    inner class DownloadBridge {
        @JavascriptInterface fun beginGeneratedDownload(name: String, mime: String) {
            synchronized(this@MainActivity) {
                try {
                    blobOutput?.close()
                    blobDisplayName = safeDownloadName(name)
                    blobMimeType = mime.ifBlank { "application/octet-stream" }
                    blobBytes = 0L
                    blobTempFile = File(cacheDir, "slbrowser_${System.currentTimeMillis()}.part")
                    blobOutput = FileOutputStream(blobTempFile!!)
                } catch (e: Exception) {
                    blobOutput = null
                    runOnUiThread { toast("Cannot prepare download file") }
                }
            }
        }
        @JavascriptInterface fun appendGeneratedDownload(base64: String) {
            synchronized(this@MainActivity) {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    blobOutput?.write(bytes)
                    blobBytes += bytes.size
                } catch (e: Exception) {
                    runOnUiThread { toast("Download data transfer failed") }
                }
            }
        }
        @JavascriptInterface fun finishGeneratedDownload() {
            synchronized(this@MainActivity) {
                try {
                    blobOutput?.flush()
                    blobOutput?.close()
                    blobOutput = null
                    val temp = blobTempFile ?: throw IllegalStateException("Missing temporary file")
                    publishGeneratedFile(temp, blobDisplayName, blobMimeType)
                    blobTempFile = null
                    runOnUiThread {
                        status.text = "Saved $blobDisplayName (${blobBytes} bytes)"
                        toast("Download saved")
                    }
                } catch (e: Exception) {
                    runOnUiThread { toast("Unable to save generated file: ${e.message}") }
                }
            }
        }
        @JavascriptInterface fun failGeneratedDownload(reason: String) {
            synchronized(this@MainActivity) {
                try { blobOutput?.close() } catch (_: Exception) {}
                blobOutput = null
                blobTempFile?.delete()
                blobTempFile = null
                runOnUiThread { status.text = "Download failed: $reason" }
            }
        }
    }

    private fun publishGeneratedFile(temp: File, name: String, mime: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create Downloads entry")
            try {
                contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("Cannot open destination")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            } finally { temp.delete() }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val target = uniqueFile(File(dir, name))
            temp.copyTo(target, overwrite = false)
            temp.delete()
        }
    }

    private fun copyLocalUriToDownloads(uri: Uri, disposition: String?, mime: String?) {
        try {
            val name = safeDownloadName(guessName(disposition, uri.toString()))
            val type = mime ?: contentResolver.getType(uri) ?: "application/octet-stream"
            val temp = File(cacheDir, "slbrowser_local_${System.currentTimeMillis()}.part")
            contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("Cannot open source file")
            publishGeneratedFile(temp, name, type)
            toast("Download saved")
        } catch (e: Exception) { toast("Unable to copy file: ${e.message}") }
    }

    private fun safeDownloadName(value: String): String {
        val cleaned = value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim()
        return cleaned.take(120).ifBlank { "download_${System.currentTimeMillis()}" }
    }

    private fun uniqueFile(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension
        var n = 1
        while (true) {
            val candidate = File(file.parentFile, "${base}_$n${if (ext.isBlank()) "" else ".$ext"}")
            if (!candidate.exists()) return candidate
            n++
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
