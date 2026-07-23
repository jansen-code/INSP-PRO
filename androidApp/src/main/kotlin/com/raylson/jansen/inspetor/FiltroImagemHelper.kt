package com.raylson.jansen.inspetor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import android.widget.ImageView
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.ScriptIntrinsicConvolve3x3

/**
 * Helper responsável por aplicar TODOS os filtros da régua sobre a
 * camada de foto (nunca sobre a tarja/overlay).
 *
 *  Modos suportados:
 *   • BRILHO       — ColorMatrix (rápido, em tempo real)
 *   • NITIDEZ      — ColorMatrix de contraste local (rápido, em tempo real)
 *   • VETORIZACAO  — pipeline pesado (convolução + unsharp mask) aplicado
 *                    APENAS no momento de salvar/compartilhar.
 *
 *  Durante a interação com a régua (preview ao vivo) usamos um atalho leve
 *  do modo VETORIZACAO baseado em ColorMatrix (nitidez + saturação), para
 *  não travar a UI. O tratamento pesado real acontece somente em
 *  [fundirCamadasParaSalvar] / [aplicarVetorizacaoFinal].
 */
object FiltroImagemHelper {

    private const val TAG = "FiltroImagemHelper"

    // ─────────────────────────────────────────────────────────────────────
    //  1. PREVIEW AO VIVO  (não altera bitmaps — só aplica ColorFilter)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Aplica o filtro do modo ativo como ColorFilter no ImageView.
     *  – Não modifica o Bitmap subjacente.
     *  – Rápido o suficiente para rodar em cada movimento da régua.
     *
     * Para VETORIZACAO usamos apenas um preview leve (nitidez + saturação
     * sutil). O processamento pesado real é feito ao salvar.
     */
    fun aplicarFiltroAoVivo(imageView: ImageView, modo: ReguaVerticalView.Modo, valor: Float) {
        val v = valor.coerceIn(-1f, 1f)
        val cm = when (modo) {
            ReguaVerticalView.Modo.BRILHO      -> matrizBrilho(v)
            ReguaVerticalView.Modo.NITIDEZ     -> matrizNitidez(v)
            ReguaVerticalView.Modo.VETORIZACAO -> matrizVetorizacaoPreview(v)
        }
        imageView.colorFilter = ColorMatrixColorFilter(cm)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  2. FUSÃO FINAL DAS CAMADAS  (para salvar / compartilhar)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Mantida para compatibilidade — chama a versão nova com valorVetorizacao = 0f.
     */
    fun fundirCamadasParaSalvar(
        bmpLimpo: Bitmap,
        bmpOverlay: Bitmap,
        modo: ReguaVerticalView.Modo,
        valor: Float
    ): Bitmap = fundirCamadasParaSalvar(bmpLimpo, bmpOverlay, modo, valor, 0f)

    /**
     * Nova versão: aceita explicitamente o valor de VETORIZACAO.
     *
     * Fluxo:
     *  1. Cria uma cópia da foto limpa.
     *  2. Aplica o filtro do modo ativo NA FOTO.
     *     – Para BRILHO/NITIDEZ: ColorMatrix "queimado" no bitmap.
     *     – Para VETORIZACAO:    convolução + unsharp mask (pesado).
     *  3. Se valorVetorizacao != 0 (regra: independente do modo ativo,
     *     se o usuário mexeu na régua de VETORIZACAO uma vez, o efeito
     *     é preservado), aplica também a vetorização.
     *  4. Desenha a tarja (overlay) por cima — sem filtro nenhum.
     */
    fun fundirCamadasParaSalvar(
        bmpLimpo: Bitmap,
        bmpOverlay: Bitmap,
        modo: ReguaVerticalView.Modo,
        valor: Float,
        valorVetorizacao: Float
    ): Bitmap {
        // 1. Base: cópia editável da foto limpa
        var fotoEditada: Bitmap = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)

        // 2. Aplica o filtro do modo ativo (ColorMatrix "queimado")
        //    – exceto VETORIZACAO, que é aplicada em passo separado
        when (modo) {
            ReguaVerticalView.Modo.BRILHO ->
                fotoEditada = aplicarColorMatrixNoBitmap(fotoEditada, matrizBrilho(valor))
            ReguaVerticalView.Modo.NITIDEZ ->
                fotoEditada = aplicarColorMatrixNoBitmap(fotoEditada, matrizNitidez(valor))
            ReguaVerticalView.Modo.VETORIZACAO -> {
                // Se o modo ativo É vetorização, `valor` já é o valor da vetorização
                if (Math.abs(valor) > 0.01f) {
                    fotoEditada = aplicarVetorizacaoFinal(fotoEditada, valor)
                }
            }
        }

        // 3. Se veio um valorVetorizacao ≠ 0 SEPARADAMENTE do modo ativo,
        //    aplica também (permite combinar Brilho + Vetorização, por ex.)
        if (modo != ReguaVerticalView.Modo.VETORIZACAO && Math.abs(valorVetorizacao) > 0.01f) {
            fotoEditada = aplicarVetorizacaoFinal(fotoEditada, valorVetorizacao)
        }

        // 4. Fusão final: foto tratada + tarja intacta
        val resultado = Bitmap.createBitmap(
            fotoEditada.width, fotoEditada.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultado)
        canvas.drawBitmap(fotoEditada, 0f, 0f, null)
        // Overlay desenhado por cima, escalado se necessário — SEM filtro
        if (bmpOverlay.width == fotoEditada.width && bmpOverlay.height == fotoEditada.height) {
            canvas.drawBitmap(bmpOverlay, 0f, 0f, null)
        } else {
            val src = android.graphics.Rect(0, 0, bmpOverlay.width, bmpOverlay.height)
            val dst = android.graphics.Rect(0, 0, fotoEditada.width, fotoEditada.height)
            canvas.drawBitmap(bmpOverlay, src, dst, null)
        }

        if (fotoEditada != bmpLimpo && !fotoEditada.isRecycled) fotoEditada.recycle()
        return resultado
    }

    // ─────────────────────────────────────────────────────────────────────
    //  3. VETORIZAÇÃO / ANTI-SERRILHADO  (função pesada — só ao salvar)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Pipeline de "vetorização" que:
     *   1. Suaviza serrilhados/ruído com um blur gaussiano leve.
     *   2. Realça bordas via Unsharp Mask (original + k*(original - blur)).
     *   3. Aumenta ligeiramente saturação e contraste, mantendo o
     *      aspecto FOTOGRÁFICO (não vira desenho).
     *
     *  @param intensidade valor da régua no intervalo [-1f, 1f].
     *                     – Valores ≤ 0 aplicam apenas anti-serrilhado suave.
     *                     – Valores > 0 intensificam o realce de bordas.
     */
    fun aplicarVetorizacaoFinal(bitmap: Bitmap, intensidade: Float): Bitmap {
        val v = intensidade.coerceIn(-1f, 1f)
        if (Math.abs(v) < 0.05f) return bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val src = if (bitmap.config != Bitmap.Config.ARGB_8888)
            bitmap.copy(Bitmap.Config.ARGB_8888, true) else bitmap

        val out = if (v < 0f) {
            // Valores negativos: Suavizar (Anti-serrilhado suave)
            val raio = (-v * 4f).coerceAtLeast(0.5f)
            try { aplicarBlurRenderScript(src, raio) } catch (e: Exception) { aplicarBlurConvolucao(src) }
        } else {
            // Valores positivos: Vetorizar (Definir linhas/bordas diretamente na GPU)
            aplicarSharpenRenderScript(src, v)
        }
        
        if (src != bitmap && !src.isRecycled) src.recycle()
        return out
    }
    
        @Suppress("DEPRECATION")
    private fun aplicarSharpenRenderScript(src: Bitmap, forca: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val rs = try { RenderScript.create(null) } catch (e: Exception) { null }
        
        // Se o celular for muito antigo e não suportar RenderScript, usa o contraste seguro
        if (rs == null) return aplicarColorMatrixNoBitmap(src, matrizVetorizacaoPreview(forca))
        
        try {
            val allocIn = Allocation.createFromBitmap(rs, src)
            val allocOut = Allocation.createFromBitmap(rs, out)
            val conv = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs))
            
            // Cria a matriz de realce de bordas baseada na força escolhida
            val k = forca * 1.5f
            val center = 1f + (4f * k)
            val edge = -k
            
            conv.setCoefficients(floatArrayOf(
                0f, edge, 0f,
                edge, center, edge,
                0f, edge, 0f
            ))
            
            conv.setInput(allocIn)
            conv.forEach(allocOut)
            allocOut.copyTo(out)
            
            allocIn.destroy(); allocOut.destroy(); conv.destroy()
        } finally {
            rs.destroy()
        }
        return out
    }


    // ─────────────────────────────────────────────────────────────────────
    //  4. MATRIZES DE COR
    // ─────────────────────────────────────────────────────────────────────

    /**
     * BRILHO — [-1, 1] → escurece/clareia usando offset uniforme + leve
     * ajuste de contraste para não "lavar" a imagem.
     */
    private fun matrizBrilho(v: Float): ColorMatrix {
        val offset = v * 70f                    // -70..+70
        val contraste = 1f + v * 0.15f          // 0.85..1.15
        val t = (1f - contraste) * 128f
        return ColorMatrix(floatArrayOf(
            contraste, 0f, 0f, 0f, t + offset,
            0f, contraste, 0f, 0f, t + offset,
            0f, 0f, contraste, 0f, t + offset,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /**
     * NITIDEZ (aparente) — [-1, 1] → ajusta contraste + saturação.
     * Não é sharpen real (isso exigiria convolução) mas dá a percepção
     * de "mais definida" em tempo real.
     */
    private fun matrizNitidez(v: Float): ColorMatrix {
        val contraste = 1f + v * 0.55f          // 0.45..1.55
        val t = (1f - contraste) * 128f
        val saturacao = 1f + v * 0.35f          // 0.65..1.35
        val mContraste = ColorMatrix(floatArrayOf(
            contraste, 0f, 0f, 0f, t,
            0f, contraste, 0f, 0f, t,
            0f, 0f, contraste, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        val mSat = ColorMatrix().apply { setSaturation(saturacao) }
        mContraste.postConcat(mSat)
        return mContraste
    }

    /**
     * VETORIZACAO — preview leve (ColorMatrix apenas).
     * O efeito real com convolução só entra ao salvar.
     */
    private fun matrizVetorizacaoPreview(v: Float): ColorMatrix {
        // v vai de -1 a 1
        val contraste = 1f + (v * 0.25f) 
        val t = (1f - contraste) * 128f
        val saturacao = 1f + (v * 0.15f) 
        val mContraste = ColorMatrix(floatArrayOf(
            contraste, 0f, 0f, 0f, t,
            0f, contraste, 0f, 0f, t,
            0f, 0f, contraste, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        val mSat = ColorMatrix().apply { setSaturation(saturacao) }
        mContraste.postConcat(mSat)
        return mContraste
    }

    /**
     * Acabamento aplicado APÓS a convolução da vetorização.
     * – Saturação sutil (+8..+15%).
     * – Contraste sutil (+5..+12%).
     * Mantém o realismo.
     */

    // ─────────────────────────────────────────────────────────────────────
    //  5. UTILITÁRIOS DE PROCESSAMENTO
    // ─────────────────────────────────────────────────────────────────────

    /** Aplica um ColorMatrix "queimando-o" no bitmap resultante. */
    private fun aplicarColorMatrixNoBitmap(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Blur gaussiano via RenderScript (rápido em GPU/CPU).
     *  – RenderScript está deprecated no Android 12+ mas ainda funciona
     *    e é a forma mais leve de conseguir um blur real.
     *  – Para API 31+ com suporte, o ideal seria RenderEffect, mas ele
     *    só se aplica em Views, não em Bitmaps.
     */
    @Suppress("DEPRECATION")
    private fun aplicarBlurRenderScript(src: Bitmap, raio: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val rs = RenderScript.create(null /* usa context estático interno */)
            ?: return aplicarBlurConvolucao(src)
        try {
            val allocIn = Allocation.createFromBitmap(rs, src)
            val allocOut = Allocation.createFromBitmap(rs, out)
            val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(raio.coerceIn(0.5f, 25f))
            blur.setInput(allocIn)
            blur.forEach(allocOut)
            allocOut.copyTo(out)
            allocIn.destroy(); allocOut.destroy(); blur.destroy()
        } finally {
            rs.destroy()
        }
        return out
    }

    /**
     * Fallback: convolução 3×3 gaussiana via ScriptIntrinsicConvolve3x3.
     *  – Usado apenas se RenderScript.create() falhar em algum device.
     */
    @Suppress("DEPRECATION")
    private fun aplicarBlurConvolucao(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val rs = try { RenderScript.create(null) } catch (e: Exception) { null }
        if (rs == null) return src.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val allocIn = Allocation.createFromBitmap(rs, src)
            val allocOut = Allocation.createFromBitmap(rs, out)
            val conv = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs))
            // Kernel gaussiano 3x3 normalizado
            conv.setCoefficients(floatArrayOf(
                1f/16f, 2f/16f, 1f/16f,
                2f/16f, 4f/16f, 2f/16f,
                1f/16f, 2f/16f, 1f/16f
            ))
            conv.setInput(allocIn)
            conv.forEach(allocOut)
            allocOut.copyTo(out)
            allocIn.destroy(); allocOut.destroy(); conv.destroy()
        } finally {
            rs.destroy()
        }
        return out
    }
}