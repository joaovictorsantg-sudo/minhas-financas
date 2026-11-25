package com.github.joaovictorsantg.financas

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.webkit.JavascriptInterface
import org.json.JSONArray

/**
 * O que a página enxerga do aparelho. Só isto: ler as notificações guardadas,
 * descartá-las e abrir a tela de permissão. Nada que escreva nos dados dela.
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
}
