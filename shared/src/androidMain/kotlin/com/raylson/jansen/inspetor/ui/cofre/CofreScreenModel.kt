package com.raylson.jansen.inspetor.ui.cofre

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import cafe.adriel.voyager.core.model.ScreenModel
import com.raylson.jansen.inspetor.CofreManager
import com.raylson.jansen.inspetor.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class CofreState(
    val grupoAtual: CofreManager.Grupo = CofreManager.Grupo.NA,
    val itens: List<CofreManager.ItemCofre> = emptyList(),
    val itensMix: List<Any> = emptyList(),
    val contagens: Map<CofreManager.Grupo, Int> = mapOf(
        CofreManager.Grupo.DET01 to 0,
        CofreManager.Grupo.NA to 0,
        CofreManager.Grupo.ARBS to 0
    ),
    val caminhosSelecionados: Set<String> = emptySet(),
    val emModoMultiSelecao: Boolean = false,
    val titulo: String = "GALERIA 2",
    val subtitulo: String = "Toque para abrir \u2022 segure para selecionar v\u00E1rias"
)

class CofreScreenModel(private val appContext: Context) : ScreenModel {

    private val _state = MutableStateFlow(CofreState())
    val state: StateFlow<CofreState> = _state

    private var itensRawAtuais: List<CofreManager.ItemCofre> = emptyList()

    init {
        val prefs = SecurePrefs.get(appContext, "cofre_prefs")
        val nomeGrupoSalvo = prefs.getString("grupo_atual", null)
        val grupoInicial = nomeGrupoSalvo?.let { nome ->
            CofreManager.Grupo.values().find { it.name == nome }
        } ?: CofreManager.Grupo.NA
        _state.update { it.copy(grupoAtual = grupoInicial) }
        atualizarContagens()
        carregarGrupo(grupoInicial)
    }

    fun selecionarGrupo(grupo: CofreManager.Grupo) {
        if (grupo == _state.value.grupoAtual) return
        limparSelecao()
        _state.update { it.copy(grupoAtual = grupo) }
        salvarGrupoAtual(grupo)
        carregarGrupo(grupo)
    }

    fun toggleSelecao(item: CofreManager.ItemCofre) {
        val path = item.arquivo.absolutePath
        val atual = _state.value.caminhosSelecionados.toMutableSet()
        if (atual.contains(path)) atual.remove(path) else atual.add(path)
        val emModo = atual.isNotEmpty()
        _state.update {
            it.copy(
                caminhosSelecionados = atual,
                emModoMultiSelecao = emModo,
                subtitulo = if (emModo) "${atual.size} ${if (atual.size == 1) "foto selecionada" else "fotos selecionadas"}"
                           else "Toque para abrir \u2022 segure para selecionar v\u00E1rias"
            )
        }
    }

    fun limparSelecao() {
        _state.update {
            it.copy(
                caminhosSelecionados = emptySet(),
                emModoMultiSelecao = false,
                subtitulo = "Toque para abrir \u2022 segure para selecionar v\u00E1rias"
            )
        }
    }

    fun selecionarTudo() {
        val atual = _state.value
        if (atual.emModoMultiSelecao && atual.caminhosSelecionados.size == itensRawAtuais.size) {
            limparSelecao()
            return
        }
        val todos = itensRawAtuais.map { it.arquivo.absolutePath }.toSet()
        _state.update {
            it.copy(
                caminhosSelecionados = todos,
                emModoMultiSelecao = true,
                subtitulo = "${todos.size} ${if (todos.size == 1) "foto selecionada" else "fotos selecionadas"}"
            )
        }
    }

    fun podeCompartilhar(): Boolean = _state.value.caminhosSelecionados.isNotEmpty()
    fun podeExcluir(): Boolean = _state.value.caminhosSelecionados.isNotEmpty()

    fun criarIntentCompartilhamento(): Intent? {
        val selecionados = itensRawAtuais.filter { _state.value.caminhosSelecionados.contains(it.arquivo.absolutePath) }
        if (selecionados.isEmpty()) return null

        val uris = selecionados.mapNotNull { item ->
            try {
                val arquivoTemp = File(appContext.cacheDir, "cofre_multi_${item.timestampMillis}_${selecionados.indexOf(item)}.jpg")
                if (!arquivoTemp.exists() || arquivoTemp.length() == 0L || arquivoTemp.lastModified() < item.arquivo.lastModified()) {
                    CofreManager.exportarComoJpegHighQuality(item.arquivo, arquivoTemp) ?: return@mapNotNull null
                }
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", arquivoTemp)
            } catch (e: Exception) { null }
        }

        if (uris.isEmpty()) return null

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newUri(appContext.contentResolver, "Fotos do Cofre", uris.first())
            for (i in 1 until uris.size) clipData?.addItem(ClipData.Item(uris[i]))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun excluirSelecionados() {
        val selecionados = itensRawAtuais.filter { _state.value.caminhosSelecionados.contains(it.arquivo.absolutePath) }
        selecionados.forEach { CofreManager.excluir(it) }
        limparSelecao()
        atualizarContagens()
        carregarGrupo(_state.value.grupoAtual)
    }

    fun obterCaminhoItem(item: CofreManager.ItemCofre): String = item.arquivo.absolutePath

    fun listarCaminhosParaVisualizador(): ArrayList<String> {
        return ArrayList(itensRawAtuais.map { it.arquivo.absolutePath })
    }

    fun obterPosicaoInicial(item: CofreManager.ItemCofre): Int {
        return itensRawAtuais.indexOf(item).coerceAtLeast(0)
    }

    fun recarregar() {
        atualizarContagens()
        carregarGrupo(_state.value.grupoAtual)
    }

    private fun carregarGrupo(grupo: CofreManager.Grupo) {
        itensRawAtuais = CofreManager.listarPorGrupo(appContext, grupo)

        if (itensRawAtuais.isEmpty()) {
            _state.update {
                it.copy(
                    itens = emptyList(),
                    itensMix = emptyList(),
                    subtitulo = "Nenhuma foto em ${grupo.rotulo} ainda."
                )
            }
            return
        }

        val agrupado = itensRawAtuais.groupBy { it.subpasta }
        val novoMix = mutableListOf<Any>()
        val chavesOrdenadas = agrupado.keys.sortedWith { a, b ->
            if (a == "ARQUIVO ANTIGO") 1 else if (b == "ARQUIVO ANTIGO") -1 else a.compareTo(b)
        }
        for (chave in chavesOrdenadas) {
            novoMix.add(chave)
            novoMix.addAll(agrupado[chave] ?: emptyList())
        }

        _state.update {
            it.copy(
                itens = itensRawAtuais,
                itensMix = novoMix
            )
        }
    }

    private fun atualizarContagens() {
        _state.update {
            it.copy(
                contagens = mapOf(
                    CofreManager.Grupo.DET01 to CofreManager.contarPorGrupo(appContext, CofreManager.Grupo.DET01),
                    CofreManager.Grupo.NA to CofreManager.contarPorGrupo(appContext, CofreManager.Grupo.NA),
                    CofreManager.Grupo.ARBS to CofreManager.contarPorGrupo(appContext, CofreManager.Grupo.ARBS)
                )
            )
        }
    }

    private fun salvarGrupoAtual(grupo: CofreManager.Grupo) {
        SecurePrefs.get(appContext, "cofre_prefs").edit().putString("grupo_atual", grupo.name).apply()
    }
}
