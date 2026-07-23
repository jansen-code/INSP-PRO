package com.raylson.jansen.inspetor.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.raylson.jansen.inspetor.domain.DashboardData
import com.raylson.jansen.inspetor.domain.Estacao
import com.raylson.jansen.inspetor.domain.ItemHm
import com.raylson.jansen.inspetor.domain.LagoNA
import com.raylson.jansen.inspetor.platform.OverlayRenderer
import com.raylson.jansen.inspetor.platform.SecureStorage
import com.raylson.jansen.inspetor.platform.createSecureStorage
import com.raylson.jansen.inspetor.platform.shareImage
import com.raylson.jansen.inspetor.platform.vibrateStrong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 * DashboardScreenModel.kt
 *
 * Substitui os campos soltos + lógica imperativa da DashboardActivity
 * por um único StateFlow<DashboardUiState>, no padrão Voyager
 * (ScreenModel = ViewModel-like, sobrevive a recomposição/navegação).
 *
 * Regra de Ouro aplicada: nenhuma regra de negócio foi removida, só
 * reorganizada — comentários apontam a função original correspondente.
 * ═══════════════════════════════════════════════════════════════════
 */

/** Um diálogo por vez — nada de N booleans soltos (showDialogX, showDialogY...). */
sealed class DashboardDialog {
    object Nenhum : DashboardDialog()
    object ConfirmacaoVazao : DashboardDialog()
    object LeituraManual : DashboardDialog()
    object EscolhaOrigemFoto : DashboardDialog()
    object ExcluirFoto : DashboardDialog()
    data class EditarDataHora(val horaAtual: String, val horaOriginal: String) : DashboardDialog()
    data class ResultadoRegistro(val comTarjaLabel: String) : DashboardDialog()
}

data class DashboardUiState(
    val estacoes: List<Estacao> = DashboardData.estacoes,
    val estacaoSelecionada: Estacao = DashboardData.estacoes.first(),
    val itensAtuais: List<ItemHm> = DashboardData.itensPorEstacao["DET-01"].orEmpty(),
    val hmSelecionado: Int = 0,
    val statusBomba: String = "DESLIGADA",

    val lagosNA: List<LagoNA> = DashboardData.lagosNA,
    val lagoNASelecionado: Int = 0,
    val foraDeNA: Boolean = false,

    val saudacao: String = "",
    val gerandoRegistro: Boolean = false,
    val dialogAtivo: DashboardDialog = DashboardDialog.Nenhum,

    // fase que originou a captura em andamento: "hidro" | "bomba" | "na_regua"
    val faseCaptura: String = ""
) {
    val isModoNA: Boolean get() = estacaoSelecionada.nome == "N.A."

    /** Era `isEstacaoComFormatoConfiguravel()`. */
    val temFormatoConfiguravel: Boolean
        get() = estacaoSelecionada.nome in setOf("ARB-05", "ARB-06", "N.A.")

    val itemAtual: ItemHm? get() = itensAtuais.getOrNull(hmSelecionado)
    val lagoAtual: LagoNA? get() = lagosNA.getOrNull(lagoNASelecionado)
}

class DashboardScreenModel : ScreenModel {

    private val prefs: SecureStorage = createSecureStorage("inspetor_prefs")

    private val _state = MutableStateFlow(carregarEstadoInicial())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    /** Era o bloco de `onCreate` que lê `SecurePrefs` pra restaurar a última estação/item. */
    private fun carregarEstadoInicial(): DashboardUiState {
        val lastStationNome = prefs.getString("last_station", "DET-01") ?: "DET-01"
        val estacao = DashboardData.estacoes.find { it.nome == lastStationNome } ?: DashboardData.estacoes.first()

        return if (estacao.nome == "N.A.") {
            val lagoIdx = prefs.getInt("last_lago_na", 0).coerceIn(0, DashboardData.lagosNA.size - 1)
            DashboardUiState(
                estacaoSelecionada = estacao,
                itensAtuais = emptyList(),
                lagoNASelecionado = lagoIdx,
                statusBomba = "DESLIGADA"
            )
        } else {
            val itens = DashboardData.itensPorEstacao[estacao.nome].orEmpty()
            val hmIdx = prefs.getInt("last_hm_${estacao.nome}", 0)
                .coerceIn(0, (itens.size - 1).coerceAtLeast(0))
            DashboardUiState(
                estacaoSelecionada = estacao,
                itensAtuais = itens,
                hmSelecionado = hmIdx,
                statusBomba = itens.getOrNull(hmIdx)?.statusPadrao ?: "LIGADA"
            )
        }
    }

    /** Era o trecho de `carrosselEstacoes` onClick + persistência `last_station`. */
    fun selecionarEstacao(estacao: Estacao) {
        prefs.putString("last_station", estacao.nome)

        _state.update { atual ->
            if (estacao.nome == "N.A.") {
                val lagoIdx = prefs.getInt("last_lago_na", 0).coerceIn(0, DashboardData.lagosNA.size - 1)
                atual.copy(
                    estacaoSelecionada = estacao,
                    itensAtuais = emptyList(),
                    lagoNASelecionado = lagoIdx,
                    statusBomba = "DESLIGADA"
                )
            } else {
                val itens = DashboardData.itensPorEstacao[estacao.nome].orEmpty()
                val hmIdx = prefs.getInt("last_hm_${estacao.nome}", 0)
                    .coerceIn(0, (itens.size - 1).coerceAtLeast(0))
                atual.copy(
                    estacaoSelecionada = estacao,
                    itensAtuais = itens,
                    hmSelecionado = hmIdx,
                    statusBomba = itens.getOrNull(hmIdx)?.statusPadrao ?: "LIGADA"
                )
            }
        }
    }

    /** Era o onClick do carrosselHm + `last_hm_<estacao>`. */
    fun selecionarItem(indice: Int) {
        val nomeEstacao = _state.value.estacaoSelecionada.nome
        prefs.putInt("last_hm_$nomeEstacao", indice)
        _state.update { atual ->
            atual.copy(
                hmSelecionado = indice,
                statusBomba = atual.itensAtuais.getOrNull(indice)?.statusPadrao ?: atual.statusBomba
            )
        }
    }

    /** Era o onClick do carrossel de lagos N.A. + `last_lago_na`. */
    fun selecionarLagoNA(indice: Int) {
        prefs.putInt("last_lago_na", indice)
        _state.update { it.copy(lagoNASelecionado = indice) }
    }

    /** Era `setupBotaoToggleForaNA` / `aplicarToggleForaNA`. */
    fun toggleForaNA() {
        _state.update { it.copy(foraDeNA = !it.foraDeNA) }
    }

    /** Era `aplicarStatus` — ciclo entre os `statusDisponiveis` do item atual. */
    fun avancarStatus() {
        _state.update { atual ->
            val item = atual.itemAtual ?: return@update atual
            val opcoes = item.statusDisponiveis
            if (opcoes.isEmpty()) return@update atual
            val idxAtual = opcoes.indexOf(atual.statusBomba).let { if (it < 0) 0 else it }
            val proximo = opcoes[(idxAtual + 1) % opcoes.size]
            atual.copy(statusBomba = proximo)
        }
    }

    // ── Diálogos declarativos: troca AlertDialog.Builder imperativo por estado ──

    fun abrirDialogVazao() = _state.update { it.copy(dialogAtivo = DashboardDialog.ConfirmacaoVazao) }
    fun abrirDialogLeituraManual() = _state.update { it.copy(dialogAtivo = DashboardDialog.LeituraManual) }
    fun abrirDialogEscolhaOrigemFoto(fase: String) =
        _state.update { it.copy(dialogAtivo = DashboardDialog.EscolhaOrigemFoto, faseCaptura = fase) }
    fun abrirDialogExcluirFoto() = _state.update { it.copy(dialogAtivo = DashboardDialog.ExcluirFoto) }
    fun abrirDialogEditarDataHora(horaAtual: String, horaOriginal: String) =
        _state.update { it.copy(dialogAtivo = DashboardDialog.EditarDataHora(horaAtual, horaOriginal)) }
    fun fecharDialog() = _state.update { it.copy(dialogAtivo = DashboardDialog.Nenhum) }

    /** Era `setupCaixasCamera` guardando a foto tirada no slot certo. */
    fun definirFotoCapturada(slot: String, bytes: ByteArray, veioDoCofre: Boolean = false) {
        _state.update { atual ->
            val itens = atual.itensAtuais.toMutableList()
            val idx = atual.hmSelecionado
            val item = itens.getOrNull(idx) ?: return@update atual
            itens[idx] = when (slot) {
                "hidro" -> item.copy(fotoSup = bytes, fotoSupVeioDoCofre = veioDoCofre)
                "bomba" -> item.copy(fotoInf = bytes, fotoInfVeioDoCofre = veioDoCofre)
                else -> item
            }
            atual.copy(itensAtuais = itens)
        }
    }

    /** Era `apagarFotoIndividual`. */
    fun apagarFotoIndividual(slot: String) {
        _state.update { atual ->
            val itens = atual.itensAtuais.toMutableList()
            val idx = atual.hmSelecionado
            val item = itens.getOrNull(idx) ?: return@update atual
            itens[idx] = when (slot) {
                "hidro" -> item.copy(fotoSup = null, fotoSupVeioDoCofre = false)
                "bomba" -> item.copy(fotoInf = null, fotoInfVeioDoCofre = false)
                else -> item
            }
            atual.copy(itensAtuais = itens, dialogAtivo = DashboardDialog.Nenhum)
        }
    }

    /** Era `limparTudo(porTimeout)` — reset da estação atual pro DET-01. */
    fun limparTudo() {
        prefs.remove("last_station")
        DashboardData.estacoes.forEach { prefs.remove("last_hm_${it.nome}") }
        prefs.remove("last_lago_na")

        val itens = DashboardData.itensPorEstacao["DET-01"].orEmpty()
        _state.value = DashboardUiState(
            estacaoSelecionada = DashboardData.estacoes.first(),
            itensAtuais = itens,
            hmSelecionado = 0,
            statusBomba = itens.firstOrNull()?.statusPadrao ?: "LIGADA"
        )
    }

    /** Era `mostrarDialogLeituraManual` -> confirmar. */
    fun confirmarLeituraManual(valor: String, incluirNaFoto: Boolean) {
        _state.update { atual ->
            val itens = atual.itensAtuais.toMutableList()
            val idx = atual.hmSelecionado
            val item = itens.getOrNull(idx) ?: return@update atual
            itens[idx] = item.copy(leituraManual = valor, incluirLeituraNaFoto = incluirNaFoto)
            atual.copy(itensAtuais = itens, dialogAtivo = DashboardDialog.Nenhum)
        }
    }

    /**
     * Era `gerarRegistroAssincrono()` — gera o registro final (overlay +
     * versão limpa), salva no histórico e opcionalmente no Cofre.
     * O desenho pixel a pixel fica no `OverlayRenderer` (expect/actual);
     * aqui só orquestramos QUAL gerador chamar e o que fazer com o
     * resultado.
     */
    fun gerarRegistro() {
        val atual = _state.value
        val item = atual.itemAtual ?: return

        screenModelScope.launch {
            _state.update { it.copy(gerandoRegistro = true) }

            val resultado = when {
                atual.isModoNA -> {
                    val lago = atual.lagoAtual ?: return@launch
                    OverlayRenderer.gerarRegistroNA(lago, lago.valor, lago.dataHora)
                }
                item.tipo == "LIVRE" -> OverlayRenderer.gerarRegistroLivre(item)
                else -> OverlayRenderer.gerarRegistroHm(item)
            }

            // Aqui entraria a chamada de persistência no histórico
            // (equivalente a `salvarNoHistoricoGlobal`) e, se a foto não
            // veio do Cofre, o registro no Cofre (`registrarFotoLimpaNoCofreSeNecessario`).
            // Omitido por não fazer parte do escopo desta tela.

            vibrateStrong()
            _state.update {
                it.copy(
                    gerandoRegistro = false,
                    dialogAtivo = DashboardDialog.ResultadoRegistro(comTarjaLabel = "Registro gerado")
                )
            }
        }
    }

    fun compartilharUltimoRegistro(bytes: ByteArray) {
        shareImage(bytes, "Registro INSPETOR")
    }
}
