package com.github.joaovictorsantg.financas

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import org.json.JSONArray
import java.io.File

/**
 * O que a página enxerga do aparelho: ler as notificações guardadas,
 * descartá-las, pedir a permissão e gravar um arquivo em Downloads.
 * Nada que escreva nos dados dela.
 */
class Ponte(private val ctx: Context) {

  companion object {
    const val CAIXA = "notificacoes"
    const val CHAVE = "pendentes"
  }

  @JavascriptInterface
  fun versao(): Int = 1

  /** As notificações de banco ainda não processadas, como JSON. */
  @JavascriptInterface
  fun pendentes(): String =
    ctx.getSharedPreferences(CAIXA, Context.MODE_PRIVATE).getString(CHAVE, "[]") ?: "[]"

  /** Descarta as que a página já transformou em lançamento. */
  @JavascriptInterface
  fun descartar(ids: String) {
    val fora = mutableSetOf<String>()
    runCatching {
      val a = JSONArray(ids)
      for (i in 0 until a.length()) fora.add(a.getString(i))
    }
    if (fora.isEmpty()) return

    val p = ctx.getSharedPreferences(CAIXA, Context.MODE_PRIVATE)
    val atual = JSONArray(p.getString(CHAVE, "[]"))
    val ficam = JSONArray()
    for (i in 0 until atual.length()) {
      val o = atual.optJSONObject(i) ?: continue
      if (o.optString("id") !in fora) ficam.put(o)
    }
    p.edit().putString(CHAVE, ficam.toString()).apply()
  }

  /** Se o Android já está entregando notificações a este app. */
  @JavascriptInterface
  fun temPermissao(): Boolean {
    val ativos = Settings.Secure.getString(
      ctx.contentResolver, "enabled_notification_listeners"
    ).orEmpty()
    return ativos.contains(ctx.packageName)
  }

  /** Abre a tela onde a permissão é concedida. */
  @JavascriptInterface
  fun pedirPermissao() {
    ctx.startActivity(
      Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }

  /**
   * Grava um arquivo em Downloads. O download comum não sai de dentro de um
   * WebView, e sem isto não haveria como tirar o backup de dentro do app.
   * Devolve onde salvou, ou vazio se não deu.
   */
  @JavascriptInterface
  fun guardarArquivo(nome: String, base64: String): String {
    val limpo = nome.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "arquivo" }
    val dados = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return ""
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val valores = ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, limpo)
          put(MediaStore.Downloads.MIME_TYPE, "application/json")
          put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val alvo = ctx.contentResolver.insert(
          MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores
        ) ?: return ""
        ctx.contentResolver.openOutputStream(alvo)?.use { it.write(dados) } ?: return ""
        ctx.contentResolver.update(alvo, ContentValues().apply {
          put(MediaStore.Downloads.IS_PENDING, 0)
        }, null, null)
      } else {
        val pasta = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        pasta.mkdirs()
        File(pasta, limpo).writeBytes(dados)
      }
      "Downloads/$limpo"
    }.getOrDefault("")
  }
}
