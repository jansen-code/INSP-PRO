package com.raylson.jansen.inspetor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * RecorteDocumentoEngine — substitui o antigo ScannerEngine (ML Kit/Google).
 * Faz o "endireitar" (rotação) e o "achatar" (correção de perspectiva) só
 * com APIs nativas do Android — nenhuma dependência externa, nenhuma tela
 * ou marca do Google.
 *
 * O truque da perspectiva: Matrix.setPolyToPoly, quando recebe exatamente
 * 4 pontos de origem e 4 de destino, calcula uma transformação de
 * perspectiva de verdade (não só afim) — é o mesmo princípio usado por
 * apps de scanner que não dependem de OpenCV.
 *
 * Tudo aqui é síncrono e pode ser pesado com fotos grandes — SEMPRE chame
 * a partir de uma coroutine em Dispatchers.Default, nunca na main thread.
 */
object RecorteDocumentoEngine {

    /** Gira a imagem inteira em torno do próprio centro (usado pelo slider "endireitar"). */
    fun rotacionar(origem: Bitmap, graus: Float): Bitmap {
        if (graus == 0f) return origem
        val m = Matrix().apply { postRotate(graus) }
        return Bitmap.createBitmap(origem, 0, 0, origem.width, origem.height, m, true)
    }

    /**
     * Corta e achata o documento a partir dos 4 cantos (TL, TR, BR, BL, em
     * coordenadas da própria imagem `origem`), devolvendo uma imagem
     * retangular "reta e plana" — sem trapézio, sem distorção de perspectiva.
     */
    fun recortarEAchatar(origem: Bitmap, cantos: Array<PointF>): Bitmap {
        val tl = cantos[0]; val tr = cantos[1]; val br = cantos[2]; val bl = cantos[3]

        val larguraTopo  = distancia(tl, tr)
        val larguraBase  = distancia(bl, br)
        val larguraFinal = max(larguraTopo, larguraBase).roundToInt().coerceAtLeast(1)

        val alturaEsquerda = distancia(tl, bl)
        val alturaDireita  = distancia(tr, br)
        val alturaFinal    = max(alturaEsquerda, alturaDireita).roundToInt().coerceAtLeast(1)

        val saida = Bitmap.createBitmap(larguraFinal, alturaFinal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(saida)
        canvas.drawColor(Color.WHITE)

        // origem = os 4 cantos escolhidos na foto | destino = retângulo final "reto"
        val src = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dst = floatArrayOf(
            0f, 0f,
            larguraFinal.toFloat(), 0f,
            larguraFinal.toFloat(), alturaFinal.toFloat(),
            0f, alturaFinal.toFloat()
        )

        val matriz = Matrix()
        matriz.setPolyToPoly(src, 0, dst, 0, 4)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(origem, matriz, paint)
        return saida
    }

    private fun distancia(a: PointF, b: PointF): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}
