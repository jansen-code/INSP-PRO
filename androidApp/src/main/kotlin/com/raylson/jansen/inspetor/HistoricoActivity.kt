package com.raylson.jansen.inspetor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

enum class HistoricoMode { NA, DET_01, ARB }

data class RegistroReal(
    val id: String,
    val grupo: String,
    var subtitulo: String,
    var dataHora: String,
    var statusSuperior: String?,
    var statusInferior: String?,
    var valorNA: String?,
    val fotoPath: String?,
    val bitmapEditado: Bitmap? = null
)

sealed class GradeItem {
    data class Card(val nome: String) : GradeItem()
    data class Secao(val titulo: String) : GradeItem()
}

private const val TYPE_CARD  = 0
private const val TYPE_SECAO = 1

class HistoricoActivity : AppCompatActivity() {

    private var currentMode = HistoricoMode.NA
    private val itensDaGrade = mutableListOf<GradeItem>()
    private lateinit var recyclerGrade: RecyclerView
    private lateinit var adapterGrade: QuadradosAdapter
    private lateinit var fabRelogio: View
    private lateinit var tvSelecione: TextView

    private val historicoPrefs by lazy { SecurePrefs.get(this, "historico_prefs") }
    private val leituraPrefs   by lazy { SecurePrefs.get(this, "leituras_flw_hidro") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemToEdit = intent.getStringExtra("EDIT_ITEM")
        if (itemToEdit != null) {
            val emptyView = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
            setContentView(emptyView)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            abrirPreviewDoRegistro(itemToEdit)
            return
        }
        window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(R.layout.activity_historico)

        fabRelogio   = findViewById(R.id.btn_relogio_estado)
        tvSelecione  = findViewById(R.id.tv_selecione)
        recyclerGrade = findViewById(R.id.recycler_quadrados_opcoes)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        
        findViewById<View>(R.id.btnIrParaListaNA).setOnClickListener {
            val intent = Intent(this, ListaNAActivity::class.java)
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.btn_limpar).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Limpar Histórico")
                .setMessage("Tem certeza que deseja apagar todos os registros e fotos? Esta ação não pode ser desfeita.")
                .setPositiveButton("APAGAR") { dialog, _ ->
                    limparHistoricoComFotos()
                    Toast.makeText(this, "Histórico limpo!", Toast.LENGTH_SHORT).show()
                    atualizarGradePorModo()
                    dialog.dismiss()
                }
                .setNegativeButton("CANCELAR") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
        }

        setupBotaoRelogioTouch()
        setupGrid()
        atualizarGradePorModo()
    }
    
    private fun limparHistoricoComFotos() {
        try {
            val dir = java.io.File(filesDir, "historico")
            if (dir.exists()) {
                dir.listFiles()?.forEach { 
                    try {
                        it.delete()
                    } catch (_: Exception) { }
                }
            }
            
            historicoPrefs.edit().putString("registros_json", "[]").apply()
            
            val editorLeitura = leituraPrefs.edit()
            leituraPrefs.all.keys.forEach { chave ->
                editorLeitura.remove(chave)
            }
            editorLeitura.apply()
            
        } catch (e: Exception) {
            android.util.Log.e("HistoricoActivity", "Falha ao limpar histórico criptografado", e)
            try {
                historicoPrefs.edit().putString("registros_json", "[]").apply()
                val fallbackEditor = leituraPrefs.edit()
                leituraPrefs.all.keys.forEach { fallbackEditor.remove(it) }
                fallbackEditor.apply()
            } catch (e2: Exception) {
                android.util.Log.e("HistoricoActivity", "Falha também no fallback de limpeza", e2)
            }
        }
    }
    
    private fun setupBotaoRelogioTouch() {
        fabRelogio.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    aplicarEstadoPressionado(v)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    restaurarEstadoPadrao(v)
                    if (toqueDentroDaView(v, event)) {
                        v.performClick()
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        animarRotacaoHistorico(v)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    restaurarEstadoPadrao(v)
                    true
                }
                else -> false
            }
        }
    }

    private fun aplicarEstadoPressionado(view: View) {
        view.animate()
            .alpha(0.5f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(90)
            .start()
    }

    private fun restaurarEstadoPadrao(view: View) {
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(110)
            .start()
    }

    private fun animarRotacaoHistorico(view: View) {
        currentMode = when (currentMode) {
            HistoricoMode.NA -> HistoricoMode.DET_01
            HistoricoMode.DET_01 -> HistoricoMode.ARB
            HistoricoMode.ARB -> HistoricoMode.NA
        }
        atualizarGradePorModo()

        val animacao = ObjectAnimator.ofFloat(view, View.ROTATION, view.rotation, view.rotation + 360f).apply {
            duration = 420L
            interpolator = LinearInterpolator()
        }

        animacao.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.rotation = 0f
            }
        })

        animacao.start()
    }

    private fun toqueDentroDaView(view: View, event: MotionEvent): Boolean {
        return event.x >= 0f && event.x <= view.width && event.y >= 0f && event.y <= view.height
    }

    private fun setupGrid() {
        adapterGrade = QuadradosAdapter()
        val gridManager = GridLayoutManager(this, 4)
        gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (itensDaGrade.getOrNull(position) is GradeItem.Secao) 4 else 1
        }
        recyclerGrade.layoutManager = gridManager
        recyclerGrade.adapter = adapterGrade
    }

    private fun atualizarGradePorModo() {
        itensDaGrade.clear()
    when (currentMode) {
        HistoricoMode.NA -> {
        tvSelecione.text = "SELECIONE (MODO N.A.)"
        listOf(
            "ARB-01", "DET-01", "DET-02", "DT2-ex", "DET-03", 
            "L.BRUTA", "CP", "CP-ex", 
            "ARB-08", "ARB-09", "ARB-10"
        ).forEach { itensDaGrade.add(GradeItem.Card(it)) }
    }
            HistoricoMode.DET_01 -> {
                tvSelecione.text = "SELECIONE (MODO DET-01)"
                listOf("HM-01", "HM-02", "HM-03", "HM-04", "SIFÕES")
                    .forEach { itensDaGrade.add(GradeItem.Card(it)) }
            }
            HistoricoMode.ARB -> {
                tvSelecione.text = "SELECIONE (MODO GRUPO ARB)"
                itensDaGrade.add(GradeItem.Card("ARB-05"))
                itensDaGrade.add(GradeItem.Card("ARB-06"))

                itensDaGrade.add(GradeItem.Secao("ARB-07"))
                itensDaGrade.add(GradeItem.Card("BA-73"))
                itensDaGrade.add(GradeItem.Card("BA-74"))

                itensDaGrade.add(GradeItem.Secao("ARB-08"))
                itensDaGrade.add(GradeItem.Card("BA-85"))
                itensDaGrade.add(GradeItem.Card("BA-86"))
                itensDaGrade.add(GradeItem.Card("BA-87"))

                itensDaGrade.add(GradeItem.Secao("ARB-09"))
                itensDaGrade.add(GradeItem.Card("ARB-9.1"))
                itensDaGrade.add(GradeItem.Card("ARB-9.2"))
            }
        }
        adapterGrade.notifyDataSetChanged()
    }

    private fun buscarUltimoRegistroReal(itemGrid: String): RegistroReal? {
        val prefs = historicoPrefs
        val raw   = prefs.getString("registros_json", "[]") ?: "[]"

        val subtitulosValidos = mutableListOf<String>()
        var grupoEsperado: String? = null

        when (itemGrid) {
            "ARB-01"  -> { subtitulosValidos.add("ARB-01"); grupoEsperado = "N.A." }
            "DET-01"  -> { 
                if (currentMode == HistoricoMode.NA) {
                    subtitulosValidos.add("LAGOA DE DETENÇÃO 01"); grupoEsperado = "N.A."
                } else {
                    subtitulosValidos.add("HM-01"); grupoEsperado = "DET-01"
                }
            }
            "DET-02"  -> { subtitulosValidos.add("LAGOA DE DETENÇÃO 02"); grupoEsperado = "N.A." }
            "DT2-ex"  -> { subtitulosValidos.add("DET-02 EXTRAVASOR"); subtitulosValidos.add("DO EXTRAVASOR DET-02"); grupoEsperado = "N.A." }
            "DET-03"  -> { subtitulosValidos.add("LAGOA DE DETENÇÃO 03"); grupoEsperado = "N.A." }
            "L.BRUTA" -> { subtitulosValidos.add("LAGOA BRUTA"); grupoEsperado = "N.A." }
            "CP"      -> { subtitulosValidos.add("COOLING POND"); grupoEsperado = "N.A." }
            "CP-ex"   -> { subtitulosValidos.add("COOLING POND EXTRAVASOR"); subtitulosValidos.add("EXTRAVASOR C.P / COOLING POND"); subtitulosValidos.add("EXTRAVASOR C.P"); grupoEsperado = "N.A." }
            "ARB-08"  -> {
                if (currentMode == HistoricoMode.NA) {
                    subtitulosValidos.add("ARB-08"); grupoEsperado = "N.A."
                } else {
                    subtitulosValidos.add("BA-85"); grupoEsperado = "ARB-08"
                }
            }
            "ARB-09"  -> {
                if (currentMode == HistoricoMode.NA) {
                    subtitulosValidos.add("ARB-09"); grupoEsperado = "N.A."
                } else {
                    subtitulosValidos.add("9.1"); grupoEsperado = "ARB-09"
                }
            }
            "ARB-10" -> { subtitulosValidos.add("ARB-10"); grupoEsperado = "N.A."}
            "HM-01" -> { subtitulosValidos.add("HM-01"); grupoEsperado = "DET-01" }
            "HM-02" -> { subtitulosValidos.add("HM-02"); grupoEsperado = "DET-01" }
            "HM-03" -> { subtitulosValidos.add("HM-03"); grupoEsperado = "DET-01" }
            "HM-04" -> { subtitulosValidos.add("HM-04"); grupoEsperado = "DET-01" }
            "SIFÕES" -> { 
                subtitulosValidos.add("SIFÕES")
                subtitulosValidos.add("SIFÃO SUP.")
                subtitulosValidos.add("SIFÃO INF.")
                grupoEsperado = "DET-01" 
            }
            "ARB-05"  -> { subtitulosValidos.add("ARB-05"); grupoEsperado = "ARB-05" }
            "ARB-06"  -> { subtitulosValidos.add("ARB-06"); grupoEsperado = "ARB-06" }
            "BA-73"   -> { subtitulosValidos.add("BA-73"); grupoEsperado = "ARB-07" }
            "BA-74"   -> { subtitulosValidos.add("BA-74"); grupoEsperado = "ARB-07" }
            "BA-85"   -> { subtitulosValidos.add("BA-85"); grupoEsperado = "ARB-08" }
            "BA-86"   -> { subtitulosValidos.add("BA-86"); grupoEsperado = "ARB-08" }
            "BA-87"   -> { subtitulosValidos.add("BA-87"); grupoEsperado = "ARB-08" }
            "ARB-9.1" -> { subtitulosValidos.add("9.1"); grupoEsperado = "ARB-09" }
            "ARB-9.2" -> { subtitulosValidos.add("9.2"); grupoEsperado = "ARB-09" }
            else -> { subtitulosValidos.add(itemGrid) }
        }

        return try {
            val arr = JSONArray(raw)
            for (i in arr.length() - 1 downTo 0) {
                val obj   = arr.getJSONObject(i)
                val sub   = obj.optString("subtitulo", "")
                val grupo = obj.optString("grupo", "")

                val subOk   = subtitulosValidos.any { it.equals(sub, ignoreCase = true) }
                val grupoOk = grupoEsperado == null || grupo.equals(grupoEsperado, ignoreCase = true)

                if (subOk && grupoOk) {
                    return RegistroReal(
                        id             = obj.optString("id", ""),
                        grupo          = grupo,
                        subtitulo      = sub,
                        dataHora       = obj.optString("dataHora", ""),
                        statusSuperior = sanitizarCampo(obj.optString("statusSuperior", null)),
                        statusInferior = sanitizarCampo(obj.optString("statusInferior", null)),
                        valorNA        = sanitizarCampo(obj.optString("valorNA", null)),
                        fotoPath       = sanitizarCampo(obj.optString("fotoPath", null))
                    )
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private fun sanitizarCampo(valor: String?): String? {
        if (valor == null || valor == "null" || valor.isBlank()) return null
        return valor
    }
    
    private fun abrirPreviewDoRegistro(itemSelecionado: String) {
        val registro = buscarUltimoRegistroReal(itemSelecionado) ?: return

        val viewDialog = layoutInflater.inflate(R.layout.dialog_historico_edicao, null)
        
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(viewDialog)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTipo       = viewDialog.findViewById<TextView>(R.id.tvEditTipo)
        val tvSubtitulo  = viewDialog.findViewById<TextView>(R.id.tvEditSubtitulo)
        val tvDataHora   = viewDialog.findViewById<TextView>(R.id.tvEditDataHora)
        val imgFoto      = viewDialog.findViewById<ImageView>(R.id.imgFotoSuperior)
        val imgOverlay   = viewDialog.findViewById<ImageView>(R.id.imgFotoOverlay) 
        val reguaEdicao  = viewDialog.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogHistorico) 
        val tvLabelFoto  = viewDialog.findViewById<TextView>(R.id.tvLabelFotoSup)
        val tvZoom       = viewDialog.findViewById<TextView>(R.id.tvZoomLevel)
        val tvDica: TextView? = null
        val layoutToggles = viewDialog.findViewById<LinearLayout>(R.id.layoutToggles)
        val btnSup       = viewDialog.findViewById<Button>(R.id.btnToggleSuperior)
        val btnInf       = viewDialog.findViewById<Button>(R.id.btnToggleInferior)
        val layoutNA     = viewDialog.findViewById<LinearLayout>(R.id.layoutEditNA)
        val etNA         = viewDialog.findViewById<EditText>(R.id.etEditValorNA)
        
        val btnEditarHora = viewDialog.findViewById<ImageView>(R.id.btnEditarHoraHistorico)
        val horaOriginalDaSessao = registro.dataHora 

        var atualizarTarjaNALive: (() -> Unit)? = null
        var reajustarTarjaNALive: (() -> Unit)? = null

        btnEditarHora.setOnClickListener {
            mostrarDialogEditarDataHora(
                horaAtual = registro.dataHora,
                horaOriginal = horaOriginalDaSessao,
                onConfirmar = { novaHora ->
                    registro.dataHora = novaHora
                    tvDataHora.text = novaHora
                    
                    try {
                        val prefsLeitura = SecurePrefs.get(this@HistoricoActivity, "leituras_flw_hidro")
                        val allLeituras = prefsLeitura.all
                        for ((chave, valor) in allLeituras) {
                            if (valor is String) {
                                val obj = org.json.JSONObject(valor)
                                val est = obj.optString("estacao")
                                val tit = obj.optString("titulo")
                                if (est.equals(registro.grupo, ignoreCase = true) && tit.equals(registro.subtitulo, ignoreCase = true)) {
                                    obj.put("dataHora", novaHora)
                                    prefsLeitura.edit().putString(chave, obj.toString()).apply()
                                    break
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    if (registro.grupo.equals("N.A.", ignoreCase = true)) {
                        atualizarTarjaNALive?.invoke()
                        reajustarTarjaNALive?.invoke()
                    } else {
                        val statusSupLive = when {
                            registro.grupo.equals("DET-01", ignoreCase = true) -> registro.statusSuperior
                            btnSup.visibility == View.VISIBLE -> btnSup.text.toString().trim()
                            else -> registro.statusSuperior
                        }
                        val statusInfLive = when {
                            registro.grupo.equals("DET-01", ignoreCase = true) -> btnInf.text.toString().trim()
                            btnInf.visibility == View.VISIBLE -> btnInf.text.toString().trim()
                            else -> registro.statusInferior
                        }
                        val valorNALive = if (layoutNA.visibility == View.VISIBLE) etNA.text.toString().trim().ifBlank { null } else registro.valorNA
                        
                        val bmpLive = gerarBitmapEditadoHistorico(registro, statusSupLive, statusInfLive, valorNALive)
                        if (bmpLive != null) {
                            imgFoto.setImageBitmap(bmpLive)
                        }
                    }
                    Toast.makeText(this, "✓ Horário atualizado!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        val btnEditarNome  = viewDialog.findViewById<View>(R.id.btnEditarNomeLugar)
        val iconEdit       = viewDialog.findViewById<ImageView>(R.id.iconEditSubtitulo)
        val overlayEdicao  = viewDialog.findViewById<View>(R.id.overlayEdicaoLugar)
        val recyclerOpcoes = viewDialog.findViewById<RecyclerView>(R.id.recyclerOpcoesLugares)

        tvTipo.text      = registro.grupo
        tvSubtitulo.text = registro.subtitulo
        tvDataHora.text  = registro.dataHora

        carregarFoto(registro.fotoPath, imgFoto)
        setupZoomHistorico(imgFoto, tvZoom, tvDica)

        when {
            registro.grupo.equals("DET-01", ignoreCase = true) -> {
                if (registro.subtitulo.contains("SIF", ignoreCase = true)) {
                    tvLabelFoto.text      = "SIFÕES + VAZÃO"
                    tvLabelFoto.visibility = View.VISIBLE
                    layoutToggles.visibility = View.VISIBLE
                    layoutNA.visibility   = View.GONE

                    btnSup.visibility = View.GONE
                    btnInf.visibility = View.VISIBLE
                    btnInf.text = registro.statusInferior ?: "SEM VAZÃO"
                    atualizarCorBotao(btnInf, btnInf.text.toString())
                    btnInf.setOnClickListener {
                        val proximo = ciclarStatus(btnInf.text.toString(), listOf("COM VAZÃO", "SEM VAZÃO"))
                        btnInf.text = proximo
                        atualizarCorBotao(btnInf, proximo)
                    }
                } else {
                    tvLabelFoto.text      = "HIDRÔMETRO + BOMBA"
                    tvLabelFoto.visibility = View.VISIBLE
                    layoutToggles.visibility = View.VISIBLE
                    layoutNA.visibility   = View.GONE

                    btnSup.visibility = View.GONE
                    btnInf.visibility = View.VISIBLE
                    btnInf.text = registro.statusInferior ?: "DESLIGADA"
                    atualizarCorBotao(btnInf, btnInf.text.toString())
                    btnInf.setOnClickListener {
                        val proximo = ciclarStatus(btnInf.text.toString(), listOf("LIGADA", "DESLIGADA"))
                        btnInf.text = proximo
                        atualizarCorBotao(btnInf, proximo)
                    }
                }
            }

            registro.grupo.equals("ARB-08", ignoreCase = true) ||
            registro.grupo.equals("ARB-09", ignoreCase = true) -> {
                tvLabelFoto.text      = "FLOWMETER + VAZÃO"
                tvLabelFoto.visibility = View.VISIBLE
                layoutToggles.visibility = View.VISIBLE
                layoutNA.visibility   = View.GONE

                btnSup.visibility = View.VISIBLE
                btnInf.visibility = View.VISIBLE
                btnSup.text = registro.statusSuperior ?: "LIGADO"
                btnInf.text = registro.statusInferior ?: "SEM VAZÃO"
                atualizarCorBotao(btnSup, btnSup.text.toString())
                atualizarCorBotao(btnInf, btnInf.text.toString())
                btnSup.setOnClickListener {
                    val proximo = ciclarStatus(btnSup.text.toString(), listOf("LIGADO", "ZERADO", "DESLIGADO"))
                    btnSup.text = proximo
                    atualizarCorBotao(btnSup, proximo)
                }
                btnInf.setOnClickListener {
                    val proximo = ciclarStatus(btnInf.text.toString(), listOf("COM VAZÃO", "SEM VAZÃO"))
                    btnInf.text = proximo
                    atualizarCorBotao(btnInf, proximo)
                }
            }

            registro.grupo.equals("ARB-05", ignoreCase = true) ||
            registro.grupo.equals("ARB-06", ignoreCase = true) -> {
                tvLabelFoto.text      = "FLOWMETER"
                tvLabelFoto.visibility = View.VISIBLE
                layoutToggles.visibility = View.VISIBLE
                layoutNA.visibility   = View.GONE

                btnSup.visibility = View.VISIBLE
                btnInf.visibility = View.GONE
                btnSup.text = registro.statusSuperior ?: registro.statusInferior ?: "LIGADO"
                atualizarCorBotao(btnSup, btnSup.text.toString())
                btnSup.setOnClickListener {
                    val proximo = ciclarStatus(btnSup.text.toString(), listOf("LIGADO", "ZERADO", "DESLIGADO"))
                    btnSup.text = proximo
                    atualizarCorBotao(btnSup, proximo)
                }
            }

            registro.grupo.equals("ARB-07", ignoreCase = true) -> {
                tvLabelFoto.text      = "FLOWMETER ${registro.subtitulo}"
                tvLabelFoto.visibility = View.VISIBLE
                layoutToggles.visibility = View.VISIBLE
                layoutNA.visibility   = View.GONE

                btnSup.visibility = View.VISIBLE
                btnInf.visibility = View.GONE
                btnSup.text = registro.statusSuperior ?: "ZERADO"
                atualizarCorBotao(btnSup, btnSup.text.toString())
                btnSup.setOnClickListener {
                    val proximo = ciclarStatus(btnSup.text.toString(), listOf("ZERADO", "LIGADO", "DESLIGADO"))
                    btnSup.text = proximo
                    atualizarCorBotao(btnSup, proximo)
                }
            }

            // ════ AQUI ESTÁ A GAVETA DE TARJA E A RÉGUA DE LUZ ════
            registro.grupo.equals("N.A.", ignoreCase = true) -> {
                tvLabelFoto.visibility   = View.GONE
                layoutToggles.visibility = View.GONE
                iconEdit.visibility = View.VISIBLE
                
                // Gaveta da Luz
                reguaEdicao.visibility = View.VISIBLE
                reguaEdicao.alinharEsquerda = false
                reguaEdicao.onValorMudou = { modo, valor ->
                    FiltroImagemHelper.aplicarFiltroAoVivo(imgFoto, modo, valor)
                }

                val cleanPath = "${registro.fotoPath}.clean"
                if (java.io.File(cleanPath).exists()) {
                    carregarFoto(cleanPath, imgFoto)
                } else {
                    carregarFoto(registro.fotoPath, imgFoto)
                }
                
                // Gaveta da Tarja Animada
                // ═══ CORREÇÃO CRÍTICA #2: isClickable = true fazia o próprio
                // View.onTouchEvent() do imgOverlay "engolir" o ACTION_DOWN
                // automaticamente (todo View clicável consome o DOWN por padrão),
                // mesmo quando o listener abaixo retornava false. Resultado: o toque
                // NUNCA chegava na imgFoto (que fica por baixo), matando o zoom.
                // Mantemos clicável=false — quem decide se consome o toque é
                // exclusivamente a lógica manual do setOnTouchListener abaixo. ═══
                imgOverlay.isClickable = false
                imgOverlay.isFocusable = false
                var isGavetaTarjaFechada = true
                var alturaBarraBitmap = 0f

                // ═══ CORREÇÃO: antes mapeava pela matriz AO VIVO (com o zoom
                // do usuário), então acima de 100% a área de toque da "abinha"
                // ia parar num lugar onde ela não está mais visualmente — por
                // isso travava ao tentar abrir/fechar com zoom aplicado. Agora
                // retorna sempre o retângulo BASE (estático, center-crop em
                // repouso), que é exatamente onde o overlay/tarja é desenhado
                // na tela — pois o overlay tem scaleType próprio (fitCenter) e
                // NUNCA acompanha o zoom da foto de baixo, igual ao N.A. ═══
                fun rectImagemMapeado(): RectF? {
                    val d = imgFoto.drawable ?: return null
                    val iw = d.intrinsicWidth.toFloat().coerceAtLeast(1f)
                    val ih = d.intrinsicHeight.toFloat().coerceAtLeast(1f)
                    val vw = imgFoto.width.toFloat()
                    val vh = imgFoto.height.toFloat()
                    if (vw <= 0f || vh <= 0f) return null
                    val scale = maxOf(vw / iw, vh / ih)
                    val dx = (vw - iw * scale) / 2f
                    val dy = (vh - ih * scale) / 2f
                    return RectF(dx, dy, dx + iw * scale, dy + ih * scale)
                }

                fun calcularOffsetGavetaFechada(): Float {
                    val d = imgFoto.drawable ?: return 0f
                    val hImg = d.intrinsicHeight.toFloat().coerceAtLeast(1f)
                    val rect = rectImagemMapeado() ?: return 0f
                    val scale = rect.height() / hImg
                    return alturaBarraBitmap * scale
                }

                fun aplicarPosicaoGavetaTarja(animar: Boolean) {
                    val alvo = if (isGavetaTarjaFechada) calcularOffsetGavetaFechada() else 0f
                    if (animar) {
                        imgOverlay.animate()
                            .translationY(alvo)
                            .setDuration(300L)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    } else {
                        imgOverlay.translationY = alvo
                    }
                }
                
                reajustarTarjaNALive = {
                    if (imgOverlay.width > 0 && imgOverlay.height > 0) {
                        imgOverlay.imageMatrix = imgFoto.imageMatrix
                        aplicarPosicaoGavetaTarja(false)
                    } else {
                        imgOverlay.post {
                            imgOverlay.imageMatrix = imgFoto.imageMatrix
                            aplicarPosicaoGavetaTarja(false)
                        }
                    }
                }
                
                atualizarTarjaNALive = {
                    val isExtravasor = registro.subtitulo.contains("EXTRAVASOR", ignoreCase = true)
                    val valorDigitado = etNA.text.toString().trim().ifBlank { null }
                    
                    val w = imgFoto.drawable?.intrinsicWidth ?: 2160
                    val h = imgFoto.drawable?.intrinsicHeight ?: 2880
                    
                    if (w > 0 && h > 0) {
                        val bmpOverlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = Canvas(bmpOverlay)
                        
                        val data = if (isExtravasor) {
                            listOf("pin" to registro.subtitulo, "relogio" to registro.dataHora)
                        } else {
                            val textoNA = if (valorDigitado.isNullOrBlank()) "N.A: "
                                          else "N.A: ${if (valorDigitado.endsWith("m", true)) valorDigitado else "${valorDigitado}m"}"
                            listOf("pin" to registro.subtitulo, "relogio" to registro.dataHora, "hidro" to textoNA)
                        }
                        
                        alturaBarraBitmap = ImageHelper.drawOverlayKV(
                            c,
                            0f,
                            h.toFloat(),
                            w.toFloat(),
                            data,
                            deslocarDireita = true,
                            isGavetaFechada = isGavetaTarjaFechada
                        )
                        imgOverlay.setImageBitmap(bmpOverlay)
                        imgOverlay.imageMatrix = imgFoto.imageMatrix
                    }
                }

                etNA.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        atualizarTarjaNALive?.invoke()
                        reajustarTarjaNALive?.invoke()
                    }
                })

                imgFoto.viewTreeObserver.addOnDrawListener {
                    if (imgOverlay.imageMatrix != imgFoto.imageMatrix) {
                        imgOverlay.imageMatrix = imgFoto.imageMatrix
                    }
                }
                imgFoto.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    reajustarTarjaNALive?.invoke()
                }

                imgFoto.post {
                    atualizarTarjaNALive?.invoke()
                    reajustarTarjaNALive?.invoke()
                }

                // Define que o overlay NÃO consome toques por padrão, 
                // permitindo que toques de zoom cheguem à imgFoto.
                imgOverlay.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        val rect = rectImagemMapeado()
                        if (rect != null) {
                            // Verifica o toque na "abinha" (área específica)
                            val naAbinha = event.x > (rect.left + rect.width() * 0.70f) &&
                                           event.y > (rect.top + rect.height() * 0.82f)
                            
                            if (naAbinha) {
                                isGavetaTarjaFechada = !isGavetaTarjaFechada
                                atualizarTarjaNALive?.invoke()
                                aplicarPosicaoGavetaTarja(true)
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                return@setOnTouchListener true // Consome o clique da abinha
                            }
                        }
                    }
                    // Retornar FALSE aqui é o segredo: o toque passa direto 
                    // para a imgFoto (que está atrás) para que o Zoom funcione.
                    false
                }

                
                // Edição de Localização
                val opcoesLugares = listOf("ARB-01", "LAGOA BRUTA", "LAGOA DE DETENÇÃO 01", "LAGOA DE DETENÇÃO 02", "DET-02 EXTRAVASOR", "LAGOA DE DETENÇÃO 03", "COOLING POND", "COOLING POND EXTRAVASOR", "ARB-08", "ARB-09", "ARB-10")
                recyclerOpcoes.layoutManager = GridLayoutManager(this, 2)
                recyclerOpcoes.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    inner class OpcaoVH(v: View) : RecyclerView.ViewHolder(v) {
                        val tv: TextView = v.findViewById(android.R.id.text1)
                    }
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
                        return OpcaoVH(v)
                    }
                    override fun getItemCount() = opcoesLugares.size
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                        val opcao = opcoesLugares[position]
                        val tv = (holder as OpcaoVH).tv
                        tv.text = opcao
                        tv.setTextColor(Color.parseColor("#1E293B"))
                        tv.textSize = 13f
                        tv.setPadding((12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(), (12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt())
                        holder.itemView.setOnClickListener {
                            registro.subtitulo = opcao
                            tvSubtitulo.text   = opcao
                            overlayEdicao.visibility = View.GONE
                            atualizarTarjaNALive?.invoke()
                            reajustarTarjaNALive?.invoke()
                        }
                    }
                }
                
                btnEditarNome.setOnClickListener { overlayEdicao.visibility = View.VISIBLE }
                overlayEdicao.setOnClickListener { overlayEdicao.visibility = View.GONE }
                
                val isExtravasor = registro.subtitulo.contains("EXTRAVASOR", ignoreCase = true)
                if (isExtravasor) {
                    layoutNA.visibility = View.GONE
                } else {
                    layoutNA.visibility = View.VISIBLE
                    etNA.setText(registro.valorNA ?: "")
                    aplicarMascaraNA(etNA)
                }
            }
            else -> {
                tvLabelFoto.visibility   = View.GONE
                layoutToggles.visibility = View.GONE
                layoutNA.visibility      = View.GONE
            }
        }

        viewDialog.findViewById<View>(R.id.btnFecharDialog).setOnClickListener {
            esconderTeclado(etNA)
            dialog.dismiss()
        }

        fun prepararFusaoFiltroNA(): Bitmap? {
            if (!registro.grupo.equals("N.A.", ignoreCase = true)) return null
            val valorModoAtivo = when (reguaEdicao.modoAtual) {
                ReguaVerticalView.Modo.BRILHO -> reguaEdicao.valorBrilho
                ReguaVerticalView.Modo.NITIDEZ -> reguaEdicao.valorNitidez
                ReguaVerticalView.Modo.VETORIZACAO -> reguaEdicao.valorVetorizacao
            }
            val bmpBase = (imgFoto.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return null
            
            // ═══ CORREÇÃO: Em vez de pegar a tarja visual da tela (com abinha), 
            // geramos uma tarja limpa e oficial para o arquivo final. ═══
            val w = bmpBase.width
            val h = bmpBase.height
            val bmpOverClean = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmpOverClean)
            
            val isExtravasor = registro.subtitulo.contains("EXTRAVASOR", ignoreCase = true)
            val valorDigitado = etNA.text.toString().trim().ifBlank { null }
            val dataHoraTexto = tvDataHora.text.toString()

            val data = if (isExtravasor) {
                listOf("pin" to registro.subtitulo, "relogio" to dataHoraTexto)
            } else {
                val textoNA = if (valorDigitado.isNullOrBlank()) "N.A: "
                              else "N.A: ${if (valorDigitado.endsWith("m", true)) valorDigitado else "${valorDigitado}m"}"
                listOf("pin" to registro.subtitulo, "relogio" to dataHoraTexto, "hidro" to textoNA)
            }
            
            ImageHelper.drawOverlayKV(
                c,
                0f,
                h.toFloat(),
                w.toFloat(),
                data,
                deslocarDireita = false, // <-- FALSE indica que é imagem final, ocultando a abinha!
                isGavetaFechada = false
            )
            
            return FiltroImagemHelper.fundirCamadasParaSalvar(
                bmpBase, bmpOverClean, reguaEdicao.modoAtual, valorModoAtivo, reguaEdicao.valorVetorizacao
            )
        }

        viewDialog.findViewById<View>(R.id.btnSalvarBaixar).setOnClickListener {
            val statusSupEditado = when {
                registro.grupo.equals("DET-01", ignoreCase = true) -> registro.statusSuperior
                btnSup.visibility == View.VISIBLE -> btnSup.text.toString().trim()
                else -> registro.statusSuperior
            }
            val statusInfEditado = when {
                registro.grupo.equals("DET-01", ignoreCase = true) -> btnInf.text.toString().trim()
                btnInf.visibility == View.VISIBLE -> btnInf.text.toString().trim()
                else -> registro.statusInferior
            }
            val valorNAEditado = if (layoutNA.visibility == View.VISIBLE) etNA.text.toString().trim().ifBlank { null } else registro.valorNA

            val bmpFundidoForcado = prepararFusaoFiltroNA()

            val registroAtualizado = persistirEdicaoRegistro(registro, statusSupEditado, statusInfEditado, valorNAEditado, bmpFundidoForcado)
            if (registroAtualizado != null) {
                salvarBitmapNaGaleria(registroAtualizado.bitmapEditado, "${registroAtualizado.grupo}_${registroAtualizado.subtitulo}")
                dialog.dismiss()
            }
        }

        viewDialog.findViewById<View>(R.id.btnCompartilhar).setOnClickListener {
            val statusSupEditado = when {
                registro.grupo.equals("DET-01", ignoreCase = true) -> registro.statusSuperior
                btnSup.visibility == View.VISIBLE -> btnSup.text.toString().trim()
                else -> registro.statusSuperior
            }
            val statusInfEditado = when {
                registro.grupo.equals("DET-01", ignoreCase = true) -> btnInf.text.toString().trim()
                btnInf.visibility == View.VISIBLE -> btnInf.text.toString().trim()
                else -> registro.statusInferior
            }
            val valorNAEditado = if (layoutNA.visibility == View.VISIBLE) etNA.text.toString().trim().ifBlank { null } else registro.valorNA

            val bmpFundidoForcado = prepararFusaoFiltroNA()

            val registroAtualizado = persistirEdicaoRegistro(registro, statusSupEditado, statusInfEditado, valorNAEditado, bmpFundidoForcado)
            if (registroAtualizado != null) {
                compartilharBitmap(registroAtualizado.bitmapEditado)
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener { if (intent.getStringExtra("EDIT_ITEM") != null) finish() }
        dialog.show()

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }

        val scrollHistorico = viewDialog.findViewById<ScrollView>(R.id.scrollDialogHistorico)
        if (scrollHistorico != null) {
            var tecladoAbertoHistorico = false
            var alturaBaseDialogHistorico = 0
            val limiarTecladoHistorico = (150 * resources.displayMetrics.density).toInt()
            
            val globalLayoutListenerHistorico = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val alturaAtual = scrollHistorico.height
                if (alturaAtual > 0) {
                    if (alturaBaseDialogHistorico == 0) {
                        alturaBaseDialogHistorico = alturaAtual
                    } else {
                        val diferenca = alturaBaseDialogHistorico - alturaAtual
                        val agoraAberto = diferenca > limiarTecladoHistorico
                        if (agoraAberto != tecladoAbertoHistorico) {
                            tecladoAbertoHistorico = agoraAberto
                            val conteudo = scrollHistorico.getChildAt(0)
                            if (agoraAberto) {
                                scrollHistorico.post { scrollSuaveRapido(scrollHistorico, conteudo.height) }
                            } else {
                                scrollHistorico.post { scrollSuaveRapido(scrollHistorico, 0) }
                            }
                        } else if (!agoraAberto) {
                            alturaBaseDialogHistorico = alturaAtual
                        }
                    }
                }
                reajustarTarjaNALive?.invoke()
            }
            scrollHistorico.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListenerHistorico)
            dialog.setOnDismissListener {
                scrollHistorico.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListenerHistorico)
            }
        }
    }


    private fun carregarFoto(fotoPath: String?, imgView: ImageView) {
        if (fotoPath.isNullOrBlank()) {
            imgView.setImageResource(android.R.color.darker_gray)
            return
        }
        try {
            val arquivo = java.io.File(fotoPath)
            if (!arquivo.exists()) {
                imgView.setImageResource(android.R.color.darker_gray)
                return
            }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(fotoPath, opts)

            var sample = 1
            while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 2560) sample *= 2

            val bmp = BitmapFactory.decodeFile(fotoPath, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp != null) imgView.setImageBitmap(bmp)
            else imgView.setImageResource(android.R.color.darker_gray)
        } catch (e: Exception) {
            imgView.setImageResource(android.R.color.darker_gray)
        }
    }

    private fun setupZoomHistorico(iv: ImageView, tvZoomLevel: TextView?, tvDica: TextView?) {
        iv.isClickable = true // Garante que a imagem está pronta para aceitar toques de zoom
        iv.scaleType = ImageView.ScaleType.MATRIX
        val matrix = Matrix()
        val baseMatrix = Matrix()
        var minScale = 1f
        val maxMultiplier = 6f

        fun currentScale(): Float { val v = FloatArray(9); matrix.getValues(v); return v[Matrix.MSCALE_X] }
        fun baseScale(): Float { val v = FloatArray(9); baseMatrix.getValues(v); return v[Matrix.MSCALE_X] }
        fun atualizarHud() {
            val base = baseScale()
            val pct = if (base > 0f) ((currentScale() / base) * 100f).toInt() else 100
            tvZoomLevel?.text = "${pct}%"
        }
        fun aplicarFitCenter() {
            val d = iv.drawable ?: return
            val vw = iv.width.toFloat(); val vh = iv.height.toFloat()
            if (vw <= 0f || vh <= 0f) return
            val iw = d.intrinsicWidth.toFloat(); val ih = d.intrinsicHeight.toFloat()
            if (iw <= 0f || ih <= 0f) return

            baseMatrix.reset()
            // ═══ Igual ao diálogo N.A.: maxOf = CENTER CROP, preenche o frame
            // por completo sem sobrar vão/borda nas laterais ou topo/base. ═══
            val s = maxOf(vw / iw, vh / ih)
            val dx = (vw - iw * s) / 2f
            val dy = (vh - ih * s) / 2f
            baseMatrix.postScale(s, s)
            baseMatrix.postTranslate(dx, dy)

            matrix.set(baseMatrix)
            iv.imageMatrix = matrix
            minScale = s
            atualizarHud()
        }
        fun corrigirLimites() {
            val d = iv.drawable ?: return
            val vw = iv.width.toFloat(); val vh = iv.height.toFloat()
            val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
            matrix.mapRect(rect)

            var dx = 0f; var dy = 0f
            dx = if (rect.width() <= vw) (vw - rect.width()) / 2f - rect.left
                 else if (rect.left > 0f) -rect.left
                 else if (rect.right < vw) vw - rect.right
                 else 0f

            dy = if (rect.height() <= vh) (vh - rect.height()) / 2f - rect.top
                 else if (rect.top > 0f) -rect.top
                 else if (rect.bottom < vh) vh - rect.bottom
                 else 0f

            if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
        }

        // ═══ CORREÇÃO CRÍTICA #3 — a mais grave: este listener antes NUNCA se
        // autorremovia e, pior, a condição "!houveMudancaRealDeTamanho" fazia
        // ele chamar aplicarFitCenter() (resetando o zoom pro padrão) toda vez
        // que QUALQUER recomposição de layout ocorria no diálogo — abrir o
        // teclado, atualizar a tarja, etc. — mesmo sem o usuário ter tocado na
        // imagem. Na prática, o zoom "funcionava" por uma fração de segundo e
        // era resetado sozinho. Agora é idêntico ao N.A.: ajusta o encaixe
        // UMA VEZ no primeiro layout válido e nunca mais mexe sozinho. ═══
        iv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                if (iv.drawable != null && iv.width > 0 && iv.height > 0) {
                    aplicarFitCenter()
                    iv.removeOnLayoutChangeListener(this)
                }
            }
        })
        iv.post { if (iv.drawable != null && iv.width > 0 && iv.height > 0) aplicarFitCenter() }

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val atual = currentScale()
                val limMin = minScale
                val limMax = minScale * maxMultiplier
                var fator = detector.scaleFactor
                val alvo = (atual * fator).coerceIn(limMin, limMax)
                fator = if (atual > 0f) alvo / atual else 1f

                matrix.postScale(fator, fator, detector.focusX, detector.focusY)
                corrigirLimites()
                iv.imageMatrix = matrix
                atualizarHud()
                tvDica?.visibility = View.GONE
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val atual = currentScale()
                val alvo = if (atual > minScale * 1.2f) minScale else minScale * 3f
                val fator = if (atual > 0f) alvo / atual else 1f

                matrix.postScale(fator, fator, e.x, e.y)
                corrigirLimites()
                iv.imageMatrix = matrix
                atualizarHud()
                tvDica?.visibility = View.GONE
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                tvDica?.visibility = View.GONE
                return true
            }
        })

        var lastX = 0f; var lastY = 0f
        iv.setOnTouchListener { v, event ->
            // ═══ CORREÇÃO CRÍTICA: sem isto, o ScrollView pai (scrollDialogHistorico)
            // intercepta o gesto de arrastar/pinçar assim que detecta um movimento
            // vertical, "roubando" o toque antes que o zoom consiga processá-lo.
            // É exatamente o mesmo requestDisallowInterceptTouchEvent usado em
            // setupZoom() do diálogo N.A. — aqui estava faltando. ═══
            v.parent?.requestDisallowInterceptTouchEvent(true)

            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && currentScale() > minScale * 1.01f) {
                        matrix.postTranslate(event.x - lastX, event.y - lastY)
                        corrigirLimites()
                        iv.imageMatrix = matrix
                    }
                    lastX = event.x; lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    v.performClick()
                }
            }
            true
        }
    }
    
    private fun ciclarStatus(atual: String, opcoes: List<String>): String {
        val idx = opcoes.indexOfFirst { it.equals(atual, ignoreCase = true) }
        return if (idx == -1) opcoes[0] else opcoes[(idx + 1) % opcoes.size]
    }

    private fun atualizarCorBotao(btn: Button, status: String) {
        val cor = when (status.uppercase()) {
            "LIGADO", "LIGADA", "COM VAZÃO" -> "#22C55E"
            "ZERADO"                        -> "#F59E0B"
            else                            -> "#EF4444"
        }
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(cor))
    }

    private fun aplicarMascaraNA(et: EditText) {
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        et.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            private var prevDigitLen = 0
            private var removendoSufixo = false

            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) {
                prevDigitLen = s?.count { it.isDigit() } ?: 0
                removendoSufixo = cnt == 1 && after == 0 && s != null && st in s.indices && (s[st] == '.' || s[st] == 'm' || s[st] == 'M')
            }

            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true

                var digits = s.toString().filter { it.isDigit() }
                val apagando = digits.length < prevDigitLen || removendoSufixo
                if (removendoSufixo && digits.isNotEmpty()) digits = digits.dropLast(1)
                if (digits.length > 4) digits = digits.take(4)

                val formatted = when {
                    digits.length >= 4 -> "${digits[0]}${digits[1]}.${digits[2]}${digits[3]}m"
                    digits.length == 3 -> "${digits[0]}${digits[1]}.${digits[2]}"
                    digits.length == 2 -> if (!apagando) "${digits[0]}${digits[1]}." else "${digits[0]}${digits[1]}"
                    digits.length == 1 -> digits
                    else -> ""
                }

                if (formatted != s.toString()) {
                    et.setText(formatted)
                    et.setSelection(formatted.length)
                }
                isEditing = false
            }
        })
    }

    private fun esconderTeclado(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // ═══ Idêntico ao usado no diálogo N.A. (DashboardActivity): scroll rápido
    // e suave (190ms, curva fast-out-slow-in) ao abrir/fechar o teclado, no
    // lugar do smoothScrollTo() padrão do Android que pode parecer atrasado. ═══
    private var scrollAnimatorAtivoHistorico: android.animation.ValueAnimator? = null
    private fun scrollSuaveRapido(scrollView: ScrollView, destinoY: Int) {
        scrollAnimatorAtivoHistorico?.cancel()
        val origemY = scrollView.scrollY
        if (origemY == destinoY) return
        scrollAnimatorAtivoHistorico = android.animation.ValueAnimator.ofInt(origemY, destinoY).apply {
            duration = 190L
            interpolator = android.view.animation.PathInterpolator(0.3f, 0f, 0.15f, 1f)
            addUpdateListener { anim -> scrollView.scrollTo(0, anim.animatedValue as Int) }
            start()
        }
    }

    private fun persistirEdicaoRegistro(registro: RegistroReal, statusSuperior: String?, statusInferior: String?, valorNA: String?, bitmapForcado: Bitmap? = null): RegistroReal? {
        val prefs = historicoPrefs
        val arr = JSONArray(prefs.getString("registros_json", "[]") ?: "[]")
        
        var subtituloSalvo = registro.subtitulo
        var dataHoraSalva = registro.dataHora
        
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == registro.id) {
                subtituloSalvo = obj.optString("subtitulo", registro.subtitulo)
                dataHoraSalva = obj.optString("dataHora", registro.dataHora)
                break
            }
        }

        val houveEdicao = statusSuperior  != registro.statusSuperior  ||
                          statusInferior  != registro.statusInferior  ||
                          valorNA         != registro.valorNA         ||
                          registro.subtitulo != subtituloSalvo        ||
                          registro.dataHora  != dataHoraSalva         ||
                          bitmapForcado != null

        if (!houveEdicao) {
            val path = registro.fotoPath
            val bmpAtual = registro.bitmapEditado
                ?: path?.let { BitmapFactory.decodeFile(it) }
                ?: return null
            return registro.copy(bitmapEditado = bmpAtual)
        }

        val bitmapEditado = bitmapForcado ?: gerarBitmapEditadoHistorico(registro, statusSuperior, statusInferior, valorNA) ?: return null

        if (!salvarBitmapNoArquivo(registro.fotoPath, bitmapEditado)) return null

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == registro.id) {
                obj.put("statusSuperior", statusSuperior ?: JSONObject.NULL)
                obj.put("statusInferior", statusInferior ?: JSONObject.NULL)
                obj.put("valorNA",        valorNA        ?: JSONObject.NULL)
                obj.put("subtitulo",      registro.subtitulo)
                obj.put("dataHora",       registro.dataHora)
                break
            }
        }
        prefs.edit().putString("registros_json", arr.toString()).apply()

        return registro.copy(
            statusSuperior = statusSuperior,
            statusInferior = statusInferior,
            valorNA        = valorNA,
            bitmapEditado  = bitmapEditado
        )
    }

    private fun caminhoCleanOriginal(fotoPath: String): String = "$fotoPath.clean"
    private fun caminhoBackupOriginal(fotoPath: String): String = "$fotoPath.orig"

    private fun garantirBackupOriginal(fotoPath: String) {
        val backup = java.io.File(caminhoBackupOriginal(fotoPath))
        if (backup.exists()) return
        val atual = java.io.File(fotoPath)
        if (!atual.exists()) return
        try {
            atual.copyTo(backup, overwrite = false)
        } catch (_: Exception) {}
    }

    private fun gerarBitmapEditadoHistorico(registro: RegistroReal, statusSuperior: String?, statusInferior: String?, valorNA: String?): Bitmap? {
        val path = registro.fotoPath ?: return null

        val cleanPath  = caminhoCleanOriginal(path)
        val backupPath = caminhoBackupOriginal(path)
        val temClean   = java.io.File(cleanPath).exists()

        if (!temClean) garantirBackupOriginal(path)

        val pathParaLer = when {
            temClean                                  -> cleanPath
            java.io.File(backupPath).exists()         -> backupPath
            else                                      -> path
        }

        val original = BitmapFactory.decodeFile(pathParaLer) ?: return null
        val editado = original.copy(Bitmap.Config.ARGB_8888, true)
        if (editado != original) original.recycle()

        val c = Canvas(editado)
        val largura = editado.width.toFloat()
        val altura = editado.height.toFloat()

        when {
            registro.grupo.equals("DET-01", ignoreCase = true) -> {
                if (registro.subtitulo.contains("SIF", ignoreCase = true)) {
                    val statusInf = statusInferior ?: "SEM VAZÃO"
                    ImageHelper.drawOverlayKV(c, 0f, altura, largura, listOf(
                        "pin" to "DET-01",
                        "hidro" to "SIFÕES",
                        "relogio" to registro.dataHora,
                        "status" to statusInf
                    ), mapOf("status" to corTextoStatusHistorico(statusInf)))
                } else {
                    val statusInf = statusInferior ?: "DESLIGADA"
                    ImageHelper.drawOverlayKV(c, 0f, altura, largura, listOf(
                        "raio" to montarTituloBombaHistorico(registro.subtitulo),
                        "relogio" to registro.dataHora,
                        "status" to statusInf
                    ), mapOf("status" to corTextoStatusHistorico(statusInf)))
                }
            }

            registro.grupo.equals("ARB-05", ignoreCase = true) ||
            registro.grupo.equals("ARB-06", ignoreCase = true) ||
            registro.grupo.equals("ARB-07", ignoreCase = true) -> {
                val statusSup = statusSuperior ?: "LIGADO"
                ImageHelper.drawOverlayKV(c, 0f, altura, largura, listOf(
                    "hidro" to montarTituloFlowHistorico(registro),
                    "relogio" to registro.dataHora,
                    "status" to statusSup
                ), mapOf("status" to corTextoStatusHistorico(statusSup)))
            }

            registro.grupo.equals("N.A.", ignoreCase = true) -> {
                val isExtravasor = registro.subtitulo.contains("EXTRAVASOR", ignoreCase = true)
                val data = if (isExtravasor) {
                    listOf("pin" to registro.subtitulo, "relogio" to registro.dataHora)
                } else {
                    val textoNA = if (valorNA.isNullOrBlank()) "N.A: "
                                  else "N.A: ${if (valorNA.endsWith("m", true)) valorNA else "${valorNA}m"}"
                    listOf("pin" to registro.subtitulo, "relogio" to registro.dataHora, "hidro" to textoNA)
                }
                ImageHelper.drawOverlayKV(c, 0f, altura, largura, data)
            }
        }
        return editado
    }

    private fun montarTituloBombaHistorico(subtitulo: String): String {
        val numero = subtitulo.filter { it.isDigit() }
        return if (numero.isBlank()) "BOMBA" else "BOMBA-${numero.takeLast(2).padStart(2, '0')}"
    }

    private fun montarTituloFlowHistorico(registro: RegistroReal): String {
        return when {
            registro.grupo.equals("ARB-05", ignoreCase = true) -> "FLOWMETER ARB-05"
            registro.grupo.equals("ARB-06", ignoreCase = true) -> "FLOWMETER ARB-06"
            registro.grupo.equals("ARB-07", ignoreCase = true) -> "FLOWMETER ARB-07 ${registro.subtitulo}"
            registro.grupo.equals("ARB-08", ignoreCase = true) -> "FLOWMETER ${registro.subtitulo}"
            registro.grupo.equals("ARB-09", ignoreCase = true) -> "FLOWMETER ${registro.subtitulo}"
            else -> registro.subtitulo
        }
    }

    private fun corTextoStatusHistorico(status: String): String {
        return when (status.uppercase()) {
            "LIGADO", "LIGADA", "COM VAZÃO" -> "#22C55E"
            "ZERADO" -> "#F59E0B"
            else -> "#EF4444"
        }
    }

    private fun salvarBitmapNoArquivo(fotoPath: String?, bmp: Bitmap): Boolean {
        if (fotoPath.isNullOrBlank()) return false
        return try {
            val arquivo = java.io.File(fotoPath)
            arquivo.parentFile?.mkdirs()
            java.io.FileOutputStream(arquivo, false).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            true
        } catch (e: Exception) { false }
    }

    private fun salvarBitmapNaGaleria(bmp: Bitmap?, nomeBase: String) {
        if (bmp == null) {
            Toast.makeText(this, "Este registro não possui imagem válida.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val nomeArquivo = "${nomeBase.replace(".", "").replace(" ", "_")}_HIST_${System.currentTimeMillis()}.jpg"
            val out: java.io.OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, nomeArquivo)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/INSPETOR")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { contentResolver.openOutputStream(it) }
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "INSPETOR")
                if (!dir.exists()) dir.mkdirs()
                java.io.FileOutputStream(java.io.File(dir, nomeArquivo))
            }
            out?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Toast.makeText(this, "✓ Imagem salva em Galeria/INSPETOR", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Erro ao salvar a imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compartilharBitmap(bmp: Bitmap?) {
        if (bmp == null) {
            Toast.makeText(this, "Este registro não possui imagem válida.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = java.io.File(cacheDir, "historico_editado_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(contentResolver, "Registro do Histórico", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar registro"))
        } catch (_: Exception) {
            Toast.makeText(this, "Erro ao compartilhar a imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    inner class QuadradosAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        // ═══ TRAVA DE TOQUE RÁPIDO (DEBOUNCE) ═══
        private var tempoUltimoClique: Long = 0

        inner class CardViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val textNome: TextView = v.findViewById(R.id.tvNomeQuadrado)
            val tvStatus: TextView = v.findViewById(R.id.tvStatusLeitura)
            val imgIcone: ImageView = v.findViewById(R.id.imgIconeQuadrado)
        }
        inner class SecaoViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val textSecao: TextView = v.findViewById(R.id.tvNomeSecao)
        }

        override fun getItemViewType(position: Int): Int =
            when (itensDaGrade[position]) {
                is GradeItem.Card  -> TYPE_CARD
                is GradeItem.Secao -> TYPE_SECAO
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_SECAO) {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_secao_historico, parent, false)
                SecaoViewHolder(v)
            } else {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_quadrado_filtro, parent, false)
                CardViewHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = itensDaGrade[position]) {
                is GradeItem.Card -> {
                    val h = holder as CardViewHolder
                    h.textNome.text = item.nome

                    if (currentMode == HistoricoMode.NA) {
                        h.imgIcone.setImageResource(R.drawable.ic_regua_outline)
                        h.imgIcone.setPadding(0, 0, 0, 0)
                    } else if (item.nome == "SIFÕES") {
                        h.imgIcone.setImageResource(R.drawable.ic_raio)
                        val p = (8 * h.itemView.context.resources.displayMetrics.density).toInt()
                        h.imgIcone.setPadding(p, p, p, p)
                    } else {
                        h.imgIcone.setImageResource(R.drawable.ic_flowmeter)
                        h.imgIcone.setPadding(0, 0, 0, 0)
                    }

                    val badge = h.itemView.findViewById<View>(R.id.badgeNotificacao)

                    val registro = buscarUltimoRegistroReal(item.nome)
                    if (registro != null) {
                        h.tvStatus.text = "Leitura registrada"
                        h.tvStatus.setTextColor(Color.parseColor("#475569"))
                        badge?.visibility = View.VISIBLE
                    } else {
                        h.tvStatus.text = "Aguardando leitura"
                        h.tvStatus.setTextColor(Color.parseColor("#94A3B8"))
                        badge?.visibility = View.GONE
                    }

                    val cardClick = h.itemView.findViewById<View>(R.id.cardQuadrado) ?: h.itemView
                    cardClick.setOnClickListener { 
                        // ═══ VERIFICAÇÃO DO TEMPO DE CLIQUE ═══
                        val tempoAtual = System.currentTimeMillis()
                        if (tempoAtual - tempoUltimoClique > 500) { // Trava de 500ms
                            tempoUltimoClique = tempoAtual
                            if (registro == null) {
                                avisarVazio(cardClick)
                            } else {
                                abrirPreviewDoRegistro(item.nome)
                            }
                        }
                    }
                }
                is GradeItem.Secao -> {
                    (holder as SecaoViewHolder).textSecao.text = item.titulo
                }
            }
        }

        override fun getItemCount(): Int = itensDaGrade.size
    }

    
    private fun avisarVazio(view: android.view.View) {
        val anim = android.view.animation.TranslateAnimation(-15f, 15f, 0f, 0f)
        anim.duration = 40
        anim.repeatMode = android.view.animation.Animation.REVERSE
        anim.repeatCount = 5
        view.startAnimation(anim)

        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
    
    private fun mostrarDialogEditarDataHora(horaAtual: String, horaOriginal: String, onConfirmar: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_editar_data_hora, null)
        val d = android.app.AlertDialog.Builder(this).setView(view).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etData = view.findViewById<EditText>(R.id.etEditarData)
        val etHora = view.findViewById<EditText>(R.id.etEditarHora)

        fun partesDe(texto: String): Pair<String, String> {
            val partes = texto.split("//").map { it.trim() }
            val data = partes.getOrNull(0) ?: ""
            val hora = (partes.getOrNull(1) ?: "").removeSuffix("h").trim()
            return data to hora
        }

        val (dataInicial, horaInicial) = partesDe(horaAtual)
        etData.setText(dataInicial)
        etHora.setText(horaInicial)

        aplicarMascaraData(etData)
        aplicarMascaraHora(etHora)

        view.findViewById<ImageView>(R.id.btnRestaurarHoraOriginal).setOnClickListener {
            val (dataOrig, horaOrig) = partesDe(horaOriginal)
            etData.setText(dataOrig)
            etData.setSelection(etData.text.length)
            etHora.setText(horaOrig)
            etHora.setSelection(etHora.text.length)
            Toast.makeText(this, "Horário original restaurado", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnCancelarHora).setOnClickListener { d.dismiss() }

        view.findViewById<Button>(R.id.btnSalvarHora).setOnClickListener {
            val dataDigitada = etData.text.toString().trim()
            val horaDigitada = etHora.text.toString().trim()

            if (dataDigitada.length != 10 || horaDigitada.length != 5) {
                Toast.makeText(this, "Preencha data (DD.MM.AAAA) e hora (HH:mm) completas.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val novaHora = "$dataDigitada // ${horaDigitada}h"
            onConfirmar(novaHora)
            esconderTeclado(view)
            d.dismiss()
        }
        d.show()
    }

    private fun aplicarMascaraData(et: EditText) {
        et.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                val digits = s.toString().filter { it.isDigit() }.take(8)
                val sb = StringBuilder()
                for (i in digits.indices) {
                    sb.append(digits[i])
                    if ((i == 1 || i == 3) && i != digits.lastIndex) sb.append('.')
                }
                val formatted = sb.toString()
                if (formatted != s.toString()) {
                    et.setText(formatted)
                    et.setSelection(formatted.length)
                }
                isEditing = false
            }
        })
    }

    private fun aplicarMascaraHora(et: EditText) {
        et.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                val digits = s.toString().filter { it.isDigit() }.take(4)
                val sb = StringBuilder()
                for (i in digits.indices) {
                    sb.append(digits[i])
                    if (i == 1 && i != digits.lastIndex) sb.append(':')
                }
                val formatted = sb.toString()
                if (formatted != s.toString()) {
                    et.setText(formatted)
                    et.setSelection(formatted.length)
                }
                isEditing = false
            }
        })
    }

}
