package com.github.joaovictorsantg.financas

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class Principal : AppCompatActivity() {

  private lateinit var web: WebView

  @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
  override fun onCreate(estado: Bundle?) {
    super.onCreate(estado)

    web = WebView(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
      )
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true        // os dados moram aqui
      settings.databaseEnabled = true
      settings.mediaPlaybackRequiresUserGesture = false
      webChromeClient = WebChromeClient()
      // links do app abrem no app; qualquer outro endereço vai para o navegador
      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean {
          if (url.startsWith(SITE)) return false
          startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
          return true
        }
      }
      addJavascriptInterface(Ponte(this@Principal), "Aparelho")
    }
    setContentView(web)

    if (estado == null) web.loadUrl(SITE) else web.restoreState(estado)
  }

  override fun onSaveInstanceState(fora: Bundle) {
    super.onSaveInstanceState(fora); web.saveState(fora)
  }

  @Deprecated("deprecated")
  override fun onBackPressed() {
    if (web.canGoBack()) web.goBack() else super.onBackPressed()
  }

  companion object {
    const val SITE = "https://joaovictorsantg-sudo.github.io/minhas-financas/"
  }
}
