package tv.saimo.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar

class MainActivity : Activity() {

    private val HOME_URL = "https://saimo-tv.pages.dev/"

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar

    // Suporte a vídeo fullscreen via WebChromeClient
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val chromeClient = object : WebChromeClient() {

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            // Esconde a WebView e mostra o vídeo em tela cheia
            customView = view
            customViewCallback = callback
            web.visibility = View.GONE
            root.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        override fun onHideCustomView() {
            // Volta da tela cheia do vídeo
            customView?.let { root.removeView(it) }
            customViewCallback?.onCustomViewHidden()
            customView = null
            web.visibility = View.VISIBLE
            web.requestFocus()
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress.progress = newProgress
            progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
        }
    }

    private val webViewClient = object : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Mantém tudo dentro da própria WebView
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            progress.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            progress.visibility = View.GONE
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            // Tenta recarregar se for a URL principal e houver conexão
            if (request?.isForMainFrame == true && isConnected()) {
                view?.reload()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantém a tela ligada enquanto o app está aberto
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Layout raiz
        root = FrameLayout(this)
        setContentView(root)

        // Barra de progresso fina no topo
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                8  // altura em px
            )
        }

        // WebView
        web = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }

        root.addView(web)
        root.addView(progress)

        // Configurações da WebView
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false   // Vídeos iniciam sem gesto
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(false)
            // User-Agent com "TV" para o site saber que é TV
            userAgentString = userAgentString + " SaimoTV/1.0 (AndroidTV)"
        }

        web.webViewClient = webViewClient
        web.webChromeClient = chromeClient
        web.requestFocus()

        // Restaura estado após rotação ou carrega a URL inicial
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl(HOME_URL)
        }
    }

    // ── Controle remoto ──────────────────────────────────────────────────────
    // As setas, OK e teclas de mídia chegam direto na WebView (que está com foco).

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            customView != null -> chromeClient.onHideCustomView()  // Sai do fullscreen
            web.canGoBack() -> web.goBack()                        // Volta uma página
            else -> super.onBackPressed()                          // Fecha o app
        }
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.requestFocus()
    }

    override fun onDestroy() {
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }

    // ── Utilitários ──────────────────────────────────────────────────────────

    private fun isConnected(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }
}
