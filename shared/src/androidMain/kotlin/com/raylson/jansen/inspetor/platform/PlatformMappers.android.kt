package com.raylson.jansen.inspetor.platform

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.raylson.jansen.inspetor.ConfiguracoesActivity
import com.raylson.jansen.inspetor.ImageHelper
import com.raylson.jansen.inspetor.domain.ItemHm
import com.raylson.jansen.inspetor.domain.LagoNA
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════════════════
 * PlatformMappers.android.kt (androidMain)
 *
 * Implementação real de cada `expect` do commonMain. O motor de baixo
 * nível (drawOverlayKV/drawIconSvg) NÃO foi reescrito — `ImageHelper.kt`
 * original continua intocado, só é chamado daqui (Regra de Ouro).
 *
 * O `OverlayRenderer` abaixo É um port linha a linha de:
 *   gerarParBitmapRegistro / gerarParBitmapRegistroEmpilhado /
 *   gerarParBitmapRegistroSimplesStatus / gerarOverlayNA /
 *   gerarParImagemFinalNA / gerarOverlayLivre / gerarParImagemFinalLivre
 * (DashboardActivity.kt original), adaptado para receber por parâmetro
 * o que antes vinha de campos da Activity (itensAtuais, estacaoSelecionada,
 * statusBomba, foraDeNA).
 * ═══════════════════════════════════════════════════════════════════
 */

// ── vibrateStrong ────────────────────────────────────────────────────

actual fun vibrateStrong() {
    val context = AndroidContextHolder.appContext
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(60)
    }
}

// ── bytesToImageBitmap ───────────────────────────────────────────────

actual fun bytesToImageBitmap(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// ── decodeImageSafely ────────────────────────────────────────────────
// Mesma lógica de `ImageHelper.carregarComExif`, adaptada de "arquivo em
// disco" para "bytes em memória".

actual fun decodeImageSafely(rawBytes: ByteArray, maxSide: Int): ByteArray? {
    return try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0) return null

        var sample = 1
        while (boundsOpts.outWidth / sample > maxSide || boundsOpts.outHeight / sample > maxSide) {
            sample *= 2
        }

        val decOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        var bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decOpts) ?: return null

        val orientacao = try {
            ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientacao) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
        }
        if (!m.isIdentity) {
            val rot = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rot != bmp) { bmp.recycle(); bmp = rot }
        }

        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            bmp.recycle()
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    }
}

// ── pickImageFromGallery ─────────────────────────────────────────────

actual fun pickImageFromGallery(onResult: (ByteArray?) -> Unit) {
    AndroidActivityBridge.launchGalleryPicker(onResult)
}

// ── shareImage ───────────────────────────────────────────────────────
// ⚠️ REQUER FileProvider configurado no AndroidManifest.xml (ver
// comentário completo na versão anterior deste arquivo / mensagem
// anterior da conversa).

actual fun shareImage(bytes: ByteArray, label: String) {
    val context = AndroidContextHolder.appContext
    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val file = File(dir, "registro_${System.currentTimeMillis()}.jpg")
    file.writeBytes(bytes)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, label).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

// ── OverlayRenderer ──────────────────────────────────────────────────

actual object OverlayRenderer {

    // ═══════════════════════════════════════════════════════════════
    //  Helpers privados — port 1:1 de scaleCrop / drawPlaceholder /
    //  calcularDimensoesNA / calcularAlturasEmpilhadas /
    //  calcularDimensoesLivre / corStatusLivre / montarTituloOverlay,
    //  originalmente métodos privados da DashboardActivity.
    // ═══════════════════════════════════════════════════════════════

    private fun scaleCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (src.width * scale).toInt()
        val sh = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val x = ((sw - w) / 2).coerceAtLeast(0)
        val y = ((sh - h) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(scaled, x, y, w.coerceAtMost(sw), h.coerceAtMost(sh))
    }

    private fun drawPlaceholder(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111318") }
        c.drawRect(x, y, x + w, y + h, p)
        p.color = Color.parseColor("#5A6478")
        p.typeface = Typeface.MONOSPACE
        p.textSize = w * 0.035f
        c.drawText("Foto não registrada", x + w * 0.05f, y + h / 2, p)
    }

    private fun calcularDimensoesNA(foto: Bitmap): Pair<Int, Int> {
        val isLandscape = foto.width > foto.height
        return if (isLandscape) {
            val w = 2880
            w to (w.toFloat() * foto.height / foto.width).toInt()
        } else {
            val w = 2160
            w to (w.toFloat() * foto.height / foto.width).toInt().coerceIn(2160, 4320)
        }
    }

    private fun calcularDimensoesLivre(foto: Bitmap): Pair<Int, Int> {
        val isLandscape = foto.width > foto.height
        return if (isLandscape) {
            val w = 2880
            w to (w.toFloat() * foto.height / foto.width).toInt()
        } else {
            val w = 2160
            w to (w.toFloat() * foto.height / foto.width).toInt().coerceIn(2160, 4320)
        }
    }

    private fun calcularAlturasEmpilhadas(fotoSup: Bitmap?, fotoInf: Bitmap?, canvasW: Int): Pair<Int, Int> {
        val context = AndroidContextHolder.appContext
        if (ImageHelper.lerProporcao(context) == ConfiguracoesActivity.PROP_FULL) {
            fun alturaPorFoto(foto: Bitmap?): Int {
                val f = foto ?: return (canvasW * 5 / 4) / 2
                val ratio = f.width.toFloat() / f.height.toFloat()
                return (canvasW / ratio).toInt().coerceIn(300, 4320)
            }
            return alturaPorFoto(fotoSup) to alturaPorFoto(fotoInf)
        }
        val meia = when (ImageHelper.lerProporcao(context)) {
            ConfiguracoesActivity.PROP_4x5 -> (canvasW * 5) / 4 / 2
            ConfiguracoesActivity.PROP_3x4 -> (canvasW * 4) / 3 / 2
            ConfiguracoesActivity.PROP_9x16 -> (canvasW * 16) / 9 / 2
            ConfiguracoesActivity.PROP_1x1 -> canvasW / 2
            else -> canvasW / 2
        }
        return meia to meia
    }

    private fun corStatusLivre(status: String): String = when (status) {
        "LIGADO", "LIGADA", "COM VAZÃO" -> "#00e676"
        "ZERADO" -> "#F59E0B"
        "NENHUM" -> "#000000"
        else -> "#ff3b3b"
    }

    private fun corStatus(statusBomba: String): String = when (statusBomba) {
        "LIGADO", "LIGADA", "COM VAZÃO" -> "#00e676"
        "ZERADO" -> "#F59E0B"
        "NENHUM" -> "#000000"
        else -> "#ff3b3b"
    }

    private fun montarTituloOverlay(item: ItemHm, estacaoNome: String): String {
        return when (estacaoNome) {
            "DET-01" -> "HIDRÔMETRO-${item.id.padStart(2, '0')}"
            "ARB-05" -> "FLOWMETER ARB-05"
            "ARB-06" -> "FLOWMETER ARB-06"
            "ARB-07" -> "FLOWMETER ARB-07 ${item.id}"
            "ARB-08" -> "FLOWMETER ARB-08 ${item.id}"
            "ARB-09" -> "FLOWMETER ARB-${item.id}"
            else -> item.cardAzulLabel
        }
    }

    private fun deveGerarFlowmeterHibridoComoSimples(item: ItemHm): Boolean =
        item.tipo == "HM_VAZAO" && item.fotoSup != null && item.fotoInf == null

    private fun Bitmap.toJpegBytes(): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.toByteArray()
        }

    private fun decode(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun horaAutomatica(): String {
        val now = Date()
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("HH:mm'h'", Locale.getDefault()).format(now)
        return "$dateStr // $timeStr"
    }

    // ═══════════════════════════════════════════════════════════════
    //  N.A. — port de gerarOverlayNA + gerarParImagemFinalNA
    // ═══════════════════════════════════════════════════════════════

    private fun gerarOverlayNA(
        lago: LagoNA,
        valorNA: String?,
        horaOverride: String,
        w: Int,
        h: Int,
        foraDeNA: Boolean,
        isPreview: Boolean = false,
        isGavetaFechada: Boolean = false
    ): Pair<Bitmap, Float> {
        val isExtravasor = lago.abreviacao.equals("DT2-ex", ignoreCase = true) ||
            lago.abreviacao.equals("CP-ex", ignoreCase = true)
        val overlayData = if (isExtravasor) {
            listOf("pin" to lago.nomeCard, "relogio" to horaOverride)
        } else {
            val naStr = when {
                foraDeNA -> "N.A: Fora do nível da régua."
                valorNA.isNullOrBlank() -> "N.A: "
                else -> "N.A: ${valorNA.trimEnd('m')}m"
            }
            listOf("pin" to lago.nomeCard, "relogio" to horaOverride, "hidro" to naStr)
        }
        val bmpOverlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val alturaBarra = ImageHelper.drawOverlayKV(
            Canvas(bmpOverlay), 0f, h.toFloat(), w.toFloat(), overlayData,
            deslocarDireita = isPreview, isGavetaFechada = isGavetaFechada
        )
        return bmpOverlay to alturaBarra
    }

    actual fun gerarRegistroNA(lago: LagoNA, valorNA: String?, horaOverride: String, foraDeNA: Boolean): RegistroGerado {
        val fotoBytes = lago.fotoRegua ?: return RegistroGerado(ByteArray(0), ByteArray(0), ByteArray(0))
        val foto = decode(fotoBytes)
        val (W, H) = calcularDimensoesNA(foto)

        val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
        val sc = scaleCrop(foto, W, H)
        cl.drawBitmap(sc, 0f, 0f, null)
        if (sc != foto) sc.recycle()

        val (bmpOverlay, _) = gerarOverlayNA(lago, valorNA, horaOverride, W, H, foraDeNA, isPreview = false, isGavetaFechada = false)

        val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)

        return RegistroGerado(bmpLimpo.toJpegBytes(), bmpOverlay.toJpegBytes(), bmpFinal.toJpegBytes())
    }

    // ═══════════════════════════════════════════════════════════════
    //  HM — port de gerarParBitmapRegistro (dispatcher) +
    //  gerarParBitmapRegistroSimplesStatus + gerarParBitmapRegistroEmpilhado
    // ═══════════════════════════════════════════════════════════════

    actual fun gerarRegistroHm(
        item: ItemHm,
        itensDaEstacao: List<ItemHm>,
        estacaoNome: String,
        statusBomba: String,
        horaOverride: String?
    ): RegistroGerado {
        if (deveGerarFlowmeterHibridoComoSimples(item)) {
            return gerarParBitmapRegistroSimplesStatus(item, estacaoNome, statusBomba, horaOverride)
        }
        return gerarParBitmapRegistroEmpilhado(item, itensDaEstacao, estacaoNome, statusBomba, horaOverride)
    }

    private fun gerarParBitmapRegistroSimplesStatus(
        item: ItemHm,
        estacaoNome: String,
        statusBomba: String,
        horaOverride: String?
    ): RegistroGerado {
        val fotoSupBytes = item.fotoSup
        val fotoSup = fotoSupBytes?.let { decode(it) }
        val isLandscape = fotoSup?.let { it.width > it.height } ?: false
        val W = if (isLandscape) 2880 else 2160

        val statusCor = corStatus(statusBomba)
        val context = AndroidContextHolder.appContext
        val bmpFoto = fotoSup ?: Bitmap.createBitmap(W, 100, Bitmap.Config.ARGB_8888)
        val proporcao = ImageHelper.lerProporcao(context)

        val alturaFinal = if (proporcao == ConfiguracoesActivity.PROP_FULL) {
            val ratio = bmpFoto.width.toFloat() / bmpFoto.height.toFloat()
            (W / ratio).toInt().coerceIn(600, 4320)
        } else {
            when (proporcao) {
                ConfiguracoesActivity.PROP_4x5 -> (W * 5) / 4
                ConfiguracoesActivity.PROP_3x4 -> (W * 4) / 3
                ConfiguracoesActivity.PROP_9x16 -> (W * 16) / 9
                ConfiguracoesActivity.PROP_1x1 -> W
                else -> (W * 5) / 4
            }
        }

        val bmpLimpo = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
        val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
        val sc = scaleCrop(bmpFoto, W, alturaFinal)
        cl.drawBitmap(sc, 0f, 0f, null)
        if (sc != bmpFoto) sc.recycle()

        val horaUsar = horaOverride ?: item.dataHoraSup.ifEmpty { horaAutomatica() }
        val labelHidro = montarTituloOverlay(item, estacaoNome)
        val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
            "$statusBomba // VAZ: ${item.leituraManual}"
        else
            statusBomba

        val bmpOverlay = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
        ImageHelper.drawOverlayKV(
            Canvas(bmpOverlay), 0f, alturaFinal.toFloat(), W.toFloat(),
            listOf("hidro" to labelHidro, "relogio" to horaUsar, "status" to statusComLeitura),
            mapOf("status" to statusCor)
        )

        val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)

        return RegistroGerado(bmpLimpo.toJpegBytes(), bmpOverlay.toJpegBytes(), bmpFinal.toJpegBytes())
    }

    private fun gerarParBitmapRegistroEmpilhado(
        item: ItemHm,
        itensDaEstacao: List<ItemHm>,
        estacaoNome: String,
        statusBomba: String,
        horaOverride: String?
    ): RegistroGerado {
        val fotoSupItemBytes = if (item.tipo == "SIFAO") itensDaEstacao.find { it.id == "SIF-SUP" }?.fotoSup else item.fotoSup
        val fotoPrincipal = fotoSupItemBytes?.let { decode(it) }
        val isLandscape = fotoPrincipal?.let { it.width > it.height } ?: false
        val W = if (isLandscape) 2880 else 2160

        val horaFinal = horaOverride ?: horaAutomatica()
        val statusCor = corStatus(statusBomba)

        fun finalizar(bmpLimpo: Bitmap, desenharOverlay: (Canvas) -> Unit): RegistroGerado {
            val bmpOverlay = Bitmap.createBitmap(bmpLimpo.width, bmpLimpo.height, Bitmap.Config.ARGB_8888)
            desenharOverlay(Canvas(bmpOverlay))
            val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)
            return RegistroGerado(bmpLimpo.toJpegBytes(), bmpOverlay.toJpegBytes(), bmpFinal.toJpegBytes())
        }

        return when (item.tipo) {
            "HM" -> {
                val fotoSup = item.fotoSup?.let { decode(it) }
                val fotoInf = item.fotoInf?.let { decode(it) }
                val (photoHsup, photoHinf) = calcularAlturasEmpilhadas(fotoSup, fotoInf, W)
                val H = photoHsup + photoHinf
                val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val temSup = fotoSup?.let {
                    val sc = scaleCrop(it, W, photoHsup)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, 0f, W.toFloat(), photoHsup.toFloat()); false }
                Paint().apply { color = Color.parseColor("#00d4ff") }.also { cl.drawRect(0f, photoHsup - 2f, W.toFloat(), photoHsup + 2f, it) }
                val temInf = fotoInf?.let {
                    val sc = scaleCrop(it, W, photoHinf)
                    cl.drawBitmap(sc, 0f, photoHsup.toFloat(), null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, photoHsup.toFloat(), W.toFloat(), photoHinf.toFloat()); false }

                val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
                    "$statusBomba // VAZ: ${item.leituraManual}"
                else
                    statusBomba

                finalizar(bmpLimpo) { c ->
                    if (temSup) ImageHelper.drawOverlayKV(c, 0f, photoHsup.toFloat(), W.toFloat(), listOf("pin" to estacaoNome, "hidro" to montarTituloOverlay(item, estacaoNome)))
                    if (temInf) ImageHelper.drawOverlayKV(c, 0f, H.toFloat(), W.toFloat(), listOf("raio" to "BOMBA-${item.id.padStart(2, '0')}", "relogio" to horaFinal, "status" to statusComLeitura), mapOf("status" to statusCor))
                }
            }
            "HM_VAZAO" -> {
                if (deveGerarFlowmeterHibridoComoSimples(item)) {
                    return gerarParBitmapRegistroSimplesStatus(item, estacaoNome, statusBomba, horaOverride)
                }
                val fotoSup = item.fotoSup?.let { decode(it) }
                val fotoInf = item.fotoInf?.let { decode(it) }
                val (photoHsup, photoHinf) = calcularAlturasEmpilhadas(fotoSup, fotoInf, W)
                val H = photoHsup + photoHinf
                val corFlowmeter = when (statusBomba) { "LIGADO" -> "#00e676"; "ZERADO" -> "#F59E0B"; "NENHUM" -> "#000000"; else -> "#ff3b3b" }
                val corVazao = if (item.statusVazao == "COM VAZÃO") "#00e676" else "#ff3b3b"
                val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val temSup = fotoSup?.let {
                    val sc = scaleCrop(it, W, photoHsup)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, 0f, W.toFloat(), photoHsup.toFloat()); false }
                Paint().apply { color = Color.parseColor("#00d4ff") }.also { cl.drawRect(0f, photoHsup - 2f, W.toFloat(), photoHsup + 2f, it) }
                val temInf = fotoInf?.let {
                    val sc = scaleCrop(it, W, photoHinf)
                    cl.drawBitmap(sc, 0f, photoHsup.toFloat(), null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, photoHsup.toFloat(), W.toFloat(), photoHinf.toFloat()); false }

                val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
                    "$statusBomba // VAZ: ${item.leituraManual}"
                else
                    statusBomba

                finalizar(bmpLimpo) { c ->
                    if (temSup) ImageHelper.drawOverlayKV(c, 0f, photoHsup.toFloat(), W.toFloat(), listOf("hidro" to montarTituloOverlay(item, estacaoNome), "relogio" to horaFinal, "status" to statusComLeitura), mapOf("status" to corFlowmeter))
                    if (temInf) ImageHelper.drawOverlayKV(c, 0f, H.toFloat(), W.toFloat(), listOf("hidro" to item.id, "status" to item.statusVazao), mapOf("status" to corVazao))
                }
            }
            "SIMPLES_STATUS", "SIMPLES_STATUS_ADD" -> {
                val bmpFoto = item.fotoSup?.let { decode(it) } ?: Bitmap.createBitmap(W, 100, Bitmap.Config.ARGB_8888)
                val ratio = bmpFoto.width.toFloat() / bmpFoto.height.toFloat()
                val alturaFinal = (W / ratio).toInt().coerceAtLeast(1200)
                val bmpLimpo = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val sc = scaleCrop(bmpFoto, W, alturaFinal)
                cl.drawBitmap(sc, 0f, 0f, null)
                if (sc != bmpFoto) sc.recycle()
                val horaUsar = horaOverride ?: item.dataHoraSup.ifEmpty { horaFinal }
                val labelHidro = montarTituloOverlay(item, estacaoNome)

                val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
                    "$statusBomba // VAZ: ${item.leituraManual}"
                else
                    statusBomba

                finalizar(bmpLimpo) { c ->
                    ImageHelper.drawOverlayKV(c, 0f, alturaFinal.toFloat(), W.toFloat(), listOf("hidro" to labelHidro, "relogio" to horaUsar, "status" to statusComLeitura), mapOf("status" to statusCor))
                }
            }
            "SIFAO" -> {
                val supItem = itensDaEstacao.find { it.id == "SIF-SUP" }
                val infItem = itensDaEstacao.find { it.id == "SIF-INF" }
                val supFoto = supItem?.fotoSup?.let { decode(it) }
                val infFoto = infItem?.fotoSup?.let { decode(it) }
                val (photoHsup, photoHinf) = calcularAlturasEmpilhadas(supFoto, infFoto, W)
                val H = photoHsup + photoHinf
                val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                if (supFoto != null) {
                    val sc = scaleCrop(supFoto, W, photoHsup)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != supFoto) sc.recycle()
                } else {
                    drawPlaceholder(cl, 0f, 0f, W.toFloat(), photoHsup.toFloat())
                }
                Paint().apply { color = Color.parseColor("#00d4ff") }.also { cl.drawRect(0f, photoHsup - 2f, W.toFloat(), photoHsup + 2f, it) }
                if (infFoto != null) {
                    val sc = scaleCrop(infFoto, W, photoHinf)
                    cl.drawBitmap(sc, 0f, photoHsup.toFloat(), null)
                    if (sc != infFoto) sc.recycle()
                } else {
                    drawPlaceholder(cl, 0f, photoHsup.toFloat(), W.toFloat(), photoHinf.toFloat())
                }
                finalizar(bmpLimpo) { c ->
                    if (infItem != null && infFoto != null) {
                        val horaInf = horaOverride ?: infItem.dataHoraSup.ifEmpty { horaFinal }
                        val corStatusInf = if (infItem.statusVazao == "COM VAZÃO") "#00e676" else "#ff3b3b"
                        ImageHelper.drawOverlayKV(
                            c, 0f, H.toFloat(), W.toFloat(),
                            listOf("pin" to estacaoNome, "hidro" to "SIFÕES", "relogio" to horaInf, "status" to infItem.statusVazao),
                            mapOf("status" to corStatusInf)
                        )
                    }
                }
            }
            "SIMPLES" -> {
                val bmpFoto = item.fotoSup?.let { decode(it) } ?: Bitmap.createBitmap(W, 2160, Bitmap.Config.ARGB_8888)
                val imgRatio = bmpFoto.width.toFloat() / bmpFoto.height.toFloat()
                val alturaFinal = (W / imgRatio).toInt().coerceIn(1200, 4320)

                val bmpLimpo = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val temFoto = item.fotoSup?.let {
                    val sc = scaleCrop(bmpFoto, W, alturaFinal)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != bmpFoto) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, 0f, W.toFloat(), alturaFinal.toFloat()); false }

                finalizar(bmpLimpo) { c ->
                    if (temFoto) ImageHelper.drawOverlayKV(c, 0f, alturaFinal.toFloat(), W.toFloat(), listOf("pin" to estacaoNome, "hidro" to item.cardAzulLabel, "relogio" to horaFinal))
                }
            }
            else -> {
                val empty = Bitmap.createBitmap(W, 100, Bitmap.Config.ARGB_8888)
                RegistroGerado(empty.toJpegBytes(), empty.toJpegBytes(), empty.toJpegBytes())
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  LIVRE — port de gerarOverlayLivre + gerarParImagemFinalLivre
    // ═══════════════════════════════════════════════════════════════

    private fun gerarOverlayLivre(
        texto: String,
        incluirDataHora: Boolean,
        horaOverride: String,
        statusOverlay: String?,
        w: Int,
        h: Int,
        isPreview: Boolean = false,
        isGavetaFechada: Boolean = false
    ): Pair<Bitmap, Float> {
        val linhas = mutableListOf<Pair<String, String>>()
        val cores = mutableMapOf<String, String>()

        if (incluirDataHora) {
            linhas.add("relogio" to horaOverride)
        }

        val textoNorm = texto.trim()
        if (textoNorm.isNotEmpty()) {
            textoNorm.split('\n').forEach { linha ->
                linhas.add("__cont__" to linha.trimEnd())
            }
        }

        if (!statusOverlay.isNullOrBlank()) {
            linhas.add("status" to statusOverlay)
            cores["status"] = corStatusLivre(statusOverlay)
        }

        if (linhas.isEmpty()) {
            linhas.add("__cont__" to " ")
        }

        val bmpOverlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val alturaBarra = ImageHelper.drawOverlayKV(
            Canvas(bmpOverlay), 0f, h.toFloat(), w.toFloat(), linhas, cores,
            deslocarDireita = isPreview, isGavetaFechada = isGavetaFechada
        )
        return bmpOverlay to alturaBarra
    }

    actual fun gerarRegistroLivre(item: ItemHm, statusBomba: String, horaOverride: String?): RegistroGerado {
        val fotoBytes = item.fotoSup ?: return RegistroGerado(ByteArray(0), ByteArray(0), ByteArray(0))
        val foto = decode(fotoBytes)
        val (W, H) = calcularDimensoesLivre(foto)

        val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
        val sc = scaleCrop(foto, W, H)
        cl.drawBitmap(sc, 0f, 0f, null)
        if (sc != foto) sc.recycle()

        val horaFinal = horaOverride ?: horaAutomatica()
        // "NENHUM" segue o mesmo critério do statusBomba original: sem status desenhado.
        val statusOverlay = if (statusBomba == "NENHUM") null else statusBomba

        val (bmpOverlay, _) = gerarOverlayLivre(
            texto = item.textoLivre,
            incluirDataHora = item.incluirDataHoraLivre,
            horaOverride = horaFinal,
            statusOverlay = statusOverlay,
            w = W, h = H,
            isPreview = false, isGavetaFechada = false
        )

        val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)

        return RegistroGerado(bmpLimpo.toJpegBytes(), bmpOverlay.toJpegBytes(), bmpFinal.toJpegBytes())
    }
}
