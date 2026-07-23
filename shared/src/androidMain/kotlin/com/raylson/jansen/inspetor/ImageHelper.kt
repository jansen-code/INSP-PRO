package com.raylson.jansen.inspetor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.PathParser
import androidx.exifinterface.media.ExifInterface
import android.graphics.Path


object ImageHelper {

    private const val TAG = "ImageHelper"

    // ═══ FONTE FIXA GRAVADA NA FOTO (Roboto Mono) ═══
    // Antes, a tarja da foto (LAGOA X, data/hora, N.A) usava
    // Typeface.MONOSPACE — um "apelido" genérico do Android que ELE
    // decide pra qual fonte de verdade aponta. Na maioria dos aparelhos
    // isso já cai em Roboto Mono, mas em alguns (principalmente com
    // "trocador de fonte do sistema" tipo Samsung/Xiaomi) esse apelido
    // pode ser substituído também. Chamando `definirFontePersonalizada()`
    // uma vez (no onCreate do app) com a fonte embutida de verdade, TODA
    // tarja gravada em foto passa a usar exatamente esse arquivo — sem
    // depender de nenhum apelido do sistema. Se ninguém chamar essa
    // função, cai automaticamente no Typeface.MONOSPACE de sempre (nada
    // quebra caso a fonte ainda não tenha sido adicionada ao projeto).
    private var fontePersonalizada: Typeface? = null

    fun definirFontePersonalizada(typeface: Typeface) {
        fontePersonalizada = typeface
    }

    private fun fonteTarja(): Typeface =
        fontePersonalizada ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    // ── 1. Carrega bitmap de arquivo corrigindo rotação EXIF ─────────────────

        fun carregarComExif(filePath: String): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, boundsOpts)
            if (boundsOpts.outWidth <= 0) return null

            // ═══ MUDOU DE 1920 PARA 4000 ═══
            val maxSide = 4000
            var sample  = 1
            while (boundsOpts.outWidth  / sample > maxSide ||
                   boundsOpts.outHeight / sample > maxSide) sample *= 2
                   

            val decOpts = BitmapFactory.Options().apply {
                inSampleSize      = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable         = true
            }
            var bmp = BitmapFactory.decodeFile(filePath, decOpts) ?: return null

            // Corrige rotação EXIF (essencial no Samsung A15)
            val exif = ExifInterface(filePath)
            val ori  = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val m = Matrix()
            when (ori) {
                ExifInterface.ORIENTATION_ROTATE_90      -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180     -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270     -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
            }
            if (!m.isIdentity) {
                val rot = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rot != bmp) { bmp.recycle(); bmp = rot }
            }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "carregarComExif: ${e.message}")
            null
        }
    }

    // ── 2. Recorta bitmap para a proporção escolhida ──────────────────────────
    //
    //  A CameraCaptureActivity chama esta função diretamente quando
    //  EXTRA_APLICAR_CORTE == true. O DashboardActivity NÃO precisa
    //  chamar novamente — a foto já chega cortada.

    fun recortarPorProporcao(bitmap: Bitmap, proporcao: String): Bitmap {
        if (proporcao == "full") return bitmap

        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        if (srcW <= 0f || srcH <= 0f) return bitmap

        // ── Calcula as dimensões alvo de acordo com a proporção ───────────────
        // Para proporções "retrato" (largura < altura) usamos a largura como
        // âncora e derivamos a altura. Para proporções quadradas (1:1) qualquer
        // âncora serve. A lógica de centralização e clipping é idêntica para
        // todos os casos.

        val dstW: Int
        val dstH: Int

        when (proporcao) {
            // ← NOVO: 4:5  →  altura = largura * 5/4
            "4:5" -> {
                val targetRatio = 4f / 5f   // largura/altura
                if (srcW / srcH > targetRatio) {
                    // foto mais larga → corta as laterais
                    dstH = srcH.toInt()
                    dstW = (srcH * targetRatio).toInt().coerceAtLeast(1)
                } else {
                    // foto mais alta → corta cima/baixo
                    dstW = srcW.toInt()
                    dstH = (srcW / targetRatio).toInt().coerceAtLeast(1)
                }
            }
            "3:4" -> {
                val targetRatio = 3f / 4f
                if (srcW / srcH > targetRatio) {
                    dstH = srcH.toInt()
                    dstW = (srcH * targetRatio).toInt().coerceAtLeast(1)
                } else {
                    dstW = srcW.toInt()
                    dstH = (srcW / targetRatio).toInt().coerceAtLeast(1)
                }
            }
            "9:16" -> {
                val targetRatio = 9f / 16f
                if (srcW / srcH > targetRatio) {
                    dstH = srcH.toInt()
                    dstW = (srcH * targetRatio).toInt().coerceAtLeast(1)
                } else {
                    dstW = srcW.toInt()
                    dstH = (srcW / targetRatio).toInt().coerceAtLeast(1)
                }
            }
            "1:1" -> {
                val lado = minOf(srcW, srcH).toInt().coerceAtLeast(1)
                dstW = lado; dstH = lado
            }
            else -> return bitmap
        }

        val x = ((srcW - dstW) / 2f).toInt().coerceAtLeast(0)
        val y = ((srcH - dstH) / 2f).toInt().coerceAtLeast(0)

        val safeW = dstW.coerceAtMost(bitmap.width  - x)
        val safeH = dstH.coerceAtMost(bitmap.height - y)

        return try {
            val cropped = Bitmap.createBitmap(bitmap, x, y, safeW, safeH)
            if (cropped != bitmap) bitmap.recycle()
            cropped
        } catch (e: Exception) {
            bitmap
        }
    }

    // ── 3. Aplica no ImageView ajustando a altura para a proporção ────────────

    fun aplicarNoImageView(bitmap: Bitmap, imageView: ImageView, larguraCardPx: Int) {
        val lp = imageView.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (larguraCardPx > 0) {
            val alturaCalculada = (larguraCardPx.toFloat() * bitmap.height / bitmap.width)
                .toInt().coerceAtLeast(1)
            lp.height = alturaCalculada
            lp.width  = ViewGroup.LayoutParams.MATCH_PARENT
            imageView.layoutParams = lp
        }
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageBitmap(bitmap)
        imageView.visibility = View.VISIBLE
    }

    // ── 4. Lê a proporção salva nas prefs ────────────────────────────────────
    //  Default agora é PROP_4x5 (proporção padrão do aplicativo)

    fun lerProporcao(context: Context): String {
        val prefs = SecurePrefs.get(
            context,
            "Configuracoes"
        )
        return prefs.getString(
            "pref_proporcao",
            "4:5"   // ← default atualizado
        ) ?: "4:5"
    }

    // ── 5. Esta estação usa a proporção configurada? ──────────────────────────
    //  ARB-05, ARB-06, ARB-07 e N.A. → sim.  DET-01, ARB-08, ARB-09 → full.

    fun estacaoUsaProporcaoConfig(nomeEstacao: String): Boolean {
        val u = nomeEstacao.uppercase()
        return u == "ARB-05" || u == "ARB-06" || u == "ARB-07" ||
               u == "N.A."  || u == "NA"
    }

    // ── 6. Overlay (tarja) com ícones e texto ─────────────────────────────────
    //  Fonte da verdade: visual original do DashboardActivity (LinearGradient
    //  transparente + faixa Color.argb(133, 0, 0, 0)). NÃO ALTERAR.

    fun drawOverlayKV(c: Canvas, x: Float, bottomY: Float, w: Float, data: List<Pair<String, String>>, colors: Map<String, String> = emptyMap(), deslocarDireita: Boolean = false, isGavetaFechada: Boolean = false): Float {
        // ══ CORREÇÃO AQUI: Usa APENAS a largura (w) para o cálculo. 
        // Isso garante que a tarja superior e inferior tenham exatamente o mesmo tamanho de texto! ══
        val fs   = w * 0.037f
        val padV = w * 0.018f
        val padH = if (deslocarDireita) w * 0.08f else w * 0.04f 
        
        val lh   = fs * 1.28f
        val h    = data.size * lh + padV * 2
        val top  = bottomY - h
        
        // Diminuímos de 0.42f para 0.20f (20% de escurecimento apenas)
        val grad = LinearGradient(x, top - h * 0.5f, x, bottomY, intArrayOf(Color.TRANSPARENT, Color.argb((0.20f * 255).toInt(), 0, 0, 0)), null, Shader.TileMode.CLAMP)
        c.drawRect(x, top - h * 0.5f, x + w, bottomY, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad })

        // Diminuímos de 82 para 40 (Transparência bem mais leve)
        // ═══ Texto "um pouquinho mais grosso": além do preenchimento
        // normal, desenha um traço fino por cima do próprio texto (mesma
        // cor) — engrossa levemente as bordas das letras sem precisar
        // trocar de arquivo de fonte. O valor 0.02f é sutil de propósito;
        // pra engrossar mais, é só aumentar esse número (ex: 0.035f). ═══
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 0, 0, 0)
            typeface = fonteTarja()
            textSize = fs
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = fs * 0.03f
        }
        c.drawRect(x, top, x + w, bottomY, p)
        
        // ══ DESENHO DA GAVETA (ABINHA) NO CANTO DIREITO ══
        if (deslocarDireita) {
            val abaLargura = w * 0.144f
            val abaAltura = w * 0.055f
            val abaRaioCurva = w * 0.027f
            val abaRight = w - padH
            val abaLeft = abaRight - abaLargura
            val abaBottom = top
            val abaTop = abaBottom - abaAltura

            val paintGaveta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#99000000")
                style = Paint.Style.FILL
            }

            val abaPath = Path().apply {
                moveTo(abaLeft, abaBottom)
                lineTo(abaLeft, abaTop + abaRaioCurva)
                quadTo(abaLeft, abaTop, abaLeft + abaRaioCurva, abaTop)
                lineTo(abaRight - abaRaioCurva, abaTop)
                quadTo(abaRight, abaTop, abaRight, abaTop + abaRaioCurva)
                lineTo(abaRight, abaBottom)
                close()
            }
            c.drawPath(abaPath, paintGaveta)

            // ══ DESENHO DA SETINHA ══
            val setaCx = abaLeft + abaLargura / 2f
            val setaCy = abaTop + abaAltura / 2f + (w * 0.005f)
            val tamanhoSeta = w * 0.016f

            val paintSeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = w * 0.008f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                setShadowLayer(w * 0.005f, 0f, w * 0.002f, Color.BLACK)
            }

            val pathSeta = Path()
            if (isGavetaFechada) { // Setinha pra CIMA ^
                pathSeta.moveTo(setaCx - tamanhoSeta, setaCy + tamanhoSeta / 2)
                pathSeta.lineTo(setaCx, setaCy - tamanhoSeta / 2)
                pathSeta.moveTo(setaCx, setaCy - tamanhoSeta / 2)
                pathSeta.lineTo(setaCx + tamanhoSeta, setaCy + tamanhoSeta / 2)
            } else { // Setinha pra BAIXO v
                pathSeta.moveTo(setaCx - tamanhoSeta, setaCy - tamanhoSeta / 2)
                pathSeta.lineTo(setaCx, setaCy + tamanhoSeta / 2)
                pathSeta.moveTo(setaCx, setaCy + tamanhoSeta / 2)
                pathSeta.lineTo(setaCx + tamanhoSeta, setaCy - tamanhoSeta / 2)
            }
            c.drawPath(pathSeta, paintSeta)
        }

        data.forEachIndexed { i, (icon, txt) ->
            val lineCenter = top + padV + i * lh + lh * 0.5f
            p.color = Color.parseColor(colors[icon] ?: "#e8ecf4")
            val iconSize = fs * 0.92f
            
            drawIconSvg(c, icon, x + padH, lineCenter - iconSize * 0.5f, iconSize, colors[icon] ?: "#e8ecf4")
            c.drawText(txt, x + padH + iconSize * 1.5f, lineCenter + fs * 0.35f, p)
        }
        
        return h 
    }


    fun drawIconSvg(c: Canvas, name: String, x: Float, y: Float, size: Float, color: String) {
        val paths = mapOf(
            "pin"     to "M12 2C8 2 5 5.5 5 9c0 5 7 13 7 13s7-8 7-13c0-3.5-3-7-7-7z M12 12a3 3 0 1 1 0-6 3 3 0 0 1 0 6z",
            "hidro"   to "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M7 12h10 M12 7v5l3 2",
            "raio"    to "M13 2L3 14h7l-1 8 10-12h-7l1-8z",
            "relogio" to "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M12 6v6l4 2",
            "status"  to "M2 12h4l3 8 4-16 3 8h6",
            // ═══ NOVO: alias do mesmo ícone de "status", usado quando uma tarja
            // unificada precisa de DUAS linhas de status com cores diferentes
            // (ex.: status do flowmeter + status da vazão na mesma faixa).
            "status2" to "M2 12h4l3 8 4-16 3 8h6"
        )
        try {
            val path = PathParser.createPathFromPathData(paths[name] ?: return)
            path.transform(Matrix().apply { postScale(size / 24f, size / 24f); postTranslate(x, y) })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = size * 0.09f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = Color.parseColor(color) })
        } catch (_: Exception) {}
    }
}

