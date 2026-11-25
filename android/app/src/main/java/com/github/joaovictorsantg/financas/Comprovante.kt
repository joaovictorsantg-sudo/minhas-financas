package com.github.joaovictorsantg.financas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.graphics.BitmapFactory
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Comprovante que o banco compartilhou. Entra na mesma caixa das notificações,
 * porque dali em diante é a mesma coisa: um texto com valor e um nome.
 *
 * Nenhum banco compartilha texto. O Inter manda uma imagem e o PicPay um PDF
 * desenhado, sem letra nenhuma dentro — nos dois casos o jeito é ler o que
 * está escrito na figura, e o modelo que faz isso vem junto no aplicativo.
 */
object Comprovante {

  fun guardar(ctx: Context, titulo: String, texto: String): Boolean {
    val valor = Dinheiro.achar(texto) ?: Dinheiro.achar(titulo) ?: return false
    val item = JSONObject().apply {
      put("id", "share:" + System.currentTimeMillis())
      put("banco", "Comprovante")
      put("titulo", titulo.ifBlank { "Comprovante" })
      put("texto", texto.replace(Regex("[ \\t]+"), " ").take(1500))
      put("valor", valor)
      put("quando", System.currentTimeMillis())
    }
    val p = ctx.getSharedPreferences(Ponte.CAIXA, Context.MODE_PRIVATE)
    val atual = JSONArray(p.getString(Ponte.CHAVE, "[]"))
    atual.put(item)
    p.edit().putString(Ponte.CHAVE, atual.toString()).apply()
    return true
  }

  /** Lê a figura e devolve o resultado quando terminar — OCR demora um pouco. */
  fun lerFigura(ctx: Context, uri: Uri, tipo: String?, pronto: (Boolean) -> Unit) {
    val bmp = try {
      if (tipo?.contains("pdf") == true) doPdf(ctx, uri) else doArquivo(ctx, uri)
    } catch (e: Exception) { null }

    if (bmp == null) { pronto(false); return }

    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      .process(InputImage.fromBitmap(bmp, 0))
      .addOnSuccessListener { lido ->
        bmp.recycle()
        pronto(guardar(ctx, "Comprovante", emLinhas(lido)))
      }
      .addOnFailureListener {
        bmp.recycle()
        Toast.makeText(ctx, "Não consegui ler o comprovante", Toast.LENGTH_SHORT).show()
        pronto(false)
      }
  }

  /**
   * O comprovante tem duas colunas: rótulo à esquerda, valor à direita. Lido
   * bloco a bloco, sai uma coluna inteira e depois a outra, e "Instituição"
   * deixa de ter ao lado o banco a que pertence. Refazendo as linhas pela
   * altura em que estão na figura, cada rótulo volta a encontrar seu valor.
   */
  private fun emLinhas(t: com.google.mlkit.vision.text.Text): String {
    data class Peca(val y: Int, val x: Int, val alt: Int, val txt: String)
    val pecas = t.textBlocks.flatMap { it.lines }.mapNotNull { l ->
      l.boundingBox?.let { r -> Peca(r.centerY(), r.left, r.height(), l.text) }
    }.sortedBy { it.y }

    val linhas = mutableListOf<MutableList<Peca>>()
    for (p in pecas) {
      val atual = linhas.lastOrNull()
      // mesma linha quando os centros quase coincidem; meia altura é folga
      // bastante para letra de tamanhos diferentes lado a lado
      val junto = atual != null && kotlin.math.abs(atual[0].y - p.y) <= maxOf(p.alt, atual[0].alt) / 2
      if (junto) atual!!.add(p) else linhas.add(mutableListOf(p))
    }
    return linhas.joinToString("\n") { linha ->
      linha.sortedBy { it.x }.joinToString(" ") { it.txt }
    }
  }

  private fun doArquivo(ctx: Context, uri: Uri): Bitmap? =
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

  /**
   * Comprovante cabe numa página, e o dobro do tamanho basta para a letra
   * miúda do rodapé sair legível.
   */
  private fun doPdf(ctx: Context, uri: Uri): Bitmap? {
    val fd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return null
    fd.use {
      PdfRenderer(it).use { pdf ->
        if (pdf.pageCount == 0) return null
        pdf.openPage(0).use { pg ->
          val bmp = Bitmap.createBitmap(pg.width * 2, pg.height * 2, Bitmap.Config.ARGB_8888)
          Canvas(bmp).drawColor(Color.WHITE)
          pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          return bmp
        }
      }
    }
  }
}
