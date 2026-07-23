package com.raylson.jansen.inspetor

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListaNAActivity : AppCompatActivity() {

    data class NARegistro(
        val tituloVisual: String,
        val buscaHistorico: String,
        var valor: String? = null,
        var dataHora: String? = null,
        var foiRegistrado: Boolean = false
    )

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NAAdapter
    private lateinit var tvSubtituloModo: TextView
    private lateinit var tvTituloControle: TextView
    private lateinit var btnToggleControleTipo: CardView
    private lateinit var tvToggleControleTipo: TextView
    private var isModoInfield = true
    private var isModoFlwHidro = false

    // ════ ORDEM N.A. ════
    private val ordemInfield = listOf(
        NARegistro("CP", "COOLING POND"),
        NARegistro("LAGO BRUTA", "LAGOA BRUTA"),
        NARegistro("DET-01", "LAGOA DE DETENÇÃO 01"),
        NARegistro("DET-02", "LAGOA DE DETENÇÃO 02"),
        NARegistro("DET-03", "LAGOA DE DETENÇÃO 03"),
        NARegistro("ARB-01", "ARB-01"),
        NARegistro("ARB-08", "ARB-08"),
        NARegistro("ARB-09", "ARB-09"),
        NARegistro("ARB-10", "ARB-10")
    )

    private val ordemCaderno = listOf(
        NARegistro("LAGO BRUTA", "LAGOA BRUTA"),
        NARegistro("ARB-01", "ARB-01"),
        NARegistro("ARB-08", "ARB-08"),
        NARegistro("ARB-09", "ARB-09"),
        NARegistro("ARB-10", "ARB-10"),
        NARegistro("CP", "COOLING POND"),
        NARegistro("DET-01", "LAGOA DE DETENÇÃO 01"),
        NARegistro("DET-02", "LAGOA DE DETENÇÃO 02"),
        NARegistro("DET-03", "LAGOA DE DETENÇÃO 03")
    )

    // ════ ORDEM VAZÃO (FLOWMETER / HIDRÔMETRO) ════
    private val ordemFlwHidroInfield = listOf(
        NARegistro("HM-01", "DET-01_01"),
        NARegistro("HM-02", "DET-01_02"),
        NARegistro("HM-03", "DET-01_03"),
        NARegistro("HM-04", "DET-01_04"),
        NARegistro("ARB-05", "ARB-05_ARB-05-FM"),
        NARegistro("ARB-06", "ARB-06_ARB-06-FM"),
        NARegistro("BA-73", "ARB-07_BA-73"),
        NARegistro("BA-74", "ARB-07_BA-74"),
        NARegistro("BA-85", "ARB-08_BA-85"),
        NARegistro("BA-86", "ARB-08_BA-86"),
        NARegistro("BA-87", "ARB-08_BA-87"),
        NARegistro("ARB-9.1", "ARB-09_9.1"),
        NARegistro("ARB-9.2", "ARB-09_9.2")
    )

    private val ordemFlwHidroCaderno = listOf(
        NARegistro("ARB-9.1", "ARB-09_9.1"),
        NARegistro("ARB-9.2", "ARB-09_9.2"),
        NARegistro("BA-85", "ARB-08_BA-85"),
        NARegistro("BA-86", "ARB-08_BA-86"),
        NARegistro("BA-87", "ARB-08_BA-87"),
        NARegistro("BA-73", "ARB-07_BA-73"),
        NARegistro("BA-74", "ARB-07_BA-74"),
        NARegistro("ARB-06", "ARB-06_ARB-06-FM"),
        NARegistro("ARB-05", "ARB-05_ARB-05-FM"),
        NARegistro("HM-01", "DET-01_01"),
        NARegistro("HM-02", "DET-01_02"),
        NARegistro("HM-03", "DET-01_03"),
        NARegistro("HM-04", "DET-01_04")
    )

    private var listaAtual = ordemInfield.map { it.copy() }.toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR 
        setContentView(R.layout.activity_lista_na)

        tvSubtituloModo = findViewById(R.id.tvSubtituloModo)
        tvTituloControle = findViewById(R.id.tvTituloControle)
        btnToggleControleTipo = findViewById(R.id.btnToggleControleTipo)
        tvToggleControleTipo = findViewById(R.id.tvToggleControleTipo)

        findViewById<ImageView>(R.id.btn_back_na).setOnClickListener { finish() }

        recycler = findViewById(R.id.recycler_na)
        recycler.layoutManager = GridLayoutManager(this, 3)
        
        carregarDadosDoHistorico(listaAtual)
        adapter = NAAdapter(listaAtual)
        recycler.adapter = adapter

        btnToggleControleTipo.setOnClickListener { alternarTipoControle() }

        val btnPenBox = findViewById<FloatingActionButton>(R.id.frame_pen_box)
        val ivPenIcon = findViewById<ImageView>(R.id.ivPenIconForAnim)

        btnPenBox.setOnClickListener {
            executarAnimacaoEscritaPremium(ivPenIcon)
            it.postDelayed({
                isModoInfield = !isModoInfield
                tvSubtituloModo.text = if (isModoInfield) "ORDEM: INFIELD" else "ORDEM: CADERNO"

                if (isModoFlwHidro) {
                    val novaBase = if (isModoInfield) ordemFlwHidroInfield else ordemFlwHidroCaderno
                    listaAtual.clear()
                    listaAtual.addAll(novaBase.map { it.copy() })
                    carregarDadosFlwHidro(listaAtual)
                } else {
                    val novaBase = if (isModoInfield) ordemInfield else ordemCaderno
                    listaAtual.clear()
                    listaAtual.addAll(novaBase.map { it.copy() })
                    carregarDadosDoHistorico(listaAtual)
                }
                adapter.atualizarDados(listaAtual)
            }, 300)
        }
        
        findViewById<View>(R.id.btnIrParaHistorico).setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
            finish()
        }
    }

    private fun alternarTipoControle() {
        isModoFlwHidro = !isModoFlwHidro

        if (isModoFlwHidro) {
            tvTituloControle.text = "CONTROLE: VAZÃO"
            tvSubtituloModo.text  = if (isModoInfield) "ORDEM: INFIELD" else "ORDEM: CADERNO"
            tvToggleControleTipo.text = "ALTERNAR: N.A."

            listaAtual.clear()
            val baseLista = if (isModoInfield) ordemFlwHidroInfield else ordemFlwHidroCaderno
            listaAtual.addAll(baseLista.map { it.copy() })
            carregarDadosFlwHidro(listaAtual)
        } else {
            tvTituloControle.text = "CONTROLE: N.A."
            tvSubtituloModo.text  = if (isModoInfield) "ORDEM: INFIELD" else "ORDEM: CADERNO"
            tvToggleControleTipo.text = "ALTERNAR: VAZÃO"

            val baseLista = if (isModoInfield) ordemInfield else ordemCaderno
            listaAtual.clear()
            listaAtual.addAll(baseLista.map { it.copy() })
            carregarDadosDoHistorico(listaAtual)
        }
        adapter.atualizarDados(listaAtual)
    }

    private fun carregarDadosFlwHidro(lista: List<NARegistro>) {
        val prefs = SecurePrefs.get(this, "leituras_flw_hidro")
        for (local in lista) {
            local.foiRegistrado = false
            local.valor = null
            local.dataHora = null

            val raw = prefs.getString(local.buscaHistorico, null) ?: continue
            try {
                val obj = org.json.JSONObject(raw)
                val valorRaw = obj.optString("valor", "")
                val dh = obj.optString("dataHora", "")
                if (valorRaw.isBlank()) continue

                local.valor = valorRaw
                local.dataHora = dh
                local.foiRegistrado = true
            } catch (_: Exception) { }
        }
    }

    private fun carregarDadosDoHistorico(lista: List<NARegistro>) {
        val prefs = SecurePrefs.get(this, "historico_prefs")
        val raw = prefs.getString("registros_json", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (local in lista) {
                local.foiRegistrado = false
                for (i in arr.length() - 1 downTo 0) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optString("grupo") == "N.A." && obj.optString("subtitulo").equals(local.buscaHistorico, true)) {
                        local.foiRegistrado = true
                        local.dataHora = obj.optString("dataHora", "")
                        local.valor = obj.optString("valorNA", null)
                        break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    inner class NAAdapter(private var dados: List<NARegistro>) : RecyclerView.Adapter<NAAdapter.VH>() {
        fun atualizarDados(novosDados: List<NARegistro>) { dados = novosDados; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitulo: TextView = v.findViewById(R.id.tvTituloLugar)
            val tvValor: TextView = v.findViewById(R.id.tvValorNA)
            val tvData: TextView = v.findViewById(R.id.tvDataRegistro)

            private var tempoUltimoClique: Long = 0

            init {
                v.setOnClickListener {
                    val tempoAtual = System.currentTimeMillis()
                    if (tempoAtual - tempoUltimoClique < 400) { 
                        tempoUltimoClique = 0 
                        val pos = adapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            abrirDialogEdicaoManual(dados[pos], pos)
                        }
                    } else {
                        tempoUltimoClique = tempoAtual
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lista_na, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = dados[position]
            holder.tvTitulo.text = item.tituloVisual
            if (!item.foiRegistrado) {
                holder.itemView.alpha = 0.5f
                holder.tvValor.text = "PENDENTE"
                holder.tvData.text = "--:--"
            } else {
                holder.itemView.alpha = 1.0f
                holder.tvData.text = item.dataHora
                holder.tvValor.text = item.valor ?: "SEM LEITURA"
                holder.tvValor.setTextColor(if (item.valor == null) Color.parseColor("#EF4444") else Color.parseColor("#2563EB"))
            }
        }
        override fun getItemCount(): Int = dados.size
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  EDIÇÃO MANUAL RÁPIDA (duplo toque no card)
    // ═══════════════════════════════════════════════════════════════════════
    private fun abrirDialogEdicaoManual(item: NARegistro, position: Int) {
        val view = layoutInflater.inflate(R.layout.dialog_edicao_manual_lista, null)
        val d = AlertDialog.Builder(this).setView(view).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitulo      = view.findViewById<TextView>(R.id.tvTituloEdicaoManual)
        val tvLabelValor  = view.findViewById<TextView>(R.id.tvLabelValorEdicao)
        val etValor       = view.findViewById<EditText>(R.id.etValorEdicaoLista)
        val etData        = view.findViewById<EditText>(R.id.etDataEdicaoLista)
        val etHora        = view.findViewById<EditText>(R.id.etHoraEdicaoLista)
        val btnRestaurar  = view.findViewById<ImageView>(R.id.btnRestaurarOriginalLista)
        val btnCancelar   = view.findViewById<Button>(R.id.btnCancelarEdicaoLista)
        val btnSalvar     = view.findViewById<Button>(R.id.btnSalvarEdicaoLista)

        tvTitulo.text = item.tituloVisual

        // Pré-preenche com o que já existe
        etValor.setText(item.valor ?: "")
        
        // Se já tem data/hora salva, ele puxa. Se não tem (PENDENTE), ele insere a data/hora atual!
        if (!item.dataHora.isNullOrEmpty()) {
            val (dataIni, horaIni) = separarDataHora(item.dataHora)
            etData.setText(dataIni)
            etHora.setText(horaIni)
        } else {
            val agora = Date()
            etData.setText(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(agora))
            etHora.setText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(agora))
        }

        if (isModoFlwHidro) {
            val isHidrometro = item.tituloVisual.startsWith("HM")
            tvLabelValor.text = if (isHidrometro) "LEITURA DO HIDRÔMETRO" else "VAZÃO (m³/hr)"
            etValor.hint = if (isHidrometro) "+ 000.00 x1m³/h" else "000.0 m³/hr"
            // Traz de volta APENAS o teclado numérico com suporte a decimais!
            etValor.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            aplicarMascaraFlwHidro(etValor, isHidrometro)
        } else {
            tvLabelValor.text = "VALOR N.A."
            aplicarMascaraNA(etValor)
        }

        
        aplicarMascaraData(etData)
        aplicarMascaraHora(etHora)

        btnRestaurar.setOnClickListener {
            val chave = item.buscaHistorico
            
            val resultado = if (isModoFlwHidro) removerValorFlwGlobal(chave) else removerValorNAGlobal(chave)

            when (resultado) {
                1 -> { 
                    item.valor = null
                    item.dataHora = null
                    item.foiRegistrado = false
                    adapter.notifyItemChanged(position)
                    
                    // ═══ Atualiza o diálogo em tempo real e NÃO fecha a tela ═══
                    etValor.setText("")
                    etData.setText("")
                    etHora.setText("")
                    etValor.clearFocus()
                    
                    Toast.makeText(this@ListaNAActivity, "Restaurado para PENDENTE", Toast.LENGTH_SHORT).show()
                }
                2 -> { 
                    if (isModoFlwHidro) {
                        val raw = SecurePrefs.get(this@ListaNAActivity, "leituras_flw_hidro").getString(chave, null)
                        if (raw != null) {
                            val obj = JSONObject(raw)
                            item.valor = obj.optString("valor", "")
                            item.dataHora = obj.optString("dataHora", "")
                            item.foiRegistrado = true
                        }
                    } else {
                        val prefs = SecurePrefs.get(this@ListaNAActivity, "historico_prefs")
                        val raw = prefs.getString("registros_json", "[]") ?: "[]"
                        val arr = JSONArray(raw)
                        for (i in arr.length() - 1 downTo 0) {
                            val obj = arr.getJSONObject(i)
                            if (obj.optString("grupo") == "N.A." && obj.optString("subtitulo").equals(chave, true)) {
                                item.valor = obj.optString("valorNA", "")
                                item.dataHora = obj.optString("dataHora", "")
                                item.foiRegistrado = true
                                break
                            }
                        }
                    }
                    adapter.notifyItemChanged(position)
                    
                    // ═══ Atualiza o diálogo em tempo real e NÃO fecha a tela ═══
                    etValor.setText(item.valor ?: "")
                    val (dataOri, horaOri) = separarDataHora(item.dataHora)
                    etData.setText(dataOri)
                    etHora.setText(horaOri)
                    etValor.clearFocus()
                    
                    Toast.makeText(this@ListaNAActivity, "Valores originais restaurados", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this@ListaNAActivity, "Nenhum dado para restaurar", Toast.LENGTH_SHORT).show()
                }
            }
        }


        btnCancelar.setOnClickListener {
            esconderTeclado(view)
            d.dismiss()
        }

        btnSalvar.setOnClickListener {
            val valor = etValor.text.toString().trim()
            val dataDigitada = etData.text.toString().trim()
            val horaDigitada = etHora.text.toString().trim()

            if (valor.isEmpty()) {
                Toast.makeText(this, "Digite o valor antes de salvar.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dataParcial = dataDigitada.isNotEmpty() && dataDigitada.length != 10
            val horaParcial = horaDigitada.isNotEmpty() && horaDigitada.length != 5
            if (dataParcial || horaParcial) {
                Toast.makeText(this, "Preencha DATA (DD.MM.AAAA) e HORA (HH:mm) completas, ou deixe ambas vazias.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dataHoraFinal = if (dataDigitada.isNotEmpty() && horaDigitada.isNotEmpty()) {
                "$dataDigitada // ${horaDigitada}h"
            } else {
                SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())
            }

            if (isModoFlwHidro) {
                salvarValorFlwGlobal(item.buscaHistorico, valor, dataHoraFinal)
            } else {
                salvarValorNAGlobal(item.buscaHistorico, valor, dataHoraFinal)
            }

            item.valor = valor
            item.dataHora = dataHoraFinal
            item.foiRegistrado = true
            adapter.notifyItemChanged(position)

            esconderTeclado(view)
            Toast.makeText(this, "✓ Salvo com sucesso", Toast.LENGTH_SHORT).show()
            d.dismiss()
        }

        d.show()
    }

    private fun separarDataHora(texto: String?): Pair<String, String> {
        if (texto.isNullOrBlank()) return "" to ""
        val partes = texto.split("//").map { it.trim() }
        val data = partes.getOrNull(0) ?: ""
        val hora = (partes.getOrNull(1) ?: "").removeSuffix("h").trim()
        return data to hora
    }

    private fun esconderTeclado(v: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun aplicarMascaraNA(et: EditText) {
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        et.hint = "00.00m"
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

    private fun aplicarMascaraFlwHidro(et: EditText, isHidrometro: Boolean) {
        et.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            private val prefixo = if (isHidrometro) "+ " else ""
            private val sufixo = if (isHidrometro) " x1m³/h" else " m³/hr"

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                var raw = s.toString()

                if (prefixo.isNotEmpty() && raw.startsWith(prefixo)) raw = raw.substring(prefixo.length)
                if (sufixo.isNotEmpty() && raw.endsWith(sufixo)) raw = raw.substring(0, raw.length - sufixo.length)
                
                raw = raw.replace("x1m³/h", "").replace("x1m³/", "").replace("x1m³", "").replace("x1m", "").replace("x1", "").replace("x", "")
                raw = raw.replace("m³/hr", "").replace("m³/", "").replace("m³", "").replace("m", "").replace("h", "").replace("r", "")
                raw = raw.replace("³", "").replace("/", "").replace("+", "").replace(" ", "")

                raw = raw.filter { it.isDigit() || it == '.' }
                val parts = raw.split('.')
                if (parts.size > 2) raw = parts[0] + "." + parts.drop(1).joinToString("")

                val formatted = if (raw.isEmpty()) "" else "$prefixo$raw$sufixo"

                if (s.toString() != formatted) {
                    et.setText(formatted)
                    val cursorPosition = if (formatted.isNotEmpty()) prefixo.length + raw.length else 0
                    et.setSelection(cursorPosition.coerceIn(0, formatted.length))
                }
                
                isEditing = false
            }
        })
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

    private fun salvarValorNAGlobal(subtitulo: String, valor: String, dataHora: String) {
        try {
            val prefs = SecurePrefs.get(this, "historico_prefs")
            val raw = prefs.getString("registros_json", "[]") ?: "[]"
            val arr = JSONArray(raw)
            var idx = -1
            for (i in arr.length() - 1 downTo 0) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("grupo") == "N.A." && obj.optString("subtitulo").equals(subtitulo, true)) {
                    idx = i; break
                }
            }
            if (idx >= 0) {
                val obj = arr.getJSONObject(idx)
                val temFoto = !obj.isNull("fotoPath") && obj.optString("fotoPath").isNotBlank()
                
                if (temFoto && !obj.has("valorNA_original")) {
                    obj.put("valorNA_original", obj.optString("valorNA", ""))
                    obj.put("dataHora_original", obj.optString("dataHora", ""))
                }
                obj.put("valorNA", valor)
                obj.put("dataHora", dataHora)
            } else {
                val novo = JSONObject().apply {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("grupo", "N.A.")
                    put("subtitulo", subtitulo)
                    put("dataHora", dataHora)
                    put("statusSuperior", JSONObject.NULL)
                    put("statusInferior", JSONObject.NULL)
                    put("valorNA", valor)
                    put("fotoPath", JSONObject.NULL)
                }
                arr.put(novo)
            }
            prefs.edit().putString("registros_json", arr.toString()).commit()
        } catch (_: Exception) { }
    }

    private fun removerValorNAGlobal(subtitulo: String): Int {
        return try {
            val prefs = SecurePrefs.get(this, "historico_prefs")
            val raw = prefs.getString("registros_json", "[]") ?: "[]"
            val arr = JSONArray(raw)
            var idx = -1
            for (i in arr.length() - 1 downTo 0) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("grupo") == "N.A." && obj.optString("subtitulo").equals(subtitulo, true)) {
                    idx = i; break
                }
            }
            if (idx < 0) return 0

            val obj = arr.getJSONObject(idx)
            val temFoto = !obj.isNull("fotoPath") && obj.optString("fotoPath").isNotBlank()

            if (temFoto) {
                if (obj.has("valorNA_original")) {
                    obj.put("valorNA", obj.optString("valorNA_original", ""))
                    obj.put("dataHora", obj.optString("dataHora_original", ""))
                    obj.remove("valorNA_original")
                    obj.remove("dataHora_original")
                    prefs.edit().putString("registros_json", arr.toString()).commit()
                    return 2 
                } else {
                    return 2 
                }
            } else {
                val novoArr = JSONArray()
                for (i in 0 until arr.length()) if (i != idx) novoArr.put(arr.getJSONObject(i))
                prefs.edit().putString("registros_json", novoArr.toString()).commit()
                return 1 
            }
        } catch (_: Exception) { 0 }
    }

    private fun salvarValorFlwGlobal(chave: String, valor: String, dataHora: String) {
        try {
            val prefs = SecurePrefs.get(this, "leituras_flw_hidro")
            val existenteRaw = prefs.getString(chave, null)
            val obj = if (existenteRaw != null) JSONObject(existenteRaw) else JSONObject()
            
            if (obj.has("estacao") && !obj.has("valor_original")) {
                obj.put("valor_original", obj.optString("valor", ""))
                obj.put("dataHora_original", obj.optString("dataHora", ""))
            }

            obj.put("valor", valor)
            obj.put("dataHora", dataHora)
            prefs.edit().putString(chave, obj.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun removerValorFlwGlobal(chave: String): Int {
        return try {
            val prefs = SecurePrefs.get(this, "leituras_flw_hidro")
            val existenteRaw = prefs.getString(chave, null) ?: return 0
            val obj = JSONObject(existenteRaw)

            if (obj.has("estacao")) {
                if (obj.has("valor_original")) {
                    obj.put("valor", obj.optString("valor_original", ""))
                    obj.put("dataHora", obj.optString("dataHora_original", ""))
                    obj.remove("valor_original")
                    obj.remove("dataHora_original")
                    prefs.edit().putString(chave, obj.toString()).apply()
                    return 2 
                } else {
                    return 2 
                }
            } else {
                prefs.edit().remove(chave).apply()
                return 1 
            }
        } catch (_: Exception) { 0 }
    }

    private fun executarAnimacaoEscritaPremium(iconView: View) {
        ObjectAnimator.ofPropertyValuesHolder(iconView, 
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, 18f, 0f),
            PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 15f, 0f)).apply {
            duration = 600; start()
        }
    }
}
