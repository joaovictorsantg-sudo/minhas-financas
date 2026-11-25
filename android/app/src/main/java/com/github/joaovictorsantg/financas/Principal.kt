package com.github.joaovictorsantg.financas

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class Principal : AppCompatActivity() {

  private lateinit var web: WebView

  /** Quem está esperando o arquivo escolhido, se alguém estiver. */
  private var esperandoArquivo: ValueCallback<Array<Uri>>? = null

  private val escolher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { r ->
    val cb = esperandoArquivo ?: return@registerForActivityResult
    esperandoArquivo = null
    // cancelar precisa devolver null, ou o campo de arquivo trava para sempre
    cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
  }

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

      webChromeClient = object : WebChromeClient() {
        // sem isto, restaurar backup não chega nem a abrir o seletor
        override fun onShowFileChooser(
          v: WebView?,
          callback: ValueCallback<Array<Uri>>?,
          params: FileChooserParams?
        ): Boolean {
          esperandoArquivo?.onReceiveValue(null)   // um pedido anterior que ficou aberto
          esperandoArquivo = callback
          return runCatching {
            escolher.launch(params!!.createIntent()); true
          }.getOrElse {
            esperandoArquivo = null; callback?.onReceiveValue(null); false
          }
        }
      }

      // Tudo navega aqui dentro. O conector do Open Finance leva a páginas do
      // agregador e do banco, e mandá-las para fora interromperia a conexão no
      // meio; o botão de voltar do aparelho traz de volta.
      webViewClient = WebViewClient()
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
