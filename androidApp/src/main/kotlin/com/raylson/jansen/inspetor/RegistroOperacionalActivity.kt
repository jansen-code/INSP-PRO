package com.raylson.jansen.inspetor

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    var statusAtual: String = statusPadrao,
    var bitmap: Bitmap? = null,
    var fotoPath: String? = null, 
    var dataHora: String = "",
    var concluido: Boolean = false
)

data class ConfigEstacao(
    val tituloHeader: String,
    val rotuloHeader: String,
    val statusPermitidos: List<String>,
    val statusPadrao: String,
    val usaStatusPorItem: Boolean = false
)

class RegistroOperacionalActivity : AppCompatActivity() {

    private var estacaoAtual = "DET-01"
    private var statusBomba = "DESLIGADA"
    private var captureIndex = -1

    // ==================== NOVA CÂMERA COM CAMERAX ====================
    // ==================== NOVA CÂMERA COM CAMERAX ====================
    private fun abrirCameraComCameraX() {
        // ═══ FORÇA A PROPORÇÃO 4:5 SEMPRE QUE ABRIR A CÂMERA ═══
        SecurePrefs.get(this, ConfiguracoesActivity.PREFS_NAME)
            .edit().putString(ConfiguracoesActivity.PREF_PROPORCAO, ConfiguracoesActivity.PROP_4x5).apply()

        val intent = Intent(this, CameraCaptureActivity::class.java).apply {
            putExtra(CameraCaptureActivity.EXTRA_RATIO, ConfiguracoesActivity.PROP_4x5)
        }
        startActivityForResult(intent, REQUEST_CAMERA_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CAMERA_CAPTURE && resultCode == RESULT_OK && data != null) {
            val photoPath = data.getStringExtra(CameraCaptureActivity.RESULT_PHOTO_PATH)
            if (!photoPath.isNullOrEmpty() && captureIndex >= 0) {
                val bmp = ImageHelper.carregarComExif(photoPath)
                if (bmp != null) {
                    vincularBitmapAoItem(bmp)
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA_CAPTURE = 301
    }
    // ============================================================

    private fun vincularBitmapAoItem(bmp: Bitmap) {
        val itemModificado = itens[captureIndex]
        val bmpProcessado = processarBitmapConformeConfiguracao(bmp)

        itemModificado.bitmap = bmpProcessado
        itemModificado.dataHora = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())
        itemModificado.concluido = true

        try {
            val arquivoTemporario = File(cacheDir, "foto_temp_${itemModificado.id}.jpg")
            val out = FileOutputStream(arquivoTemporario)
            bmpProcessado.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()
            itemModificado.fotoPath = arquivoTemporario.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }

        adapter.notifyDataSetChanged()
    }

    private fun processarBitmapConformeConfiguracao(bitmap: Bitmap): Bitmap {
        if (!ImageHelper.estacaoUsaProporcaoConfig(estacaoAtual)) return bitmap

        val proporcaoSelecionada = ImageHelper.lerProporcao(this)
        return ImageHelper.recortarPorProporcao(bitmap, proporcaoSelecionada)
    }

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

    private val itens = mutableListOf<ItemRegistro>()
    private lateinit var adapter: RegistroAdapter
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro_operacional)

        setupStationButtons()
        setupRecycler()

        if (savedInstanceState == null) {
            carregarEstacao(estacaoAtual)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("ESTACAO_ATUAL", estacaoAtual)
        outState.putString("STATUS_BOMBA", statusBomba)
        
        itens.forEach { item ->
            outState.putString("STATUS_${item.id}", item.statusAtual)
            outState.putBoolean("CONCLUIDO_${item.id}", item.concluido)
            outState.putString("DATAHORA_${item.id}", item.dataHora)
            outState.putString("PATH_${item.id}", item.fotoPath)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        estacaoAtual = savedInstanceState.getString("ESTACAO_ATUAL", "DET-01")
        statusBomba = savedInstanceState.getString("STATUS_BOMBA", "DESLIGADA")
        
        itens.clear()
        itens.addAll(criarItensEstacao(estacaoAtual))
        
        itens.forEach { item ->
            item.statusAtual = savedInstanceState.getString("STATUS_${item.id}", item.statusPadrao)
            item.concluido = savedInstanceState.getBoolean("CONCLUIDO_${item.id}", false)
            item.dataHora = savedInstanceState.getString("DATAHORA_${item.id}", "")
            item.fotoPath = savedInstanceState.getString("PATH_${item.id}", null)
            
            item.fotoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    item.bitmap = ImageHelper.carregarComExif(path) ?: BitmapFactory.decodeFile(path)
                }
            }
        }
        
        atualizarDestaqueBotoes()
        atualizarTituloBarra()
        adapter.notifyDataSetChanged()
    }

    private fun corEstacao(nomeEstacao: String): String = when (nomeEstacao) {
        "DET-01" -> "#2563EB"
        "ARB-05" -> "#EAB308"
        "ARB-06" -> "#22C55E"
        "ARB-07" -> "#F59E0B"
        "ARB-08" -> "#EF4444"
        "ARB-09" -> "#06B6D4"
        else -> "#2563EB"
    }

    private fun corStatus(status: String): String = when (status) {
        "LIGADA", "LIGADO", "COM VAZÃO" -> "#22C55E"
        "ZERADO" -> "#F59E0B"
        else -> "#EF4444"
    }

    private fun criarItensEstacao(nomeEstacao: String): List<ItemRegistro> {
        return templatesEstacoes[nomeEstacao].orEmpty().map { item ->
            item.copy(bitmap = null, dataHora = "", concluido = false, statusAtual = item.statusPadrao, fotoPath = null)
        }
    }

    private fun atualizarTituloBarra() {
        try {
            val root = findViewById<ViewGroup>(android.R.id.content)
            val mainLayout = root.getChildAt(0) as? ViewGroup
            val topBar = mainLayout?.getChildAt(0) as? ViewGroup
            val tvTitulo = topBar?.getChildAt(1) as? TextView
            tvTitulo?.text = "$estacaoAtual • Registro"
        } catch (_: Exception) {}
    }

    private fun setupStationButtons() {
        val botoesMap = mapOf(
            R.id.btnDet01 to "DET-01", R.id.btnArb05 to "ARB-05", R.id.btnArb06 to "ARB-06",
            R.id.btnArb07 to "ARB-07", R.id.btnArb08 to "ARB-08", R.id.btnArb09 to "ARB-09"
        )
        botoesMap.forEach { (idBtn, nomeEstacao) ->
            findViewById<TextView>(idBtn)?.setOnClickListener { carregarEstacao(nomeEstacao) }
        }
    }

    private fun carregarEstacao(novaEstacao: String) {
        estacaoAtual = novaEstacao
        val config = configs[novaEstacao] ?: return
        statusBomba = config.statusPadrao

        itens.clear()
        itens.addAll(criarItensEstacao(novaEstacao))
        adapter.notifyDataSetChanged()
        recycler.scrollToPosition(0)

        atualizarDestaqueBotoes()
        atualizarTituloBarra()
    }

    private fun atualizarDestaqueBotoes() {
        val botoes = mapOf(
            "DET-01" to R.id.btnDet01, "ARB-05" to R.id.btnArb05, "ARB-06" to R.id.btnArb06,
            "ARB-07" to R.id.btnArb07, "ARB-08" to R.id.btnArb08, "ARB-09" to R.id.btnArb09
        )
        botoes.forEach { (nome, id) ->
            val view = findViewById<TextView>(id) ?: return@forEach
            if (nome == estacaoAtual) {
                view.setBackgroundColor(Color.parseColor(corEstacao(nome)))
                view.setTextColor(Color.WHITE)
            } else {
                view.setBackgroundColor(Color.parseColor("#1F2937"))
                view.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
    }

    private fun setupRecycler() {
        recycler = findViewById(R.id.recyclerRegistros)
        adapter = RegistroAdapter()
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recycler.adapter = adapter
    }

    private fun abrirOrigemFoto(index: Int) {
        captureIndex = index
        abrirCameraComCameraX()
    }

    private fun alternarStatusGlobal() {
        val config = configs[estacaoAtual] ?: return
        if (config.statusPermitidos.isEmpty()) return

        val idxAtual = config.statusPermitidos.indexOf(statusBomba)
        val proximo = if (idxAtual == -1) 0 else (idxAtual + 1) % config.statusPermitidos.size
        statusBomba = config.statusPermitidos[proximo]
        adapter.notifyDataSetChanged()
    }

    private fun alternarStatusItem(idx: Int) {
        val item = itens[idx]
        if (item.statusDisponiveis.isEmpty()) return

        val idxAtual = item.statusDisponiveis.indexOf(item.statusAtual)
        val proximo = if (idxAtual == -1) 0 else (idxAtual + 1) % item.statusDisponiveis.size
        item.statusAtual = item.statusDisponiveis[proximo]
        adapter.notifyDataSetChanged()
    }

    private fun statusParaImagem(item: ItemRegistro): String {
        return if (item.statusDisponiveis.isNotEmpty()) item.statusAtual else statusBomba
    }

    private fun deveExibirStatus(item: ItemRegistro, status: String): Boolean {
        if (status.isBlank()) return false
        if ((estacaoAtual == "ARB-05" || estacaoAtual == "ARB-06") && status == "LIGADO") return false
        return true
    }

    private fun gerarImagem() {
        val concluidos = itens.filter { it.concluido && it.bitmap != null }
        if (concluidos.isEmpty()) {
            Toast.makeText(this, "Nenhuma foto registrada válida.", Toast.LENGTH_SHORT).show()
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
                add(estacaoAtual)
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

        desenharCabecalhoRegistro(canvas, largura, alturaHeader)

        var yAtual = alturaHeader + 28f
        concluidos.forEachIndexed { index, item ->
            val linhas = linhasPorCard[index]
            val alturaInfo = alturasInfo[index]
            desenharCardRegistro(
                canvas,
                item.bitmap!!,
                margem,
                yAtual,
                larguraCard,
                alturaFoto,
                alturaInfo,
                linhas,
                corStatus(statusParaImagem(item))
            )
            yAtual += alturaFoto + alturaInfo + gapCard
        }

        mostrarDialogResultado(bmpFinal)
    }

    private fun desenharCabecalhoRegistro(c: Canvas, largura: Float, alturaHeader: Float) {
        val card = RectF(24f, 18f, largura - 24f, alturaHeader)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(card, 28f, 28f, bgPaint)
        c.drawRoundRect(card, 28f, 28f, border)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(corEstacao(estacaoAtual)) }
        c.drawRoundRect(RectF(card.left, card.top, card.right, card.top + 8f), 28f, 28f, accent)

        val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val subtitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(corEstacao(estacaoAtual)) }
        val badgeRect = RectF(card.left + 26f, card.top + 26f, card.left + 220f, card.top + 68f)
        c.drawRoundRect(badgeRect, 20f, 20f, badge)

        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(estacaoAtual, badgeRect.centerX(), badgeRect.centerY() + 8f, badgeText)

        c.drawText("Imagem final processada", card.left + 26f, card.top + 102f, titulo)
        val statusCabecalho = if (configs[estacaoAtual]?.usaStatusPorItem == true) "Status por item" else "Status geral: $statusBomba"
        val dataCabecalho = SimpleDateFormat("dd.MM.yyyy  •  HH:mm", Locale.getDefault()).format(Date())
        c.drawText("$statusCabecalho  •  $dataCabecalho", card.left + 26f, card.top + 132f, subtitulo)
    }

    private fun desenharCardRegistro(
        c: Canvas,
        bmp: Bitmap,
        x: Float,
        y: Float,
        w: Float,
        fotoH: Float,
        infoH: Float,
        linhas: List<String>,
        corStatusHex: String
    ) {
        val cardRect = RectF(x, y, x + w, y + fotoH + infoH)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9E2EC")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(cardRect, 26f, 26f, fillPaint)
        c.drawRoundRect(cardRect, 26f, 26f, borderPaint)

        val fotoRect = RectF(x, y, x + w, y + fotoH)
        val fotoPath = Path().apply { addRoundRect(fotoRect, floatArrayOf(26f,26f,26f,26f,0f,0f,0f,0f), Path.Direction.CW) }
        c.save()
        c.clipPath(fotoPath)
        val scale = maxOf(w / bmp.width, fotoH / bmp.height)
        val sw = bmp.width * scale
        val sh = bmp.height * scale
        val left = x + (w - sw) / 2f
        val top = y + (fotoH - sh) / 2f
        c.drawBitmap(bmp, null, RectF(left, top, left + sw, top + sh), Paint(Paint.ANTI_ALIAS_FLAG))
        c.restore()

        val faixa = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(corEstacao(estacaoAtual)) }
        c.drawRect(x, y + fotoH - 8f, x + w, y + fotoH, faixa)

        val infoTop = y + fotoH
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        c.drawRoundRect(RectF(x, infoTop, x + w, y + fotoH + infoH), 0f, 0f, infoPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 24f
        }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(corStatusHex)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

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

    private fun mostrarDialogResultado(bmp: Bitmap) {
        val view = layoutInflater.inflate(R.layout.dialog_registro_resultado, null)
        val d = android.app.AlertDialog.Builder(this).setView(view).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // O diálogo agora usa duas camadas (limpa + overlay). Como esta Activity
        // não tem edição ao vivo de filtros, alimentamos imgResultadoLimpo com
        // o bitmap final e deixamos imgResultadoOverlay vazio (transparente).
        view.findViewById<ImageView>(R.id.imgResultadoLimpo).setImageBitmap(bmp)
        view.findViewById<Button>(R.id.btnBaixar).setOnClickListener { salvarImagem(bmp) }
        view.findViewById<Button>(R.id.btnCompartilhar).setOnClickListener { compartilharImagem(bmp) }
        view.findViewById<ImageView>(R.id.btnFecharDialog).setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun salvarImagem(bmp: Bitmap) {
        val nome = "${estacaoAtual}_REG_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.png"
        val out: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nome)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { contentResolver.openOutputStream(it) }
        } else {
            @Suppress("DEPRECATION")
            FileOutputStream(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), nome))
        }

        out?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Toast.makeText(this, "✓ Imagem salva", Toast.LENGTH_SHORT).show()
    }

    private fun compartilharImagem(bmp: Bitmap) {
        val file = File(cacheDir, "registro_temp.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@RegistroOperacionalActivity, "$packageName.provider", file))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Compartilhar"
            )
        )
    }

    inner class RegistroAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TYPE_HEADER = 0
        private val TYPE_ITEM = 1
        private val TYPE_FOOTER = 2
        private val totalLogico get() = if (itens.isEmpty()) 0 else 1 + itens.size + 1

        override fun getItemCount(): Int = totalLogico

        override fun getItemViewType(pos: Int): Int = when (pos) {
            0 -> TYPE_HEADER
            totalLogico - 1 -> TYPE_FOOTER
            else -> TYPE_ITEM
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_registro_header, parent, false))
                TYPE_FOOTER -> FooterVH(inflater.inflate(R.layout.item_registro_footer, parent, false))
                else -> ItemVH(inflater.inflate(R.layout.item_registro_camera, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            when (holder) {
                is HeaderVH -> holder.bind()
                is FooterVH -> holder.bind()
                is ItemVH -> holder.bind(pos - 1)
            }
        }

        inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            private val tvHeaderCaption: TextView = v.findViewById(R.id.tvHeaderCaption)
            private val tvBomba: TextView = v.findViewById(R.id.tvBombaCorrespondente)
            private val toggle: ToggleButton = v.findViewById(R.id.toggleStatus)
            private val tvSectionLabel: TextView = v.findViewById(R.id.tvSectionLabel)

            fun bind() {
                val config = configs[estacaoAtual] ?: return
                tvHeaderCaption.text = config.rotuloHeader
                tvBomba.text = config.tituloHeader
                tvSectionLabel.text = if (config.usaStatusPorItem) "REGISTROS / STATUS POR ITEM" else "REGISTROS RECENTES"

                if (config.usaStatusPorItem) {
                    toggle.visibility = View.GONE
                    return
                }

                toggle.visibility = View.VISIBLE
                toggle.setOnCheckedChangeListener(null)
                toggle.textOn = statusBomba
                toggle.textOff = statusBomba
                toggle.isChecked = false
                toggle.text = statusBomba
                toggle.setBackgroundColor(Color.parseColor(corStatus(statusBomba)))
                toggle.setOnClickListener { alternarStatusGlobal() }
            }
        }

        inner class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
            private val cardIcone: CardView = v.findViewById(R.id.cardIcone)
            private val tvLabel: TextView = v.findViewById(R.id.tvItemLabel)
            private val tvSub: TextView = v.findViewById(R.id.tvItemSub)
            private val tvStatusChip: TextView = v.findViewById(R.id.tvStatusChip)
            private val imgCheck: ImageView = v.findViewById(R.id.imgCheck)
            private val imgPreview: ImageView = v.findViewById(R.id.imgPreview)
            private val tvRefazer: TextView = v.findViewById(R.id.tvRefazer)

            fun bind(idx: Int) {
                val item = itens[idx]
                tvLabel.text = item.label
                cardIcone.setCardBackgroundColor(Color.parseColor(item.corIcone))

                if (item.statusDisponiveis.isNotEmpty()) {
                    tvStatusChip.visibility = View.VISIBLE
                    tvStatusChip.text = item.statusAtual
                    tvStatusChip.background = GradientDrawable().apply {
                        cornerRadius = 18f
                        setColor(Color.parseColor(corStatus(item.statusAtual)))
                    }
                    tvStatusChip.setTextColor(Color.WHITE)
                    tvStatusChip.setOnClickListener { alternarStatusItem(idx) }
                } else {
                    tvStatusChip.visibility = View.GONE
                    tvStatusChip.setOnClickListener(null)
                }

                if (item.concluido && item.bitmap != null) {
                    val bmpPreview = item.bitmap!!
                    imgPreview.post {
                        ImageHelper.aplicarNoImageView(
                            bmpPreview,
                            imgPreview,
                            (imgPreview.width.takeIf { it > 0 } ?: itemView.width).coerceAtLeast(1)
                        )
                    }
                    imgPreview.visibility = View.VISIBLE
                    imgCheck.visibility = View.VISIBLE
                    tvRefazer.visibility = View.VISIBLE
                    tvSub.text = item.dataHora
                    tvSub.setTextColor(Color.parseColor("#22C55E"))
                    itemView.setBackgroundColor(Color.parseColor("#0D1F0D"))
                } else {
                    imgPreview.visibility = View.GONE
                    imgCheck.visibility = View.GONE
                    tvRefazer.visibility = View.GONE
                    tvSub.text = "Toque para fotografar / galeria"
                    tvSub.setTextColor(Color.parseColor("#5A6478"))
                    itemView.setBackgroundColor(Color.TRANSPARENT)
                }

                itemView.setOnClickListener { abrirOrigemFoto(idx) }
                tvRefazer.setOnClickListener { abrirOrigemFoto(idx) }
            }
        }

        inner class FooterVH(v: View) : RecyclerView.ViewHolder(v) {
            private val btnGR: CardView = v.findViewById(R.id.btnGerarRegistro)

            fun bind() {
                val ok = itens.any { it.concluido }
                btnGR.alpha = if (ok) 1f else 0.35f
                btnGR.isEnabled = ok
                btnGR.setOnClickListener { if (ok) gerarImagem() }
            }
        }
    }
}