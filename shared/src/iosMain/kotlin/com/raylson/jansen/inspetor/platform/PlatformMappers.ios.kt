@file:OptIn(ExperimentalForeignApi::class)

package com.raylson.jansen.inspetor.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.raylson.jansen.inspetor.domain.ItemHm
import com.raylson.jansen.inspetor.domain.LagoNA
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.posix.memcpy
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImageJPEGRepresentation
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerViewController
import kotlin.math.max
import kotlin.math.roundToInt

var rootViewController: UIViewController? = null

actual fun vibrateStrong() {
    val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    generator.prepare()
    generator.impactOccurred()
}

actual fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        val skiaImage = bytes.toSkiaImage() ?: return null
        skiaImage.toComposeImageBitmap()
    } catch (_: Exception) { null }
}

actual fun decodeImageSafely(rawBytes: ByteArray, maxSide: Int): ByteArray? {
    return try {
        val uiImage = rawBytes.toUIImage() ?: return null
        val origW = uiImage.size.useContents { width }
        val origH = uiImage.size.useContents { height }
        if (origW <= 0.0 || origH <= 0.0) return null
        var drawW = origW; var drawH = origH
        val longest = maxOf(origW, origH)
        if (longest > maxSide.toDouble()) {
            val s = maxSide.toDouble() / longest
            drawW = origW * s; drawH = origH * s
        }
        UIGraphicsBeginImageContextWithOptions(platform.CoreGraphics.CGSizeMake(drawW, drawH), true, 1.0)
        uiImage.drawInRect(platform.CoreGraphics.CGRectMake(0.0, 0.0, drawW, drawH))
        val scaled = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext() ?: return null
        val jpegData = UIImageJPEGRepresentation(scaled!!, 0.92) ?: return null
        jpegData.toByteArray()
    } catch (_: Exception) { null }
}

actual fun pickImageFromGallery(onResult: (ByteArray?) -> Unit) {
    val vc = rootViewController ?: run { onResult(null); return }
    val config = PHPickerConfiguration()
    config.selectionLimit = 1
    val picker = PHPickerViewController(configuration = config)
    vc.presentViewController(picker, animated = true, completion = null)
    onResult(null)
}

actual fun shareImage(bytes: ByteArray, label: String) {
    val vc = rootViewController ?: return
    val uiImage = bytes.toUIImage() ?: return
    val activityVC = UIActivityViewController(
        activityItems = listOf(uiImage),
        applicationActivities = null
    )
    vc.presentViewController(activityVC, animated = true, completion = null)
}

// ═════════════════════════════════════════════════════════════════════
//  OverlayRenderer
// ═════════════════════════════════════════════════════════════════════

actual object OverlayRenderer {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun parseHex(hex: String): Int {
        val h = hex.trimStart('#')
        val r = h.substring(0, 2).toInt(16)
        val g = h.substring(2, 4).toInt(16)
        val b = h.substring(4, 6).toInt(16)
        return argb(255, r, g, b)
    }

    private fun horaAutomatica(): String {
        val fmt = NSDateFormatter()
        fmt.dateFormat = "dd.MM.yyyy // HH:mm'h'"
        fmt.locale = NSLocale(localeIdentifier = "pt_BR")
        return fmt.stringFromDate(NSDate())
    }

    private fun fontWithSize(size: Float): Font {
        return Font().makeWithSize(size)
    }

    private fun corStatus(status: String): Int = when (status) {
        "LIGADO", "LIGADA", "COM VAZAO" -> parseHex("#00e676")
        "ZERADO" -> parseHex("#F59E0B")
        "NENHUM" -> 0xFF000000.toInt()
        else -> parseHex("#ff3b3b")
    }

    private fun montarTitulo(item: ItemHm, estacao: String): String = when (estacao) {
        "DET-01" -> "HIDROMETRO-${item.id.padStart(2, '0')}"
        "ARB-05" -> "FLOWMETER ARB-05"
        "ARB-06" -> "FLOWMETER ARB-06"
        "ARB-07" -> "FLOWMETER ARB-07 ${item.id}"
        "ARB-08" -> "FLOWMETER ARB-08 ${item.id}"
        "ARB-09" -> "FLOWMETER ARB-${item.id}"
        else -> item.cardAzulLabel
    }

    private fun decode(bytes: ByteArray): Image? {
        return try { Image.makeFromEncoded(bytes) } catch (_: Exception) { null }
    }

    private fun drawOverlayKV(
        canvas: Canvas, w: Float, bottomY: Float,
        data: List<Pair<String, String>>,
        colors: Map<String, Int> = emptyMap()
    ): Float {
        val fs = w * 0.037f
        val padV = w * 0.018f
        val padH = w * 0.04f
        val lh = fs * 1.28f
        val h = data.size * lh + padV * 2
        val top = bottomY - h

        val bgPaint = Paint().apply { color = argb(40, 0, 0, 0); mode = PaintMode.FILL }
        canvas.drawRect(Rect.makeXYWH(0f, top, w, h), bgPaint)

        val font = fontWithSize(fs)
        for ((i, pair) in data.withIndex()) {
            val (icon, txt) = pair
            val lineCenter = top + padV + i * lh + lh * 0.5f
            val lineColor = colors[icon] ?: parseHex("#e8ecf4")
            val textPaint = Paint().apply { color = lineColor; mode = PaintMode.FILL }
            val iconX = padH
            val textX = iconX + fs * 0.92f * 1.5f

            drawIcon(canvas, icon, iconX, lineCenter - fs * 0.46f, fs * 0.92f, lineColor)
            canvas.drawString(txt, textX, lineCenter + fs * 0.35f, font, textPaint)
        }
        return h
    }

    private fun drawIcon(canvas: Canvas, name: String, x: Float, y: Float, size: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            mode = PaintMode.STROKE
            strokeWidth = size * 0.09f
            isAntiAlias = true
        }
        val s = size / 24f
        val ox = x
        val oy = y
        fun ptX(px: Float) = ox + px * s
        fun ptY(py: Float) = oy + py * s

        when (name) {
            "pin" -> {
                val p = PathBuilder().apply {
                    moveTo(ptX(12f), ptY(2f))
                    lineTo(ptX(8f), ptY(2f))
                    cubicTo(ptX(5f), ptY(2f), ptX(5f), ptY(5.5f), ptX(5f), ptY(5.5f))
                    cubicTo(ptX(5f), ptY(9f), ptX(12f), ptY(15f), ptX(12f), ptY(15f))
                    cubicTo(ptX(12f), ptY(15f), ptX(19f), ptY(9f), ptX(19f), ptY(9f))
                    cubicTo(ptX(19f), ptY(5.5f), ptX(16f), ptY(2f), ptX(12f), ptY(2f))
                    close()
                }.snapshot()
                canvas.drawPath(p, paint)
            }
            "relogio" -> {
                canvas.drawCircle(ptX(12f), ptY(12f), 10f * s, paint)
                val hp = PathBuilder().apply {
                    moveTo(ptX(12f), ptY(6f))
                    lineTo(ptX(12f), ptY(12f))
                    lineTo(ptX(16f), ptY(14f))
                }.snapshot()
                canvas.drawPath(hp, paint)
            }
            "raio" -> {
                val p = PathBuilder().apply {
                    moveTo(ptX(13f), ptY(2f))
                    lineTo(ptX(3f), ptY(14f))
                    lineTo(ptX(10f), ptY(14f))
                    lineTo(ptX(11f), ptY(22f))
                    lineTo(ptX(21f), ptY(10f))
                    lineTo(ptX(14f), ptY(10f))
                    lineTo(ptX(13f), ptY(2f))
                    close()
                }.snapshot()
                canvas.drawPath(p, paint)
            }
            "hidro" -> {
                canvas.drawCircle(ptX(12f), ptY(12f), 10f * s, paint)
                val hp = PathBuilder().apply {
                    moveTo(ptX(7f), ptY(12f))
                    lineTo(ptX(17f), ptY(12f))
                }.snapshot()
                canvas.drawPath(hp, paint)
                val vp = PathBuilder().apply {
                    moveTo(ptX(12f), ptY(7f))
                    lineTo(ptX(12f), ptY(12f))
                }.snapshot()
                canvas.drawPath(vp, paint)
                val tp = PathBuilder().apply {
                    moveTo(ptX(15f), ptY(14f))
                    lineTo(ptX(12f), ptY(12f))
                }.snapshot()
                canvas.drawPath(tp, paint)
            }
            "status", "status2" -> {
                val p = PathBuilder().apply {
                    moveTo(ptX(2f), ptY(12f))
                    lineTo(ptX(6f), ptY(12f))
                    lineTo(ptX(9f), ptY(20f))
                    lineTo(ptX(13f), ptY(4f))
                    lineTo(ptX(16f), ptY(12f))
                    lineTo(ptX(22f), ptY(12f))
                }.snapshot()
                canvas.drawPath(p, paint)
            }
        }
    }

    private fun renderToJpeg(w: Int, h: Int, draw: (Canvas) -> Unit): ByteArray {
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(w, h)) ?: return ByteArray(0)
        val canvas = surface.canvas
        canvas.clear(0xFF000000.toInt())
        draw(canvas)
        surface.flushAndSubmit()
        val image = surface.makeImageSnapshot()
        val data = image.encodeToData(EncodedImageFormat.JPEG, 100) ?: return ByteArray(0)
        return data.bytes
    }

    private fun calcularDimensoesNA(img: Image): Pair<Int, Int> {
        val isLandscape = img.width > img.height
        return if (isLandscape) {
            2880 to (2880.0 * img.height / img.width).roundToInt()
        } else {
            val w = 2160
            w to (w.toDouble() * img.height / img.width).roundToInt().coerceIn(2160, 4320)
        }
    }

    private fun scaleCrop(img: Image, tw: Int, th: Int): Image {
        val surface = Surface.makeRaster(ImageInfo.makeN32Premul(tw, th)) ?: return img
        val canvas = surface.canvas
        canvas.clear(0xFF000000.toInt())
        val srcRect = Rect.makeWH(img.width.toFloat(), img.height.toFloat())
        val dstRect = Rect.makeWH(tw.toFloat(), th.toFloat())
        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawImageRect(img, srcRect, dstRect, paint)
        surface.flushAndSubmit()
        return surface.makeImageSnapshot()
    }

    private fun Image.toJpegBytes(): ByteArray {
        val data = this.encodeToData(EncodedImageFormat.JPEG, 100) ?: return ByteArray(0)
        return data.bytes
    }

    private fun drawPlaceholder(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val bg = Paint().apply { color = parseHex("#111318"); mode = PaintMode.FILL }
        canvas.drawRect(Rect.makeXYWH(x, y, w, h), bg)
        val fg = Paint().apply { color = parseHex("#5A6478"); mode = PaintMode.FILL }
        val font = fontWithSize(w * 0.035f)
        canvas.drawString("Foto nao registrada", x + w * 0.05f, y + h * 0.5f, font, fg)
    }

    private fun calcularAlturasEmpilhadas(fotoSup: Image?, fotoInf: Image?, canvasW: Int): Pair<Int, Int> {
        val defaults = NSUserDefaults.standardUserDefaults
        val ratioStr = (defaults.stringForKey("proporcao_camera") ?: "4x5").replace("x", ":", ignoreCase = true)
        if (ratioStr == "full") {
            fun hPorFoto(f: Image?): Int {
                val img = f ?: return (canvasW * 5 / 4) / 2
                val ratio = img.width.toFloat() / img.height.toFloat()
                return (canvasW.toFloat() / ratio).roundToInt().coerceIn(300, 4320)
            }
            return hPorFoto(fotoSup) to hPorFoto(fotoInf)
        }
        val meia = when (ratioStr) {
            "4:5" -> (canvasW * 5) / 4 / 2
            "3:4" -> (canvasW * 4) / 3 / 2
            "9:16" -> (canvasW * 16) / 9 / 2
            "1:1" -> canvasW / 2
            else -> canvasW / 2
        }
        return meia to meia
    }

    private fun lerProporcao(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        return (defaults.stringForKey("proporcao_camera") ?: "4x5").replace("x", ":", ignoreCase = true)
    }

    private fun deveGerarFlowmeterHibridoComoSimples(item: ItemHm): Boolean =
        item.tipo == "HM_VAZAO" && item.fotoSup != null && item.fotoInf == null

    // ═══ gerarRegistroNA ═════════════════════════════════════════════

    actual fun gerarRegistroNA(lago: LagoNA, valorNA: String?, horaOverride: String, foraDeNA: Boolean): RegistroGerado {
        val fotoBytes = lago.fotoRegua ?: return RegistroGerado(ByteArray(0), ByteArray(0), ByteArray(0))
        val foto = decode(fotoBytes) ?: return RegistroGerado(ByteArray(0), ByteArray(0), ByteArray(0))
        val (W, H) = calcularDimensoesNA(foto)
        val bmpLimpo = renderToJpeg(W, H) { canvas ->
            val cropped = scaleCrop(foto, W, H)
            canvas.drawImage(cropped, 0f, 0f)
        }

        val isExtravasor = lago.abreviacao.equals("DT2-ex", ignoreCase = true) ||
            lago.abreviacao.equals("CP-ex", ignoreCase = true)
        val overlayData = if (isExtravasor) {
            listOf("pin" to lago.nomeCard, "relogio" to horaOverride)
        } else {
            val naStr = when {
                foraDeNA -> "N.A: Fora do nivel da regua."
                valorNA.isNullOrBlank() -> "N.A: "
                else -> "N.A: ${valorNA.trimEnd('m')}m"
            }
            listOf("pin" to lago.nomeCard, "relogio" to horaOverride, "hidro" to naStr)
        }
        val bmpOverlay = renderToJpeg(W, H) { canvas ->
            drawOverlayKV(canvas, W.toFloat(), H.toFloat(), overlayData)
        }
        val bmpFinal = renderToJpeg(W, H) { canvas ->
            val limpa = decode(bmpLimpo); if (limpa != null) canvas.drawImage(limpa, 0f, 0f)
            val ov = decode(bmpOverlay); if (ov != null) canvas.drawImage(ov, 0f, 0f)
        }
        return RegistroGerado(bmpLimpo, bmpOverlay, bmpFinal)
    }

    // ═══ gerarRegistroHm ═════════════════════════════════════════════

    actual fun gerarRegistroHm(
        item: ItemHm, itensDaEstacao: List<ItemHm>, estacaoNome: String,
        statusBomba: String, horaOverride: String?
    ): RegistroGerado {
        val fotoSup = item.fotoSup?.let { decode(it) }
        val isLandscape = fotoSup?.let { it.width > it.height } ?: false
        val W = if (isLandscape) 2880 else 2160
        val horaFinal = horaOverride ?: horaAutomatica()
        val sCor = corStatus(statusBomba)

        if (deveGerarFlowmeterHibridoComoSimples(item)) {
            return gerarSimples(item, estacaoNome, statusBomba, horaOverride, W, sCor, fotoSup)
        }

        val fotoInf = item.fotoInf?.let { decode(it) }
        val (hSup, hInf) = calcularAlturasEmpilhadas(fotoSup, fotoInf, W)
        val H = hSup + hInf
        val sLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
            "$statusBomba // VAZ: ${item.leituraManual}" else statusBomba

        val bmpLimpo = renderToJpeg(W, H) { c ->
            if (fotoSup != null) c.drawImage(scaleCrop(fotoSup, W, hSup), 0f, 0f)
            else drawPlaceholder(c, 0f, 0f, W.toFloat(), hSup.toFloat())
            val divPaint = Paint().apply { color = parseHex("#00d4ff"); mode = PaintMode.FILL }
            c.drawRect(Rect.makeXYWH(0f, hSup - 2f, W.toFloat(), 4f), divPaint)
            if (fotoInf != null) c.drawImage(scaleCrop(fotoInf, W, hInf), 0f, hSup.toFloat())
            else drawPlaceholder(c, 0f, hSup.toFloat(), W.toFloat(), hInf.toFloat())
        }
        val bmpOverlay = renderToJpeg(W, H) { c ->
            drawOverlayKV(c, W.toFloat(), hSup.toFloat(), listOf("pin" to estacaoNome, "hidro" to montarTitulo(item, estacaoNome)))
            drawOverlayKV(c, W.toFloat(), H.toFloat(), listOf("raio" to "BOMBA-${item.id.padStart(2, '0')}", "relogio" to horaFinal, "status" to sLeitura), mapOf("status" to sCor))
        }
        val bmpFinal = renderToJpeg(W, H) { c ->
            val l = decode(bmpLimpo); if (l != null) c.drawImage(l, 0f, 0f)
            val o = decode(bmpOverlay); if (o != null) c.drawImage(o, 0f, 0f)
        }
        return RegistroGerado(bmpLimpo, bmpOverlay, bmpFinal)
    }

    private fun gerarSimples(
        item: ItemHm, estacao: String, statusBomba: String, horaOverride: String?,
        W: Int, sCor: Int, fotoSup: Image?
    ): RegistroGerado {
        val bmpFoto = fotoSup
        val imgH = bmpFoto?.height?.toFloat()?.let { if (it > 0f) it else 100f } ?: 100f
        val imgW = bmpFoto?.width?.toFloat()?.let { if (it > 0f) it else W.toFloat() } ?: W.toFloat()
        val alturaFinal = (W.toFloat() * imgH / imgW).roundToInt().coerceAtLeast(1200)
        val horaUsar = horaOverride ?: item.dataHoraSup.ifEmpty { horaAutomatica() }
        val titulo = montarTitulo(item, estacao)
        val sLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
            "$statusBomba // VAZ: ${item.leituraManual}" else statusBomba

        val bmpLimpo = renderToJpeg(W, alturaFinal) { c ->
            if (bmpFoto != null) c.drawImage(scaleCrop(bmpFoto, W, alturaFinal), 0f, 0f)
            else drawPlaceholder(c, 0f, 0f, W.toFloat(), alturaFinal.toFloat())
        }
        val bmpOverlay = renderToJpeg(W, alturaFinal) { c ->
            drawOverlayKV(c, W.toFloat(), alturaFinal.toFloat(), listOf("hidro" to titulo, "relogio" to horaUsar, "status" to sLeitura), mapOf("status" to sCor))
        }
        val bmpFinal = renderToJpeg(W, alturaFinal) { c ->
            val l = decode(bmpLimpo); if (l != null) c.drawImage(l, 0f, 0f)
            val o = decode(bmpOverlay); if (o != null) c.drawImage(o, 0f, 0f)
        }
        return RegistroGerado(bmpLimpo, bmpOverlay, bmpFinal)
    }

    // ═══ gerarRegistroLivre ═══════════════════════════════════════════

    actual fun gerarRegistroLivre(item: ItemHm, statusBomba: String, horaOverride: String?): RegistroGerado {
        val fotoBytes = item.fotoSup ?: return RegistroGerado(ByteArray(0), ByteArray(0), ByteArray(0))
        val foto = decode(fotoBytes) ?: return RegistroGerado(ByteArray(0), ByteArray(0), ByteArray(0))
        val wF = foto.width.toFloat(); val hF = foto.height.toFloat()
        val isLandscape = wF > hF
        val W = if (isLandscape) 2880 else 2160
        val H = (W.toFloat() * hF / wF).roundToInt().coerceIn(1200, 4320)
        val horaFinal = horaOverride ?: horaAutomatica()
        val sOverlay = if (statusBomba == "NENHUM") null else statusBomba

        val bmpLimpo = renderToJpeg(W, H) { c ->
            c.drawImage(scaleCrop(foto, W, H), 0f, 0f)
        }
        val overlayData = mutableListOf<Pair<String, String>>()
        val overlayColors = mutableMapOf<String, Int>()
        if (item.incluirDataHoraLivre) overlayData.add("relogio" to horaFinal)
        val textoNorm = item.textoLivre.trim()
        if (textoNorm.isNotEmpty()) {
            textoNorm.split('\n').forEach { linha -> overlayData.add("__cont__" to linha.trimEnd()) }
        }
        if (!sOverlay.isNullOrBlank()) {
            overlayData.add("status" to sOverlay)
            overlayColors["status"] = corStatus(sOverlay)
        }
        if (overlayData.isEmpty()) overlayData.add("__cont__" to " ")

        val bmpOverlay = renderToJpeg(W, H) { c ->
            drawOverlayKV(c, W.toFloat(), H.toFloat(), overlayData, overlayColors)
        }
        val bmpFinal = renderToJpeg(W, H) { c ->
            val l = decode(bmpLimpo); if (l != null) c.drawImage(l, 0f, 0f)
            val o = decode(bmpOverlay); if (o != null) c.drawImage(o, 0f, 0f)
        }
        return RegistroGerado(bmpLimpo, bmpOverlay, bmpFinal)
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Extension helpers
// ═════════════════════════════════════════════════════════════════════

internal fun ByteArray.toNSData(): platform.Foundation.NSData {
    return this.usePinned { pinned ->
        platform.Foundation.NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}

internal fun platform.Foundation.NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { arr ->
        arr.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
}

internal fun ByteArray.toUIImage(): UIImage? {
    val nsData = this.toNSData()
    return UIImage(data = nsData)
}

internal fun ByteArray.toSkiaImage(): Image? {
    return try { Image.makeFromEncoded(this) } catch (_: Exception) { null }
}
