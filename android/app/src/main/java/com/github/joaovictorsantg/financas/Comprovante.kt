package com.github.joaovictorsantg.financas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Comprovante que o banco compartilhou. Entra na mesma caixa das notificações,
 * porque dali em diante é a mesma coisa: um texto com valor e um nome.
 */
object Comprovante {

  fun guardar(ctx: Context, titulo: String, texto: String): Boolean {
    val valor = Dinheiro.achar(texto) ?: Dinheiro.achar(titulo) ?: return false

    val item = JSONObject().apply {
      put("id", "share:" + System.currentTimeMillis())
      put("banco", "Comprovante")
      put("titulo", titulo.ifBlank { "Comprovante" })
      // o comprovante é longo e a tela mostra o começo: o essencial vem antes
      put("texto", texto.replace(Regex("\\s+"), " ").take(400))
      put("valor", valor)
      put("quando", System.currentTimeMillis())
    }

    val p = ctx.getSharedPreferences(Ponte.CAIXA, Context.MODE_PRIVATE)
    val atual = JSONArray(p.getString(Ponte.CHAVE, "[]"))
    atual.put(item)
    p.edit().putString(Ponte.CHAVE, atual.toString()).apply()
    return true
  }
}
