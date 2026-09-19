package com.docblog.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingContainer: LinearLayout
    private lateinit var loadingText: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private var serverPort = 8080

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        loadingContainer = findViewById(R.id.loadingContainer)
        loadingText = findViewById(R.id.loadingText)

        // Setup WebView early
        setupWebView()

        // Initialize and start in background
        executor.execute {
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this@MainActivity))
                }

                val dataDir = prepareDataDir()
                startPythonServerSync(dataDir)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    loadingText.text = "启动失败: ${e.message}"
                    progressBar.visibility = View.GONE
                }
            }
        }

        // Wait for server ready
        waitForServer()
    }

    private fun startPythonServerSync(dataDir: File) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("server_android")
            module.callAttr("start_server", dataDir.absolutePath, serverPort)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun waitForServer() {
        Thread {
            var retries = 0
            val maxRetries = 60
            while (retries < maxRetries) {
                try {
                    val url = java.net.URL("http://127.0.0.1:$serverPort/api/health")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    val response = conn.responseCode
                    conn.disconnect()
                    if (response == 200) {
                        runOnUiThread { loadWebApp() }
                        return@Thread
                    }
                } catch (_: Exception) {
                }
                Thread.sleep(500)
                retries++
                
                if (retries % 10 == 0) {
                    runOnUiThread {
                        loadingText.text = "正在启动... (${retries / 10}s)"
                    }
                }
            }
            runOnUiThread {
                loadingText.text = "启动超时，请重试"
                progressBar.visibility = View.GONE
            }
        }.start()
    }

    private fun prepareDataDir(): File {
        val appDir = File(filesDir, "docblog")
        val staticDir = File(appDir, "static")
        val dataDir = File(appDir, "data")

        val versionFile = File(appDir, ".version")
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName.toString()
        } catch (e: Exception) {
            "1.0"
        }
        val needCopy = !versionFile.exists() || versionFile.readText() != currentVersion

        if (needCopy) {
            runOnUiThread {
                loadingText.text = "正在准备资源..."
            }
            
            copyAssetDir("static", staticDir)
            
            runOnUiThread {
                loadingText.text = "正在解压数据库..."
            }
            copyAssetDir("data", dataDir)
            
            val dbZip = File(dataDir, "docs.db.zip")
            val dbFile = File(dataDir, "docs.db")
            if (dbZip.exists()) {
                if (!dbFile.exists()) {
                    unzipFile(dbZip, dataDir)
                }
                dbZip.delete()
            }
            versionFile.writeText(currentVersion)
        }

        return appDir
    }

    private fun unzipFile(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!entry.isDirectory) {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { fos ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun copyAssetDir(assetPath: String, destDir: File) {
        val assetManager = assets
        val files = assetManager.list(assetPath) ?: return

        if (!destDir.exists()) destDir.mkdirs()

        for (file in files) {
            val srcPath = "$assetPath/$file"
            val destFile = File(destDir, file)

            val subFiles = assetManager.list(srcPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetDir(srcPath, destFile)
            } else {
                assetManager.open(srcPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun loadWebApp() {
        loadingContainer.visibility = View.GONE
        webView.loadUrl("http://127.0.0.1:$serverPort/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://127.0.0.1:$serverPort") || url.startsWith("http://localhost:$serverPort")) {
                    return false
                }
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        try {
            val py = Python.getInstance()
            val module = py.getModule("server_android")
            module.callAttr("stop_server")
        } catch (_: Exception) {
        }
        webView.destroy()
        executor.shutdownNow()
        super.onDestroy()
    }
}
