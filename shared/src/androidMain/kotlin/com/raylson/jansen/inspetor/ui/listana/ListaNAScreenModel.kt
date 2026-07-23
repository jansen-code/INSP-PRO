package com.raylson.jansen.inspetor.ui.listana

import android.content.Context
import cafe.adriel.voyager.core.model.ScreenModel
import com.raylson.jansen.inspetor.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NARegistro(
    val tituloVisual: String,
    val buscaHistorico: String,
    var valor: String? = null,
    var dataHora: String? = null,
    var foiRegistrado: Boolean = false
)

data class ListaNAState(
    val tituloControle: String = "CONTROLE: N.A.",
    val subtituloModo: String = "ORDEM: INFIELD",
    val textoToggle: String = "ALTERNAR: VAZÃO",
    val itens: List<NARegistro> = emptyList(),
    val editandoItem: NARegistro? = null,
    val editPosition: Int = -1
)

class ListaNAScreenModel(private val appContext: Context) : ScreenModel {

    private val historicoPrefs by lazy { SecurePrefs.get(appContext, "historico_prefs") }
    private val leituraPrefs by lazy { SecurePrefs.get(appContext, "leituras_flw_hidro") }

    private val _state = MutableStateFlow(ListaNAState())
    val state: StateFlow<ListaNAState> = _state

    private var isModoInfield = true
    private var isModoFlwHidro = false

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

    init {
        carregarDados()
    }

    fun alternarTipoControle() {
        isModoFlwHidro = !isModoFlwHidro

        if (isModoFlwHidro) {
            val baseLista = if (isModoInfield) ordemFlwHidroInfield else ordemFlwHidroCaderno
            listaAtual.clear()
            listaAtual.addAll(baseLista.map { it.copy() })
            carregarDadosFlwHidro()
        } else {
            val baseLista = if (isModoInfield) ordemInfield else ordemCaderno
            listaAtual.clear()
            listaAtual.addAll(baseLista.map { it.copy() })
            carregarDadosDoHistorico()
        }

        _state.update {
            it.copy(
                tituloControle = if (isModoFlwHidro) "CONTROLE: VAZÃO" else "CONTROLE: N.A.",
                subtituloModo = if (isModoInfield) "ORDEM: INFIELD" else "ORDEM: CADERNO",
                textoToggle = if (isModoFlwHidro) "ALTERNAR: N.A." else "ALTERNAR: VAZÃO",
                itens = listaAtual.toList()
            )
        }
    }

    fun alternarOrdem() {
        isModoInfield = !isModoInfield

        if (isModoFlwHidro) {
            val novaBase = if (isModoInfield) ordemFlwHidroInfield else ordemFlwHidroCaderno
            listaAtual.clear()
            listaAtual.addAll(novaBase.map { it.copy() })
            carregarDadosFlwHidro()
        } else {
            val novaBase = if (isModoInfield) ordemInfield else ordemCaderno
            listaAtual.clear()
            listaAtual.addAll(novaBase.map { it.copy() })
            carregarDadosDoHistorico()
        }

        _state.update {
            it.copy(
                subtituloModo = if (isModoInfield) "ORDEM: INFIELD" else "ORDEM: CADERNO",
                itens = listaAtual.toList()
            )
        }
    }

    fun abrirEdicao(position: Int) {
        if (position in listaAtual.indices) {
            _state.update { it.copy(editandoItem = listaAtual[position], editPosition = position) }
        }
    }

    fun fecharEdicao() {
        _state.update { it.copy(editandoItem = null, editPosition = -1) }
    }

    fun salvarEdicao(valor: String, dataDigitada: String, horaDigitada: String): Boolean {
        val item = _state.value.editandoItem ?: return false
        val position = _state.value.editPosition

        if (valor.isEmpty()) return false

        val dataParcial = dataDigitada.isNotEmpty() && dataDigitada.length != 10
        val horaParcial = horaDigitada.isNotEmpty() && horaDigitada.length != 5
        if (dataParcial || horaParcial) return false

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

        listaAtual[position].valor = valor
        listaAtual[position].dataHora = dataHoraFinal
        listaAtual[position].foiRegistrado = true

        _state.update { it.copy(itens = listaAtual.toList(), editandoItem = null, editPosition = -1) }
        return true
    }

    fun restaurarOriginal(): Int {
        val item = _state.value.editandoItem ?: return 0
        val position = _state.value.editPosition
        val chave = item.buscaHistorico

        val resultado = if (isModoFlwHidro) removerValorFlwGlobal(chave) else removerValorNAGlobal(chave)

        when (resultado) {
            1 -> {
                listaAtual[position].valor = null
                listaAtual[position].dataHora = null
                listaAtual[position].foiRegistrado = false
                _state.update { it.copy(itens = listaAtual.toList()) }
            }
            2 -> {
                if (isModoFlwHidro) {
                    val raw = leituraPrefs.getString(chave, null)
                    if (raw != null) {
                        val obj = JSONObject(raw)
                        listaAtual[position].valor = obj.optString("valor", "")
                        listaAtual[position].dataHora = obj.optString("dataHora", "")
                        listaAtual[position].foiRegistrado = true
                    }
                } else {
                    val raw = historicoPrefs.getString("registros_json", "[]") ?: "[]"
                    val arr = JSONArray(raw)
                    for (i in arr.length() - 1 downTo 0) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("grupo") == "N.A." && obj.optString("subtitulo").equals(chave, true)) {
                            listaAtual[position].valor = obj.optString("valorNA", "")
                            listaAtual[position].dataHora = obj.optString("dataHora", "")
                            listaAtual[position].foiRegistrado = true
                            break
                        }
                    }
                }
                _state.update { it.copy(itens = listaAtual.toList()) }
            }
        }
        return resultado
    }

    fun separarDataHora(texto: String?): Pair<String, String> {
        if (texto.isNullOrBlank()) return "" to ""
        val partes = texto.split("//").map { it.trim() }
        val data = partes.getOrNull(0) ?: ""
        val hora = (partes.getOrNull(1) ?: "").removeSuffix("h").trim()
        return data to hora
    }

    fun isModoFlwHidro(): Boolean = isModoFlwHidro

    private fun carregarDados() {
        if (isModoFlwHidro) {
            carregarDadosFlwHidro()
        } else {
            carregarDadosDoHistorico()
        }
        _state.update { it.copy(itens = listaAtual.toList()) }
    }

    private fun carregarDadosFlwHidro() {
        for (local in listaAtual) {
            local.foiRegistrado = false
            local.valor = null
            local.dataHora = null

            val raw = leituraPrefs.getString(local.buscaHistorico, null) ?: continue
            try {
                val obj = JSONObject(raw)
                val valorRaw = obj.optString("valor", "")
                val dh = obj.optString("dataHora", "")
                if (valorRaw.isBlank()) continue

                local.valor = valorRaw
                local.dataHora = dh
                local.foiRegistrado = true
            } catch (_: Exception) { }
        }
    }

    private fun carregarDadosDoHistorico() {
        val raw = historicoPrefs.getString("registros_json", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (local in listaAtual) {
                local.foiRegistrado = false
                for (i in arr.length() - 1 downTo 0) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optString("grupo") == "N.A." && obj.optString("subtitulo").equals(local.buscaHistorico, true)) {
                        local.foiRegistrado = true
                        local.dataHora = obj.optString("dataHora", "")
                        local.valor = obj.optString("valorNA", "")
                        if (local.valor.isNullOrEmpty()) local.valor = null
                        break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun salvarValorNAGlobal(subtitulo: String, valor: String, dataHora: String) {
        try {
            val raw = historicoPrefs.getString("registros_json", "[]") ?: "[]"
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
            historicoPrefs.edit().putString("registros_json", arr.toString()).commit()
        } catch (_: Exception) { }
    }

    private fun removerValorNAGlobal(subtitulo: String): Int {
        return try {
            val raw = historicoPrefs.getString("registros_json", "[]") ?: "[]"
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
                    historicoPrefs.edit().putString("registros_json", arr.toString()).commit()
                    return 2
                } else {
                    return 2
                }
            } else {
                val novoArr = JSONArray()
                for (i in 0 until arr.length()) if (i != idx) novoArr.put(arr.getJSONObject(i))
                historicoPrefs.edit().putString("registros_json", novoArr.toString()).commit()
                return 1
            }
        } catch (_: Exception) { 0 }
    }

    private fun salvarValorFlwGlobal(chave: String, valor: String, dataHora: String) {
        try {
            val existenteRaw = leituraPrefs.getString(chave, null)
            val obj = if (existenteRaw != null) JSONObject(existenteRaw) else JSONObject()

            if (obj.has("estacao") && !obj.has("valor_original")) {
                obj.put("valor_original", obj.optString("valor", ""))
                obj.put("dataHora_original", obj.optString("dataHora", ""))
            }

            obj.put("valor", valor)
            obj.put("dataHora", dataHora)
            leituraPrefs.edit().putString(chave, obj.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun removerValorFlwGlobal(chave: String): Int {
        return try {
            val existenteRaw = leituraPrefs.getString(chave, null) ?: return 0
            val obj = JSONObject(existenteRaw)

            if (obj.has("estacao")) {
                if (obj.has("valor_original")) {
                    obj.put("valor", obj.optString("valor_original", ""))
                    obj.put("dataHora", obj.optString("dataHora_original", ""))
                    obj.remove("valor_original")
                    obj.remove("dataHora_original")
                    leituraPrefs.edit().putString(chave, obj.toString()).apply()
                    return 2
                } else {
                    return 2
                }
            } else {
                leituraPrefs.edit().remove(chave).apply()
                return 1
            }
        } catch (_: Exception) { 0 }
    }
}
