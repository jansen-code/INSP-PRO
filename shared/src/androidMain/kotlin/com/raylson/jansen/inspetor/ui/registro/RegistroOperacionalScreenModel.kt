package com.raylson.jansen.inspetor.ui.registro

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import cafe.adriel.voyager.core.model.ScreenModel
import com.raylson.jansen.inspetor.ImageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ItemRegistro(
    val id: String,
    val label: String,
    val corIcone: String,
    val statusDisponiveis: List<String> = emptyList(),
    val statusPadrao: String = "",
    val statusAtual: String = statusPadrao,
    val bitmap: Bitmap? = null,
    val fotoPath: String? = null,
    val dataHora: String = "",
    val concluido: Boolean = false
)

data class ConfigEstacao(
    val tituloHeader: String,
    val rotuloHeader: String,
    val statusPermitidos: List<String>,
    val statusPadrao: String,
    val usaStatusPorItem: Boolean = false
)

data class RegistroOperacionalState(
    val estacaoAtual: String = "DET-01",
    val statusBomba: String = "DESLIGADA",
    val itens: List<ItemRegistro> = emptyList(),
    val pedidoAbrirCameraIndex: Int = -1,
    val resultadoImagem: Bitmap? = null
)

class RegistroOperacionalScreenModel(private val appContext: Context) : ScreenModel {
    private val _state = MutableStateFlow(RegistroOperacionalState(itens = criarItensEstacao("DET-01")))
    val state: StateFlow<RegistroOperacionalState> = _state

    private val configs = mapOf(
        "DET-01" to ConfigEstacao("BOMBA CORRESPONDENTE", "DET-01", listOf("DESLIGADA", "LIGADA"), "DESLIGADA"),
        "ARB-05" to ConfigEstacao("FLOWMETER", "ARB-05", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO"),
        "ARB-06" to ConfigEstacao("FLOWMETER", "ARB-06", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO"),
        "ARB-07" to ConfigEstacao("FLOWMETER", "ARB-07", emptyList(), "", true),
        "ARB-08" to ConfigEstacao("FLOWMETER", "ARB-08", emptyList(), "", true),
        "ARB-09" to ConfigEstacao("FLOWMETER", "ARB-09", emptyList(), "", true)
    )

    private val templatesEstacoes = mapOf(
        "DET-01" to listOf(
            ItemRegistro("HM-01", "REGISTRO DO / HM-01", "#EF4444"),
            ItemRegistro("BM-01", "REGISTRO DA / BM-01", "#F59E0B"),
            ItemRegistro("HM-02", "REGISTRO DO / HM-02", "#EF4444"),
            ItemRegistro("BM-02", "REGISTRO DA / BM-02", "#F59E0B"),
            ItemRegistro("HM-03", "REGISTRO DO / HM-03", "#EF4444"),
            ItemRegistro("BM-03", "REGISTRO DA / BM-03", "#F59E0B"),
            ItemRegistro("HM-04", "REGISTRO DO / HM-04", "#EF4444"),
            ItemRegistro("BM-04", "REGISTRO DA / BM-04", "#F59E0B"),
            ItemRegistro("SIF-INF", "SIFÃO INFERIOR", "#22C55E"),
            ItemRegistro("SIF-SUP", "SIFÃO SUPERIOR", "#06B6D4"),
            ItemRegistro("GALERIA", "GALERIA", "#8B5CF6"),
            ItemRegistro("CALHA", "CALHA PARSHALL", "#F97316")
        ),
        "ARB-05" to listOf(ItemRegistro("ARB-05", "FLOWMETER ARB-05", "#EAB308")),
        "ARB-06" to listOf(ItemRegistro("ARB-06", "FLOWMETER ARB-06", "#22C55E")),
        "ARB-07" to listOf(
            ItemRegistro("BA-73", "FLOWMETER ARB-07 BA-73", "#F59E0B", listOf("ZERADO", "DESLIGADO"), "ZERADO"),
            ItemRegistro("BA-74", "FLOWMETER ARB-07 BA-74", "#F59E0B", listOf("ZERADO", "DESLIGADO"), "ZERADO")
        ),
        "ARB-08" to listOf(
            ItemRegistro("BA-85", "FLOWMETER ARB-08 BA-85", "#EF4444", listOf("COM VAZÃO", "SEM VAZÃO", "DESLIGADO"), "COM VAZÃO"),
            ItemRegistro("BA-86", "FLOWMETER ARB-08 BA-86", "#EF4444", listOf("DESLIGADO", "LIGADO"), "DESLIGADO"),
            ItemRegistro("BA-87", "FLOWMETER ARB-08 BA-87", "#EF4444", listOf("COM VAZÃO", "SEM VAZÃO", "DESLIGADO"), "COM VAZÃO")
        ),
        "ARB-09" to listOf(
            ItemRegistro("9.1", "FLOWMETER ARB-09 9.1", "#06B6D4", listOf("COM VAZÃO", "SEM VAZÃO", "DESLIGADO"), "COM VAZÃO"),
            ItemRegistro("9.2", "FLOWMETER ARB-09 9.2", "#06B6D4", listOf("COM VAZÃO", "SEM VAZÃO", "DESLIGADO"), "COM VAZÃO")
        )
    )

    fun corEstacao(nomeEstacao: String): String = when (nomeEstacao) {
        "DET-01" -> "#2563EB"
        "ARB-05" -> "#EAB308"
        "ARB-06" -> "#22C55E"
        "ARB-07" -> "#F59E0B"
        "ARB-08" -> "#EF4444"
        "ARB-09" -> "#06B6D4"
        else -> "#2563EB"
    }

    fun corStatus(status: String): String = when (status) {
        "LIGADA", "LIGADO", "COM VAZÃO" -> "#22C55E"
        "ZERADO" -> "#F59E0B"
        else -> "#EF4444"
    }

    private fun criarItensEstacao(nomeEstacao: String): List<ItemRegistro> =
        templatesEstacoes[nomeEstacao].orEmpty().map { it.copy() }

    fun carregarEstacao(novaEstacao: String) {
        val config = configs[novaEstacao] ?: return
        _state.update {
            it.copy(
                estacaoAtual = novaEstacao,
                statusBomba = config.statusPadrao,
                itens = criarItensEstacao(novaEstacao)
            )
        }
    }

    fun alternarStatusGlobal() {
        val config = configs[_state.value.estacaoAtual] ?: return
        if (config.statusPermitidos.isEmpty()) return

        val idxAtual = config.statusPermitidos.indexOf(_state.value.statusBomba)
        val proximo = if (idxAtual == -1) 0 else (idxAtual + 1) % config.statusPermitidos.size
        _state.update { it.copy(statusBomba = config.statusPermitidos[proximo]) }
    }

    fun alternarStatusItem(idx: Int) {
        val itens = _state.value.itens
        val item = itens.getOrNull(idx) ?: return
        if (item.statusDisponiveis.isEmpty()) return

        val idxAtual = item.statusDisponiveis.indexOf(item.statusAtual)
        val proximo = if (idxAtual == -1) 0 else (idxAtual + 1) % item.statusDisponiveis.size
        val itemAtualizado = item.copy(statusAtual = item.statusDisponiveis[proximo])

        _state.update { it.copy(itens = itens.toMutableList().apply { set(idx, itemAtualizado) }) }
    }

    fun configAtual(): ConfigEstacao? = configs[_state.value.estacaoAtual]

    fun abrirOrigemFoto(idx: Int) {
        _state.update { it.copy(pedidoAbrirCameraIndex = idx) }
    }

    fun confirmarCameraAberta() {
        _state.update { it.copy(pedidoAbrirCameraIndex = -1) }
    }

    fun vincularBitmapAoItem(index: Int, bmp: Bitmap) {
        val itens = _state.value.itens
        val item = itens.getOrNull(index) ?: return
        val bmpProcessado = processarBitmapConformeConfiguracao(bmp)

        var fotoPath: String? = null
        try {
            val arquivoTemporario = File(appContext.cacheDir, "foto_temp_${item.id}.jpg")
            FileOutputStream(arquivoTemporario).use { out ->
                bmpProcessado.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            fotoPath = arquivoTemporario.absolutePath
        } catch (e: Exception) { e.printStackTrace() }

        val itemAtualizado = item.copy(
            bitmap = bmpProcessado,
            dataHora = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date()),
            concluido = true,
            fotoPath = fotoPath
        )
        _state.update { it.copy(itens = itens.toMutableList().apply { set(index, itemAtualizado) }) }
    }

    private fun processarBitmapConformeConfiguracao(bitmap: Bitmap): Bitmap {
        val estacaoAtual = _state.value.estacaoAtual
        if (!ImageHelper.estacaoUsaProporcaoConfig(estacaoAtual)) return bitmap
        val proporcaoSelecionada = ImageHelper.lerProporcao(appContext)
        return ImageHelper.recortarPorProporcao(bitmap, proporcaoSelecionada)
    }

    private fun statusParaImagem(item: ItemRegistro): String =
        if (item.statusDisponiveis.isNotEmpty()) item.statusAtual else _state.value.statusBomba

    private fun deveExibirStatus(item: ItemRegistro, status: String): Boolean {
        if (status.isBlank()) return false
        val estacaoAtual = _state.value.estacaoAtual
        if ((estacaoAtual == "ARB-05" || estacaoAtual == "ARB-06") && status == "LIGADO") return false
        return true
    }

    fun gerarImagem() {
        val estado = _state.value
        val concluidos = estado.itens.filter { it.concluido && it.bitmap != null }
        if (concluidos.isEmpty()) {
            Toast.makeText(appContext, "Nenhuma foto registrada válida.", Toast.LENGTH_SHORT).show()
            return
        }

        val largura = 1080f
        val margem = 36f
        val larguraCard = largura - margem * 2f
        val alturaHeader = 150f
        val gapCard = 28f
        val alturaFoto = 500f

        val linhasPorCard = concluidos.map { item ->
            val statusItem = statusParaImagem(item)
            buildList {
                add(estado.estacaoAtual)
                add(item.label.uppercase(Locale.getDefault()))
                add(item.dataHora)
                if (deveExibirStatus(item, statusItem)) add("STATUS: $statusItem")
            }
        }

        val alturasInfo = linhasPorCard.map { linhas -> 56f + linhas.size * 34f }
        val alturaCards = alturasInfo.sum() + concluidos.size * alturaFoto + (concluidos.size - 1) * gapCard
        val alturaFinal = (alturaHeader + 28f + alturaCards + 40f).toInt()

        val bmpFinal = Bitmap.createBitmap(largura.toInt(), alturaFinal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmpFinal)
        canvas.drawColor(Color.WHITE)

        desenharCabecalhoRegistro(canvas, largura, alturaHeader, estado)

        var yAtual = alturaHeader + 28f
        concluidos.forEachIndexed { index, item ->
            val linhas = linhasPorCard[index]
            val alturaInfo = alturasInfo[index]
            desenharCardRegistro(
                canvas, item.bitmap!!, margem, yAtual, larguraCard, alturaFoto, alturaInfo,
                linhas, corStatus(statusParaImagem(item)), estado
            )
            yAtual += alturaFoto + alturaInfo + gapCard
        }

        _state.update { it.copy(resultadoImagem = bmpFinal) }
    }

    fun fecharDialogResultado() {
        _state.update { it.copy(resultadoImagem = null) }
    }

    private fun desenharCabecalhoRegistro(c: Canvas, largura: Float, alturaHeader: Float, estado: RegistroOperacionalState) {
        val card = RectF(24f, 18f, largura - 24f, alturaHeader)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 2f }
        c.drawRoundRect(card, 28f, 28f, bgPaint)
        c.drawRoundRect(card, 28f, 28f, border)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(corEstacao(estado.estacaoAtual)) }
        c.drawRoundRect(RectF(card.left, card.top, card.right, card.top + 8f), 28f, 28f, accent)

        val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A"); textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) }
        val subtitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#475569"); textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(corEstacao(estado.estacaoAtual)) }
        val badgeRect = RectF(card.left + 26f, card.top + 26f, card.left + 220f, card.top + 68f)
        c.drawRoundRect(badgeRect, 20f, 20f, badge)

        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        c.drawText(estado.estacaoAtual, badgeRect.centerX(), badgeRect.centerY() + 8f, badgeText)
        c.drawText("Imagem final processada", card.left + 26f, card.top + 102f, titulo)
        val statusCabecalho = if (configs[estado.estacaoAtual]?.usaStatusPorItem == true) "Status por item" else "Status geral: ${estado.statusBomba}"
        val dataCabecalho = SimpleDateFormat("dd.MM.yyyy    HH:mm", Locale.getDefault()).format(Date())
        c.drawText("$statusCabecalho    $dataCabecalho", card.left + 26f, card.top + 132f, subtitulo)
    }

    private fun desenharCardRegistro(c: Canvas, bmp: Bitmap, x: Float, y: Float, w: Float, fotoH: Float, infoH: Float, linhas: List<String>, corStatusHex: String, estado: RegistroOperacionalState) {
        val cardRect = RectF(x, y, x + w, y + fotoH + infoH)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9E2EC"); style = Paint.Style.STROKE; strokeWidth = 2f }
        c.drawRoundRect(cardRect, 26f, 26f, fillPaint)
        c.drawRoundRect(cardRect, 26f, 26f, borderPaint)

        val fotoRect = RectF(x, y, x + w, y + fotoH)
        val fotoPath = Path().apply { addRoundRect(fotoRect, floatArrayOf(26f, 26f, 26f, 26f, 0f, 0f, 0f, 0f), Path.Direction.CW) }
        c.save()
        c.clipPath(fotoPath)
        val scale = maxOf(w / bmp.width, fotoH / bmp.height)
        val sw = bmp.width * scale
        val sh = bmp.height * scale
        val left = x + (w - sw) / 2f
        val top = y + (fotoH - sh) / 2f
        c.drawBitmap(bmp, null, RectF(left, top, left + sw, top + sh), Paint(Paint.ANTI_ALIAS_FLAG))
        c.restore()

        val faixa = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(corEstacao(estado.estacaoAtual)) }
        c.drawRect(x, y + fotoH - 8f, x + w, y + fotoH, faixa)

        val infoTop = y + fotoH
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        c.drawRoundRect(RectF(x, infoTop, x + w, y + fotoH + infoH), 0f, 0f, infoPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A"); textSize = 28f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) }
        val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#475569"); textSize = 24f }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(corStatusHex); textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) }

        val leftText = x + 28f
        var yLinha = infoTop + 42f
        linhas.forEachIndexed { idx, linha ->
            val paint = when {
                idx == 0 -> textPaint
                linha.startsWith("STATUS:") -> statusPaint
                else -> secondaryPaint
            }
            c.drawText(linha, leftText, yLinha, paint)
            yLinha += if (idx == 0) 34f else 32f
        }
    }

    fun salvarImagem(bmp: Bitmap) {
        val nome = "${_state.value.estacaoAtual}_REG_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.png"
        val out: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nome)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                ?.let { appContext.contentResolver.openOutputStream(it) }
        } else {
            @Suppress("DEPRECATION")
            FileOutputStream(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), nome))
        }

        out?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Toast.makeText(appContext, " Imagem salva", Toast.LENGTH_SHORT).show()
    }

    fun compartilharImagem(bmp: Bitmap) {
        val file = File(appContext.cacheDir, "registro_temp.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri: Uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", file)
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Compartilhar"
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        appContext.startActivity(intent)
    }
}
