package com.github.joaovictorsantg.financas

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class Principal : AppCompatActivity() {

  private lateinit var raiz: FrameLayout
  private lateinit var web: WebView

  /** A janela que o conector do banco abre por cima, quando abre. */
  private var janela: View? = null

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

    raiz = FrameLayout(this)
    web = novaWeb(principal = true)
    raiz.addView(web)
    setContentView(raiz)

    if (estado == null) web.loadUrl(SITE) else web.restoreState(estado)
    receber(intent)
  }

  override fun onNewIntent(novo: Intent?) {
    super.onNewIntent(novo)
    intent = novo
    receber(novo)
  }

  /**
   * Comprovante compartilhado pelo banco. Pix enviado é o único movimento que
   * não gera notificação, e o comprovante traz o valor e para quem foi.
   */
  private fun receber(i: Intent?) {
    if (i?.action != Intent.ACTION_SEND) return
    val figura = i.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
    if (figura != null) {
      Toast.makeText(this, "Lendo o comprovante…", Toast.LENGTH_SHORT).show()
      Comprovante.lerFigura(this, figura, i.type) { deu -> if (deu) avisarWeb() }
      return
    }
    val texto = (i.getStringExtra(Intent.EXTRA_TEXT) ?: return).trim()
    if (texto.isBlank()) return
    val titulo = i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
    if (Comprovante.guardar(this, titulo, texto)) avisarWeb()
  }

  private fun avisarWeb() {
    web.post { web.evaluateJavascript("window.dispatchEvent(new Event('focus'))", null) }
  }

  @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
  private fun novaWeb(principal: Boolean): WebView = WebView(this).apply {
    layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
    )
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true        // os dados moram aqui
    settings.databaseEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.setSupportMultipleWindows(true) // o conector do banco abre janela
    settings.javaScriptCanOpenWindowsAutomatically = true
    webViewClient = WebViewClient()
    if (principal) addJavascriptInterface(Ponte(this@Principal), "Aparelho")

    webChromeClient = object : WebChromeClient() {
      override fun onCreateWindow(
        v: WebView?, dialog: Boolean, gesto: Boolean, msg: Message?
      ): Boolean {
        val filha = abrirJanela() ?: return false
        (msg?.obj as? WebView.WebViewTransport)?.webView = filha
        msg?.sendToTarget()
        return true
      }

      override fun onCloseWindow(w: WebView?) { fecharJanela() }

      // sem isto, restaurar backup não chega nem a abrir o seletor
      override fun onShowFileChooser(
        v: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?
      ): Boolean {
        esperandoArquivo?.onReceiveValue(null)
        esperandoArquivo = callback
        return runCatching {
          escolher.launch(params!!.createIntent()); true
        }.getOrElse {
          esperandoArquivo = null; callback?.onReceiveValue(null); false
        }
      }
    }
  }

  /**
   * Monta a janela de cima com uma barra de fechar. O conector termina numa
   * página que diz "pode fechar esta janela" e não fecha nada sozinha: sem a
   * barra, não há como voltar ao app.
   */
  private fun abrirJanela(): WebView? {
    fecharJanela()
    val caixa = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.parseColor("#0E0F12"))
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
      )
    }
    val fechar = Button(this).apply {
      text = "Fechar e voltar ao app"
      setOnClickListener { fecharJanela() }
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply { gravity = Gravity.CENTER }
    }
    val filha = novaWeb(principal = false)
    filha.layoutParams = LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
    )
    caixa.addView(fechar)
    caixa.addView(filha)
    raiz.addView(caixa)
    janela = View(caixa, filha)
    return filha
  }

  private fun fecharJanela() {
    val j = janela ?: return
    janela = null
    raiz.removeView(j.caixa)
    j.web.destroy()
    // o conector avisa a página quando termina; se não avisou, uma olhada basta
    web.evaluateJavascript("window.dispatchEvent(new Event('focus'))", null)
  }

  private class View(val caixa: LinearLayout, val web: WebView)

  override fun onSaveInstanceState(fora: Bundle) {
    super.onSaveInstanceState(fora); web.saveState(fora)
  }

  @Deprecated("deprecated")
  override fun onBackPressed() {
    val j = janela
    when {
      j != null && j.web.canGoBack() -> j.web.goBack()
      j != null -> fecharJanela()
      // fora do site e sem janela: um toque volta ao app, em vez de desandar
      // o caminho inteiro do banco passo a passo
      web.url?.startsWith(SITE) == false -> web.loadUrl(SITE)
      web.canGoBack() -> web.goBack()
      else -> super.onBackPressed()
    }
  }

  companion object {
    const val SITE = "https://joaovictorsantg-sudo.github.io/minhas-financas/"
  }
}
