package com.raylson.jansen.inspetor.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.raylson.jansen.inspetor.domain.PontoGeotecnico
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InspMapUiState(
    val projetos: List<PontoGeotecnico> = emptyList(),
    val dialogNovoProjetoAberto: Boolean = false,
    val nomeProjetoTemp: String = "",
    val dataHoraTemp: String = "",
    val zonaUtmSelecionada: String = "UTM-23S",
    val btConectado: Boolean = false,
    val precisaoMetros: Double = 0.0,
    val latitudeAtual: Double = 0.0,
    val longitudeAtual: Double = 0.0
)

class InspMapScreenModel : ScreenModel {

    private val _state = MutableStateFlow(InspMapUiState(
        dataHoraTemp = timestampAtual()
    ))
    val state: StateFlow<InspMapUiState> = _state.asStateFlow()

    fun abrirDialogNovoProjeto() {
        _state.update {
            it.copy(
                dialogNovoProjetoAberto = true,
                nomeProjetoTemp = "",
                dataHoraTemp = timestampAtual()
            )
        }
    }

    fun fecharDialogNovoProjeto() {
        _state.update { it.copy(dialogNovoProjetoAberto = false) }
    }

    fun atualizarNomeProjeto(nome: String) {
        _state.update { it.copy(nomeProjetoTemp = nome) }
    }

    fun atualizarDataHora(valor: String) {
        _state.update { it.copy(dataHoraTemp = valor) }
    }

    fun selecionarZonaUtm(zona: String) {
        _state.update { it.copy(zonaUtmSelecionada = zona) }
    }

    fun salvarProjeto() {
        val atual = _state.value
        if (atual.nomeProjetoTemp.isBlank()) return

        screenModelScope.launch(Dispatchers.Default) {
            val agora = System.currentTimeMillis()
            val novoPonto = PontoGeotecnico(
                id = agora.toString(),
                nomeProjeto = atual.nomeProjetoTemp.trim(),
                dataHora = agora,
                latitude = atual.latitudeAtual,
                longitude = atual.longitudeAtual,
                zonaUtm = atual.zonaUtmSelecionada
            )

            _state.update { estado ->
                estado.copy(
                    projetos = estado.projetos + novoPonto,
                    dialogNovoProjetoAberto = false,
                    nomeProjetoTemp = ""
                )
            }
        }
    }

    fun toggleBtConexao() {
        screenModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(btConectado = !it.btConectado) }

            if (_state.value.btConectado) {
                simularRtk()
            }
        }
    }

    private fun simularRtk() {
        screenModelScope.launch(Dispatchers.Default) {
            val latBase = -15.7801
            val lonBase = -47.9292
            while (_state.value.btConectado) {
                val precisao = (0.01 + Math.random() * 0.05)
                val lat = latBase + (Math.random() - 0.5) * 0.0001
                val lon = lonBase + (Math.random() - 0.5) * 0.0001
                _state.update {
                    it.copy(
                        precisaoMetros = precisao,
                        LatitudeAtual = lat,
                        longitudeAtual = lon
                    )
                }
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    private fun timestampAtual(): String {
        val agora = System.currentTimeMillis()
        val s = agora / 1000
        val seg = s % 60
        val min = (s / 60) % 60
        val hora = (s / 3600) % 24
        val dia = (s / 86400) + 1
        return "%02d/%02d/%04d %02d:%02d:%02d".format(dia.toInt(), 1, 2026, hora.toInt(), min.toInt(), seg.toInt())
    }
}
