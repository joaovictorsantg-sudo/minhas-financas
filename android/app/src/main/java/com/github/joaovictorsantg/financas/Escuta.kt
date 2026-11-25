package com.github.joaovictorsantg.financas

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Escuta as notificações dos bancos. O Android só entrega alguma coisa aqui
 * depois que a pessoa concede acesso a notificações nas configurações.
 *
 * Nada é enviado para lugar nenhum: a notificação é guardada neste aparelho,
 * o app a lê quando abre e a descarta em seguida.
 */
class Escuta : NotificationListenerService() {

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val pacote = sbn.packageName ?: return
    val banco = Bancos.nome(pacote) ?: return

    val extras = sbn.notification?.extras ?: return
    val titulo = extras.getCharSequence("android.title")?.toString().orEmpty()
    val texto = (extras.getCharSequence("android.bigText")
      ?: extras.getCharSequence("android.text"))?.toString().orEmpty()
    if (titulo.isBlank() && texto.isBlank()) return

    val valor = Dinheiro.achar("$titulo $texto") ?: return  // sem valor não é gasto

    val item = JSONObject().apply {
      put("id", pacote + ":" + sbn.postTime)
      put("banco", banco)
      put("titulo", titulo)
      put("texto", texto)
      put("valor", valor)
      put("quando", sbn.postTime)
    }
    guardar(item)
  }

  private fun guardar(item: JSONObject) {
    val p = getSharedPreferences(Ponte.CAIXA, MODE_PRIVATE)
    val atual = JSONArray(p.getString(Ponte.CHAVE, "[]"))

    // a mesma notificação pode ser postada de novo quando o banco a atualiza
    val id = item.optString("id")
    for (i in 0 until atual.length()) {
      if (atual.optJSONObject(i)?.optString("id") == id) return
    }

    // O banco e a carteira avisam a mesma compra, cada um do seu jeito. Dois
    // avisos de mesmo valor em poucos minutos são o mesmo gasto: fica o
    // primeiro, que costuma ser o da carteira, e o segundo não entra.
    val valor = item.optDouble("valor", -1.0)
    val quando = item.optLong("quando", 0L)
    for (i in 0 until atual.length()) {
      val o = atual.optJSONObject(i) ?: continue
      if (Math.abs(o.optDouble("valor", -2.0) - valor) < 0.005 &&
          Math.abs(o.optLong("quando", 0L) - quando) < 10 * 60 * 1000
      ) return
    }

    atual.put(item)
    // guardar as últimas: isto é uma caixa de entrada, não um arquivo
    val podadas = JSONArray()
    val inicio = maxOf(0, atual.length() - 200)
    for (i in inicio until atual.length()) podadas.put(atual.get(i))

    p.edit().putString(Ponte.CHAVE, podadas.toString()).apply()
  }
}

/** Os bancos cujas notificações interessam, pelo nome do pacote. */
object Bancos {
  private val mapa = mapOf(
    "br.com.intermedium" to "Inter",
    "com.picpay" to "PicPay",
    "com.nu.production" to "Nubank",
    "com.itau" to "Itaú",
    "com.bradesco" to "Bradesco",
    "br.com.bb.android" to "Banco do Brasil",
    "com.santander.app" to "Santander",
    "br.com.original.bank" to "Original",
    "com.c6bank.app" to "C6",
    "br.com.neon" to "Neon",
    "com.mercadopago.wallet" to "Mercado Pago",
    // pagamento por aproximação costuma ser avisado pela carteira, não pelo banco
    "com.google.android.apps.walletnfcrel" to "Google Wallet",
    "com.google.android.apps.wallet" to "Google Wallet",
    "com.samsung.android.spay" to "Samsung Wallet",
    "com.samsung.android.spaylite" to "Samsung Wallet",
  )
  fun nome(pacote: String): String? = mapa[pacote]
}

/** Acha um valor em reais no texto da notificação. */
object Dinheiro {
  private val padrao = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*|\d+)(?:,(\d{2}))?""")

  fun achar(texto: String): Double? {
    val m = padrao.find(texto) ?: return null
    val inteiro = m.groupValues[1].replace(".", "")
    val centavos = m.groupValues[2].ifEmpty { "00" }
    return "$inteiro.$centavos".toDoubleOrNull()
  }
}
