package com.raylson.jansen.inspetor.ui.historico

import android.content.Context
import cafe.adriel.voyager.core.model.ScreenModel
import com.raylson.jansen.inspetor.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.io.File

enum class HistoricoComposeMode { NA, DET_01, ARB }

sealed class GradeComposeItem {
    data class Card(val nome: String, val temRegistro: Boolean) : GradeComposeItem()
    data class Secao(val titulo: String) : GradeComposeItem()
}

data class HistoricoState(
    val mode: HistoricoComposeMode = HistoricoComposeMode.NA,
    val itensDaGrade: List<GradeComposeItem> = emptyList()
)

class HistoricoScreenModel(private val appContext: Context) : ScreenModel {
    private val historicoPrefs by lazy { SecurePrefs.get(appContext, "historico_prefs") }
    private val leituraPrefs by lazy { SecurePrefs.get(appContext, "leituras_flw_hidro") }

    private val _state = MutableStateFlow(HistoricoState())
    val state: StateFlow<HistoricoState> = _state

    init {
        atualizarGradePorModo(HistoricoComposeMode.NA)
    }

    fun animarRotacaoHistorico() {
        val nextMode = when (_state.value.mode) {
            HistoricoComposeMode.NA -> HistoricoComposeMode.DET_01
            HistoricoComposeMode.DET_01 -> HistoricoComposeMode.ARB
            HistoricoComposeMode.ARB -> HistoricoComposeMode.NA
        }
        atualizarGradePorModo(nextMode)
    }

    fun recarregarGrade() {
        atualizarGradePorModo(_state.value.mode)
    }

    private fun atualizarGradePorModo(mode: HistoricoComposeMode) {
        val lista = mutableListOf<GradeComposeItem>()
        when (mode) {
            HistoricoComposeMode.NA -> {
                listOf("ARB-01", "DET-01", "DET-02", "DT2-ex", "DET-03", "L.BRUTA", "CP", "CP-ex", "ARB-08", "ARB-09", "ARB-10").forEach { 
                    lista.add(GradeComposeItem.Card(it, temRegistro(it, mode))) 
                }
            }
            HistoricoComposeMode.DET_01 -> {
                listOf("HM-01", "HM-02", "HM-03", "HM-04", "SIFÕES").forEach { 
                    lista.add(GradeComposeItem.Card(it, temRegistro(it, mode))) 
                }
            }
            HistoricoComposeMode.ARB -> {
                lista.add(GradeComposeItem.Card("ARB-05", temRegistro("ARB-05", mode)))
                lista.add(GradeComposeItem.Card("ARB-06", temRegistro("ARB-06", mode)))
                lista.add(GradeComposeItem.Secao("ARB-07"))
                lista.add(GradeComposeItem.Card("BA-73", temRegistro("BA-73", mode)))
                lista.add(GradeComposeItem.Card("BA-74", temRegistro("BA-74", mode)))
                lista.add(GradeComposeItem.Secao("ARB-08"))
                lista.add(GradeComposeItem.Card("BA-85", temRegistro("BA-85", mode)))
                lista.add(GradeComposeItem.Card("BA-86", temRegistro("BA-86", mode)))
                lista.add(GradeComposeItem.Card("BA-87", temRegistro("BA-87", mode)))
                lista.add(GradeComposeItem.Secao("ARB-09"))
                lista.add(GradeComposeItem.Card("ARB-9.1", temRegistro("ARB-9.1", mode)))
                lista.add(GradeComposeItem.Card("ARB-9.2", temRegistro("ARB-9.2", mode)))
            }
        }
        _state.update { it.copy(mode = mode, itensDaGrade = lista) }
    }

    private fun temRegistro(itemGrid: String, currentMode: HistoricoComposeMode): Boolean {
        val raw = historicoPrefs.getString("registros_json", "[]") ?: "[]"
        val subtitulosValidos = mutableListOf<String>()
        var grupoEsperado: String? = null

        when (itemGrid) {
            "ARB-01"  -> { subtitulosValidos.add("ARB-01"); grupoEsperado = "N.A." }
            "DET-01"  -> { if (currentMode == HistoricoComposeMode.NA) { subtitulosValidos.add("LAGOA DE DETENÇÃO 01"); grupoEsperado = "N.A." } else { subtitulosValidos.add("HM-01"); grupoEsperado = "DET-01" } }
            "DET-02"  -> { subtitulosValidos.add("LAGOA DE DETENÇÃO 02"); grupoEsperado = "N.A." }
            "DT2-ex"  -> { subtitulosValidos.add("DET-02 EXTRAVASOR"); subtitulosValidos.add("DO EXTRAVASOR DET-02"); grupoEsperado = "N.A." }
            "DET-03"  -> { subtitulosValidos.add("LAGOA DE DETENÇÃO 03"); grupoEsperado = "N.A." }
            "L.BRUTA" -> { subtitulosValidos.add("LAGOA BRUTA"); grupoEsperado = "N.A." }
            "CP"      -> { subtitulosValidos.add("COOLING POND"); grupoEsperado = "N.A." }
            "CP-ex"   -> { subtitulosValidos.add("COOLING POND EXTRAVASOR"); subtitulosValidos.add("EXTRAVASOR C.P / COOLING POND"); subtitulosValidos.add("EXTRAVASOR C.P"); grupoEsperado = "N.A." }
            "ARB-08"  -> { if (currentMode == HistoricoComposeMode.NA) { subtitulosValidos.add("ARB-08"); grupoEsperado = "N.A." } else { subtitulosValidos.add("BA-85"); grupoEsperado = "ARB-08" } }
            "ARB-09"  -> { if (currentMode == HistoricoComposeMode.NA) { subtitulosValidos.add("ARB-09"); grupoEsperado = "N.A." } else { subtitulosValidos.add("9.1"); grupoEsperado = "ARB-09" } }
            "ARB-10" -> { subtitulosValidos.add("ARB-10"); grupoEsperado = "N.A."}
            "HM-01" -> { subtitulosValidos.add("HM-01"); grupoEsperado = "DET-01" }
            "HM-02" -> { subtitulosValidos.add("HM-02"); grupoEsperado = "DET-01" }
            "HM-03" -> { subtitulosValidos.add("HM-03"); grupoEsperado = "DET-01" }
            "HM-04" -> { subtitulosValidos.add("HM-04"); grupoEsperado = "DET-01" }
            "SIFÕES" -> { subtitulosValidos.add("SIFÕES"); subtitulosValidos.add("SIFÃO SUP."); subtitulosValidos.add("SIFÃO INF."); grupoEsperado = "DET-01" }
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

        try {
            val arr = JSONArray(raw)
            for (i in arr.length() - 1 downTo 0) {
                val obj = arr.getJSONObject(i)
                val sub = obj.optString("subtitulo", "")
                val grupo = obj.optString("grupo", "")
                val subOk = subtitulosValidos.any { it.equals(sub, ignoreCase = true) }
                val grupoOk = grupoEsperado == null || grupo.equals(grupoEsperado, ignoreCase = true)
                if (subOk && grupoOk) return true
            }
        } catch (e: Exception) {}
        return false
    }

    fun limparHistorico() {
        try {
            val dir = File(appContext.filesDir, "historico")
            if (dir.exists()) {
                dir.listFiles()?.forEach { try { it.delete() } catch (_: Exception) { } }
            }
            historicoPrefs.edit().putString("registros_json", "[]").apply()
            val editorLeitura = leituraPrefs.edit()
            leituraPrefs.all.keys.forEach { editorLeitura.remove(it) }
            editorLeitura.apply()
        } catch (e: Exception) {}
        recarregarGrade()
    }
}
