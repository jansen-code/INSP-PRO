package com.raylson.jansen.inspetor

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.min
import kotlin.math.sqrt

/**
 * DetectorBordaEngine — detecção automática das bordas do documento,
 * 100% Kotlin puro. Sem OpenCV, sem ML Kit, sem nenhuma lib externa.
 *
 * ═══ ESTRATÉGIA (resumo) ═══
 *  1) Reduz a foto pra ~480px de largura só pra análise (rápido, não
 *     mexe na foto original em resolução real).
 *  2) Converte pra escala de cinza.
 *  3) Calcula o gradiente (operador Sobel) pra achar onde tem borda forte.
 *  4) Binariza esse gradiente (é borda / não é borda) com um limiar
 *     calculado a partir da média + desvio padrão da própria foto (se
 *     adapta a fotos mais claras ou mais escuras).
 *  5) "Inunda" (flood fill / BFS) a partir do CENTRO da foto, andando só
 *     por pixels que NÃO são borda — isso separa o miolo do documento,
 *     assumindo que ele fica mais ou menos centralizado (é como o app
 *     orienta o usuário a fotografar).
 *  6) Pega os 4 pontos mais extremos dessa área: o canto que minimiza
 *     (x+y) é o topo-esquerda, o que maximiza é o baixo-direita, e por
 *     aí vai — é a mesma sacada usada em vários scanners simples sem
 *     OpenCV pra achar os 4 cantos de um contorno.
 *  7) Escala os 4 pontos de volta pra resolução da foto original.
 *
 * Se qualquer verificação de sanidade falhar (região grande/pequena
 * demais, centro caiu em cima de uma borda, etc.), devolve `null` — quem
 * chamar deve cair de volta pra margem fixa de 6% que já existia.
 *
 * IMPORTANTE: isso faz várias passadas pixel a pixel. SEMPRE chame de
 * dentro de uma coroutine em Dispatchers.Default, nunca na main thread.
 */
object DetectorBordaEngine {

    private const val LARGURA_ANALISE = 480

    /** Cantos sugeridos (TL, TR, BR, BL) em coordenadas da foto ORIGINAL, ou null se não detectou com confiança. */
    fun detectarCantos(origem: Bitmap): Array<PointF>? {
        return try {
            if (origem.width < 50 || origem.height < 50) return null

            val escala = min(1f, LARGURA_ANALISE / origem.width.toFloat())
            val wPeq = (origem.width * escala).toInt().coerceAtLeast(10)
            val hPeq = (origem.height * escala).toInt().coerceAtLeast(10)
            val pequena = Bitmap.createScaledBitmap(origem, wPeq, hPeq, true)

            val cinza = paraCinza(pequena, wPeq, hPeq)
            pequena.recycle()

            val magnitude = sobel(cinza, wPeq, hPeq)

            // ═══ MAIS ROBUSTO: antes só tentava UM limiar de sensibilidade
            // fixo (média + 1,3 desvios). Se a foto fosse mais clara, mais
            // escura, com sombra, ou o contraste do documento contra a
            // mesa não batesse exatamente com esse ponto, a detecção
            // falhava (devolvia null) e caía sempre na margem fixa de 6%.
            // Agora tenta algumas variações de sensibilidade, da mais
            // "seletiva" pra mais "generosa", e fica com a PRIMEIRA que
            // achar um resultado plausível — aumenta bastante a taxa de
            // acerto sem custar muito mais tempo (a mesma imagem pequena
            // já calculada é reaproveitada, só muda o limiar). ═══
            val multiplicadores = floatArrayOf(1.3f, 0.9f, 1.8f, 0.6f)
            for (mult in multiplicadores) {
                val resultado = tentarDetectar(magnitude, wPeq, hPeq, origem, mult)
                if (resultado != null) return resultado
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun tentarDetectar(magnitude: FloatArray, wPeq: Int, hPeq: Int, origem: Bitmap, multiplicadorLimiar: Float): Array<PointF>? {
        val limiar = calcularLimiar(magnitude, multiplicadorLimiar)

        var isBorda = BooleanArray(wPeq * hPeq) { magnitude[it] > limiar }
        isBorda = dilatar(isBorda, wPeq, hPeq)

        val dentro = BooleanArray(wPeq * hPeq)
        val totalDentro = floodFillCentro(isBorda, dentro, wPeq, hPeq)
        val proporcao = totalDentro.toFloat() / (wPeq * hPeq)

        // Se preencheu quase a foto toda ou quase nada, a detecção
        // não é confiável — melhor devolver null do que um resultado ruim.
        if (proporcao < 0.08f || proporcao > 0.92f) return null

        val cantosPeq = extrairExtremos(dentro, wPeq, hPeq) ?: return null

        // Escala de volta pra resolução original + uma margem de
        // segurança de 1,5% pra fora (a máscara interna fica um
        // pouco pra dentro da borda real do documento).
        val fatorX = origem.width / wPeq.toFloat()
        val fatorY = origem.height / hPeq.toFloat()
        val margem = 0.015f
        val cx = wPeq / 2f
        val cy = hPeq / 2f

        return Array(4) { i ->
            val p = cantosPeq[i]
            val px = p.x + (p.x - cx) * margem
            val py = p.y + (p.y - cy) * margem
            PointF(
                (px * fatorX).coerceIn(0f, origem.width.toFloat()),
                (py * fatorY).coerceIn(0f, origem.height.toFloat())
            )
        }
    }

    private fun paraCinza(bmp: Bitmap, w: Int, h: Int): IntArray {
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val cinza = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            cinza[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return cinza
    }

    private fun sobel(cinza: IntArray, w: Int, h: Int): FloatArray {
        val mag = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = (cinza[i - w - 1] + 2 * cinza[i - 1] + cinza[i + w - 1]) -
                         (cinza[i - w + 1] + 2 * cinza[i + 1] + cinza[i + w + 1])
                val gy = (cinza[i - w - 1] + 2 * cinza[i - w] + cinza[i - w + 1]) -
                         (cinza[i + w - 1] + 2 * cinza[i + w] + cinza[i + w + 1])
                mag[i] = sqrt((gx * gx + gy * gy).toFloat())
            }
        }
        return mag
    }

    private fun calcularLimiar(mag: FloatArray, multiplicador: Float): Float {
        var soma = 0.0
        var somaQuad = 0.0
        for (v in mag) { soma += v; somaQuad += v.toDouble() * v }
        val n = mag.size.toDouble()
        val media = soma / n
        val variancia = (somaQuad / n) - (media * media)
        val desvio = sqrt(variancia.coerceAtLeast(0.0))
        // Limiar adaptativo: se adapta a fotos mais claras/escuras/contrastadas.
        return (media + desvio * multiplicador).toFloat().coerceAtLeast(30f)
    }

    private fun dilatar(borda: BooleanArray, w: Int, h: Int): BooleanArray {
        // Fecha pequenas falhas na linha de borda, pra evitar que o
        // flood fill "vaze" por um buraco de 1 pixel.
        val out = borda.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (borda[i]) continue
                if (borda[i - 1] || borda[i + 1] || borda[i - w] || borda[i + w]) {
                    out[i] = true
                }
            }
        }
        return out
    }

    /** BFS a partir do centro da foto, andando só por pixels que não são borda. */
    private fun floodFillCentro(isBorda: BooleanArray, dentro: BooleanArray, w: Int, h: Int): Int {
        val cx = w / 2
        val cy = h / 2
        val inicio = cy * w + cx
        if (isBorda[inicio]) return 0 // centro caiu em cima de uma borda — não dá pra confiar

        val fila = ArrayDeque<Int>()
        fila.add(inicio)
        dentro[inicio] = true
        var total = 1

        while (fila.isNotEmpty()) {
            val i = fila.removeFirst()
            val x = i % w
            val y = i / w

            if (x > 0)     { val j = i - 1; if (!dentro[j] && !isBorda[j]) { dentro[j] = true; total++; fila.add(j) } }
            if (x < w - 1) { val j = i + 1; if (!dentro[j] && !isBorda[j]) { dentro[j] = true; total++; fila.add(j) } }
            if (y > 0)     { val j = i - w; if (!dentro[j] && !isBorda[j]) { dentro[j] = true; total++; fila.add(j) } }
            if (y < h - 1) { val j = i + w; if (!dentro[j] && !isBorda[j]) { dentro[j] = true; total++; fila.add(j) } }
        }
        return total
    }

    /** TL = min(x+y) | BR = max(x+y) | TR = max(x-y) | BL = min(x-y). */
    private fun extrairExtremos(dentro: BooleanArray, w: Int, h: Int): Array<PointF>? {
        var tl = -1; var br = -1; var tr = -1; var bl = -1
        var somaMin = Int.MAX_VALUE; var somaMax = Int.MIN_VALUE
        var diffMax = Int.MIN_VALUE; var diffMin = Int.MAX_VALUE

        for (i in dentro.indices) {
            if (!dentro[i]) continue
            val x = i % w
            val y = i / w
            val soma = x + y
            val diff = x - y
            if (soma < somaMin) { somaMin = soma; tl = i }
            if (soma > somaMax) { somaMax = soma; br = i }
            if (diff > diffMax) { diffMax = diff; tr = i }
            if (diff < diffMin) { diffMin = diff; bl = i }
        }
        if (tl == -1 || br == -1 || tr == -1 || bl == -1) return null

        fun pf(i: Int) = PointF((i % w).toFloat(), (i / w).toFloat())
        return arrayOf(pf(tl), pf(tr), pf(br), pf(bl))
    }
}
