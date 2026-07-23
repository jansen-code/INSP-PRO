package com.raylson.jansen.inspetor

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CofreActivity — agora TUDO acontece numa tela só: os 3 grupos (DET-01 /
 * N.A. / ARB'S) são botões seletores fixos no topo (como um "segmented
 * control"), e tocar em um deles troca as fotos exibidas embaixo, sem
 * nunca abrir uma segunda tela/"entrar" numa pasta. N.A. começa
 * selecionado por padrão.
 */
class CofreActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tvTitulo: TextView
    private lateinit var tvSubtitulo: TextView
    private lateinit var btnVoltarTopo: ImageView
    private lateinit var btnAcaoPrincipal: View
    private lateinit var btnSelecionarTudo: View
    private lateinit var btnCompartilharSelecao: View
    private lateinit var btnExcluirSelecao: View
    private lateinit var tvTextoAcaoPrincipal: TextView
    private lateinit var tvTextoSelecionarTudo: TextView
    private lateinit var btnFabFechar: FloatingActionButton
    private lateinit var layoutVazio: View
    private lateinit var tvVazio: TextView

    // ═══ Os 3 botões seletores de grupo (substituem as antigas "pastas") ═══
    private lateinit var chipDet01: CardView
    private lateinit var chipNa: CardView
    private lateinit var chipArbs: CardView
    private lateinit var tvQtdDet01: TextView
    private lateinit var tvQtdNa: TextView
    private lateinit var tvQtdArbs: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val caminhosSelecionados = linkedSetOf<String>()

    private var grupoAtual: CofreManager.Grupo = CofreManager.Grupo.NA
    private var fotoAdapterAtual: CofreFotoAdapter? = null
    private var itensRawAtuais: MutableList<CofreManager.ItemCofre> = mutableListOf()
    private var itensMixAtuais: MutableList<Any> = mutableListOf() // Headers + Fotos
    private var emModoMultiSelecao = false
    private var modoSelecao = false

    companion object {
        const val EXTRA_MODO_SELECAO = "modo_selecao"
        const val EXTRA_CAMINHO_SELECIONADO = "caminho_selecionado"
        private const val REQUEST_VISUALIZADOR_SELECAO = 771
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cofre)

        modoSelecao = intent.getBooleanExtra(EXTRA_MODO_SELECAO, false)

        // ═══ Lembra qual grupo estava selecionado da última vez que essa
        // tela foi aberta (N.A. continua sendo o padrão só na primeiríssima
        // vez, antes de existir qualquer preferência salva). ═══
        val prefsInicial = SecurePrefs.get(this, "cofre_prefs")
        val nomeGrupoSalvo = prefsInicial.getString("grupo_atual", null)
        grupoAtual = nomeGrupoSalvo?.let { nome -> CofreManager.Grupo.values().find { it.name == nome } } ?: CofreManager.Grupo.NA

        recycler = findViewById(R.id.recyclerCofre)
        tvTitulo = findViewById(R.id.tvTituloCofre)
        tvSubtitulo = findViewById(R.id.tvSubtituloCofre)
        btnVoltarTopo = findViewById(R.id.btnVoltarCofre)
        layoutVazio = findViewById(R.id.layoutVazioCofre)
        tvVazio = findViewById(R.id.tvVazioCofre)
        btnAcaoPrincipal = findViewById(R.id.btnHistoricoCofre)
        btnSelecionarTudo = findViewById(R.id.btnSelecionarTudoCofre)
        btnCompartilharSelecao = findViewById(R.id.btnCompartilharCofre)
        btnExcluirSelecao = findViewById(R.id.btnExcluirSelecaoCofre)
        tvTextoAcaoPrincipal = findViewById(R.id.tvTextoAcaoPrincipalCofre)
        tvTextoSelecionarTudo = findViewById(R.id.tvTextoSelecionarTudoCofre)
        btnFabFechar = findViewById(R.id.btnFecharCofreFab)

        chipDet01 = findViewById(R.id.btnGrupoDet01Cofre)
        chipNa = findViewById(R.id.btnGrupoNaCofre)
        chipArbs = findViewById(R.id.btnGrupoArbsCofre)
        tvQtdDet01 = findViewById(R.id.tvQtdGrupoDet01Cofre)
        tvQtdNa = findViewById(R.id.tvQtdGrupoNaCofre)
        tvQtdArbs = findViewById(R.id.tvQtdGrupoArbsCofre)

        if (modoSelecao) {
            tvTitulo.text = "SELECIONAR DA GALERIA 2"
            tvSubtitulo.text = "Toque para usar esta foto"
        } else {
            atualizarTextoSelecao()
        }

        val clickVoltar = View.OnClickListener { tratarVoltar() }
        btnVoltarTopo.setOnClickListener(clickVoltar)
        // ═══ O antigo botão "Voltar" do rodapé agora abre o HISTÓRICO
        // (mesmo ícone de relógio do dashboard). Voltar/cancelar seleção
        // continua disponível pela seta no cabeçalho e pelo X. ═══
        btnAcaoPrincipal.setOnClickListener { startActivity(Intent(this, HistoricoActivity::class.java)) }
        btnFabFechar.setOnClickListener { finish() }

        btnSelecionarTudo.setOnClickListener { onClicarLimpar() }
        btnCompartilharSelecao.setOnClickListener { onClicarEnviar() }
        btnExcluirSelecao.setOnClickListener { onClicarExcluir() }

        configurarChipSeletor(chipDet01, CofreManager.Grupo.DET01)
        configurarChipSeletor(chipNa, CofreManager.Grupo.NA)
        configurarChipSeletor(chipArbs, CofreManager.Grupo.ARBS)
        atualizarEstiloChips()

        atualizarContagensChips()
        carregarGrupo(grupoAtual, preservarScroll = false)
        recycler.post { restaurarScroll(grupoAtual) }
    }

    override fun onResume() {
        super.onResume()
        atualizarContagensChips()
        // preservarScroll = true: ao voltar do carrossel de fotos, mantém
        // a posição de rolagem exatamente onde o usuário parou.
        carregarGrupo(grupoAtual, preservarScroll = true)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BOTÕES SELETORES (DET-01 / N.A. / ARB'S)
    //  Mesmo efeito de "afundar" ao tocar já usado nos cards de estação
    //  da tela principal (scaleX/Y 0.94 + alpha 0.7 na descida, e volta
    //  com um pequeno "salto" na subida).
    // ═══════════════════════════════════════════════════════════════════
    private fun configurarChipSeletor(chip: CardView, grupo: CofreManager.Grupo) {
        chip.setOnClickListener { selecionarGrupo(grupo) }
        chip.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> animarPressDown(v)
                MotionEvent.ACTION_UP -> { animarPressUp(v); v.performClick() }
                MotionEvent.ACTION_CANCEL -> animarPressUp(v)
            }
            true
        }
    }

    private fun animarPressDown(view: View) {
        view.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.7f).setDuration(80L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun animarPressUp(view: View) {
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100L)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    private fun selecionarGrupo(grupo: CofreManager.Grupo) {
        if (grupo == grupoAtual) return
        salvarPosicaoScroll(grupoAtual)
        limparSelecao(reatualizar = false)
        grupoAtual = grupo
        salvarGrupoAtual()
        atualizarEstiloChips()
        carregarGrupo(grupo, preservarScroll = false)
        recycler.post { restaurarScroll(grupo) }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Persistência: guarda o grupo ativo e a posição de rolagem de CADA
    //  grupo, pra tela voltar exatamente como foi deixada da última vez —
    //  mesmo se a Activity for fechada e reaberta do zero.
    // ═══════════════════════════════════════════════════════════════════
    private fun salvarGrupoAtual() {
        SecurePrefs.get(this, "cofre_prefs").edit().putString("grupo_atual", grupoAtual.name).apply()
    }

    private fun salvarPosicaoScroll(grupo: CofreManager.Grupo) {
        val lm = recycler.layoutManager as? GridLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos == RecyclerView.NO_POSITION) return
        val offset = lm.findViewByPosition(pos)?.top ?: 0
        SecurePrefs.get(this, "cofre_prefs").edit()
            .putInt("scroll_pos_${grupo.name}", pos)
            .putInt("scroll_off_${grupo.name}", offset)
            .apply()
    }

    private fun restaurarScroll(grupo: CofreManager.Grupo) {
        val lm = recycler.layoutManager as? GridLayoutManager ?: return
        val prefs = SecurePrefs.get(this, "cofre_prefs")
        val pos = prefs.getInt("scroll_pos_${grupo.name}", 0)
        val offset = prefs.getInt("scroll_off_${grupo.name}", 0)
        val limite = (fotoAdapterAtual?.itemCount ?: 0) - 1
        if (limite < 0) return
        lm.scrollToPositionWithOffset(pos.coerceIn(0, limite), offset)
    }

    override fun onPause() {
        super.onPause()
        salvarGrupoAtual()
        salvarPosicaoScroll(grupoAtual)
    }

    private fun atualizarEstiloChips() {
        fun aplicar(chip: CardView, grupo: CofreManager.Grupo) {
            val ativo = grupo == grupoAtual
            chip.cardElevation = if (ativo) dp(6).toFloat() else 0f
            chip.alpha = if (ativo) 1f else 0.55f
        }
        aplicar(chipDet01, CofreManager.Grupo.DET01)
        aplicar(chipNa, CofreManager.Grupo.NA)
        aplicar(chipArbs, CofreManager.Grupo.ARBS)
    }

    private fun atualizarContagensChips() {
        tvQtdDet01.text = formatarQtd(CofreManager.contarPorGrupo(this, CofreManager.Grupo.DET01))
        tvQtdNa.text = formatarQtd(CofreManager.contarPorGrupo(this, CofreManager.Grupo.NA))
        tvQtdArbs.text = formatarQtd(CofreManager.contarPorGrupo(this, CofreManager.Grupo.ARBS))
    }

    private fun formatarQtd(qtd: Int) = if (qtd == 1) "1 foto" else "$qtd fotos"

    private fun tratarVoltar() {
        if (emModoMultiSelecao) limparSelecao(reatualizar = true) else finish()
    }

    override fun onBackPressed() = tratarVoltar()

    // ═══════════════════════════════════════════════════════════════════
    //  Carrega/atualiza o grid do grupo selecionado, NA MESMA TELA.
    //  preservarScroll = true reaproveita o adapter/LayoutManager já
    //  existentes (só atualiza os dados por baixo) — NÃO reseta a posição
    //  do scroll. Usado ao voltar do carrossel de fotos.
    //  preservarScroll = false monta tudo do zero — usado ao trocar de
    //  grupo, onde é esperado que a rolagem volte pro topo.
    // ═══════════════════════════════════════════════════════════════════
    private fun carregarGrupo(grupo: CofreManager.Grupo, preservarScroll: Boolean) {
        itensRawAtuais = CofreManager.listarPorGrupo(this, grupo).toMutableList()

        if (itensRawAtuais.isEmpty()) {
            recycler.visibility = View.GONE
            layoutVazio.visibility = View.VISIBLE
            tvVazio.text = "Nenhuma foto em ${grupo.rotulo} ainda."
            fotoAdapterAtual = null
            atualizarBarraInferior()
            return
        }

        // ═══ PERFORMANCE: assim que a lista é conhecida, dispara em
        // background (fora da main thread) a geração de TODAS as
        // miniaturas desse grupo — inclusive as que ainda não apareceram
        // na tela. Isso é o que resolve os engasgos ao rolar: quando o
        // usuário chegar lá embaixo, a miniatura já foi gerada e está no
        // cache em disco, só falta ler o JPEG pequeno (rápido) em vez de
        // decodificar a foto original inteira na hora. ═══
        val itensParaAquecer = itensRawAtuais.toList()
        scope.launch(Dispatchers.IO) {
            CofreManager.pregerarMiniaturas(this@CofreActivity, itensParaAquecer)
        }

        layoutVazio.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        // Agrupa as fotos por Subpasta (Ex: "BOMBA-01", "HIDROMETRO-01")
        val agrupado = itensRawAtuais.groupBy { it.subpasta }
        val novoMix = mutableListOf<Any>()

        val chavesOrdenadas = agrupado.keys.sortedWith { a, b ->
            if (a == "ARQUIVO ANTIGO") 1 else if (b == "ARQUIVO ANTIGO") -1 else a.compareTo(b)
        }
        for (chave in chavesOrdenadas) {
            novoMix.add(chave)
            novoMix.addAll(agrupado[chave] ?: emptyList())
        }

        val adapterExistente = fotoAdapterAtual
        if (preservarScroll && adapterExistente != null) {
            itensMixAtuais.clear()
            itensMixAtuais.addAll(novoMix)
            adapterExistente.notifyDataSetChanged()
        } else {
            itensMixAtuais = novoMix
            recycler.setPadding(dp(6), recycler.paddingTop, dp(6), recycler.paddingBottom)

            val larguraDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            val colunas = (larguraDp / 88f).toInt().coerceIn(2, 6)
            val layoutManager = GridLayoutManager(this, colunas)
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (itensMixAtuais[position] is String) colunas else 1
                }
            }
            recycler.layoutManager = layoutManager

            val adapter = CofreFotoAdapter(
                itensMix = itensMixAtuais,
                emModoSelecao = { emModoMultiSelecao },
                estaSelecionado = { item -> caminhosSelecionados.contains(item.arquivo.absolutePath) },
                onAbrir = { item, imgView -> abrirFoto(item, imgView) },
                onLongPressOuToggleSelecao = { item -> if (!modoSelecao) alternarSelecao(item) }
            )
            fotoAdapterAtual = adapter
            recycler.adapter = adapter
            recycler.overScrollMode = View.OVER_SCROLL_NEVER

            // ═══ PERFORMANCE:
            // - setHasFixedSize(true): o tamanho do RecyclerView não muda
            //   quando os dados mudam, evita re-medir/re-layoutar a tela
            //   toda a cada notifyDataSetChanged.
            // - setItemViewCacheSize: guarda mais views recém-saídas da
            //   tela já prontas (evita re-inflar/re-decodificar ao rolar
            //   pra frente e pra trás perto da mesma região).
            // - RecycledViewPool maior: fast-fling recicla várias views de
            //   uma vez só; o padrão (5) é pouco pra um grid de fotos. ═══
            recycler.setHasFixedSize(true)
            recycler.setItemViewCacheSize(24)
            recycler.recycledViewPool.setMaxRecycledViews(CofreFotoAdapter.TIPO_FOTO, 24)
        }

        atualizarBarraInferior()
    }

    // ═══ Abre o carrossel de fotos passando TODAS as fotos do grupo
    // atual, na mesma ordem visual do grid, + a posição inicial — dá pra
    // folhear com swipe pro lado sem precisar voltar pra lista.
    //
    // MUDANÇA: mesmo em modoSelecao (Dashboard pedindo pra escolher uma
    // foto), NÃO retornamos mais na hora do toque. Agora abrimos o
    // carrossel em tela cheia igual ao uso normal — o usuário folheia as
    // fotos à vontade e só confirma a escolha explicitamente no botão
    // "PEGAR IMAGEM" de dentro do carrossel. Por isso usamos
    // startActivityForResult aqui: quando o carrossel volta com uma foto
    // escolhida, repassamos esse mesmo resultado pra cima (pro Dashboard). ═══
    private fun abrirFoto(item: CofreManager.ItemCofre, imgView: ImageView) {
        if (emModoMultiSelecao) { alternarSelecao(item); return }

        val caminhosOrdenados = ArrayList(itensMixAtuais.filterIsInstance<CofreManager.ItemCofre>().map { it.arquivo.absolutePath })
        val posicaoInicial = caminhosOrdenados.indexOf(item.arquivo.absolutePath).coerceAtLeast(0)

        val intent = Intent(this, CofreVisualizadorActivity::class.java).apply {
            putExtra(CofreVisualizadorActivity.EXTRA_GRUPO, item.grupo.name)
            putStringArrayListExtra(CofreVisualizadorActivity.EXTRA_CAMINHOS, caminhosOrdenados)
            putExtra(CofreVisualizadorActivity.EXTRA_POSICAO_INICIAL, posicaoInicial)
            putExtra(CofreVisualizadorActivity.EXTRA_MODO_SELECAO, modoSelecao)
        }
        val options = ActivityOptionsCompat.makeScaleUpAnimation(imgView, 0, 0, imgView.width, imgView.height)

        if (modoSelecao) {
            startActivityForResult(intent, REQUEST_VISUALIZADOR_SELECAO, options.toBundle())
        } else {
            startActivity(intent, options.toBundle())
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VISUALIZADOR_SELECAO && resultCode == RESULT_OK) {
            // O carrossel confirmou uma foto em "PEGAR IMAGEM" — repassa o
            // mesmo resultado pra cima (quem chamou o Cofre, ex: Dashboard).
            val caminho = data?.getStringExtra(EXTRA_CAMINHO_SELECIONADO)
            if (caminho != null) {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_CAMINHO_SELECIONADO, caminho))
                finish()
            }
        }
    }

    private fun alternarSelecao(item: CofreManager.ItemCofre) {
        val path = item.arquivo.absolutePath
        if (caminhosSelecionados.contains(path)) caminhosSelecionados.remove(path) else caminhosSelecionados.add(path)
        emModoMultiSelecao = caminhosSelecionados.isNotEmpty()
        atualizarTextoSelecao()
        fotoAdapterAtual?.notifyDataSetChanged()
        atualizarBarraInferior()
    }

    private fun limparSelecao(reatualizar: Boolean) {
        caminhosSelecionados.clear()
        emModoMultiSelecao = false
        atualizarTextoSelecao()
        if (reatualizar) fotoAdapterAtual?.notifyDataSetChanged()
        atualizarBarraInferior()
    }

    private fun atualizarTextoSelecao() {
        if (modoSelecao) return
        tvSubtitulo.text = if (emModoMultiSelecao) "${caminhosSelecionados.size} ${if (caminhosSelecionados.size == 1) "foto selecionada" else "fotos selecionadas"}"
                           else "Toque para abrir • segure para selecionar várias"
    }

    // ═══ Botão "Limpar" (antigo "Todas"): sempre com esse rótulo.
    // Se nem tudo estiver selecionado, pergunta antes de marcar tudo de
    // uma vez. Se já estiver tudo selecionado, apenas limpa (sem precisar
    // perguntar, já que limpar não é destrutivo). ═══
    private fun onClicarLimpar() {
        if (modoSelecao || itensRawAtuais.isEmpty()) return

        if (caminhosSelecionados.size == itensRawAtuais.size) {
            limparSelecao(reatualizar = true)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Selecionar todas?")
            .setMessage("Selecionar as ${itensRawAtuais.size} fotos de ${grupoAtual.rotulo}?")
            .setPositiveButton("SELECIONAR") { dialog, _ ->
                dialog.dismiss()
                caminhosSelecionados.apply { clear(); addAll(itensRawAtuais.map { it.arquivo.absolutePath }) }
                emModoMultiSelecao = caminhosSelecionados.isNotEmpty()
                atualizarTextoSelecao()
                fotoAdapterAtual?.notifyDataSetChanged()
                atualizarBarraInferior()
            }
            .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun onClicarEnviar() {
        if (caminhosSelecionados.isEmpty()) {
            Toast.makeText(this, "Selecione ao menos uma foto para enviar.", Toast.LENGTH_SHORT).show()
            return
        }
        compartilharSelecionados()
    }

    private fun onClicarExcluir() {
        if (caminhosSelecionados.isEmpty()) {
            Toast.makeText(this, "Selecione ao menos uma foto para excluir.", Toast.LENGTH_SHORT).show()
            return
        }
        confirmarExclusaoEmLote()
    }

    private fun compartilharSelecionados() {
        if (!emModoMultiSelecao) return
        val selecionados = itensRawAtuais.filter { caminhosSelecionados.contains(it.arquivo.absolutePath) }
        if (selecionados.isEmpty()) return

        btnCompartilharSelecao.isEnabled = false
        scope.launch {
            val uris = withContext(Dispatchers.IO) {
                selecionados.mapIndexedNotNull { index, item -> criarUriCompartilhamento(item, index) }
            }
            btnCompartilharSelecao.isEnabled = true
            if (uris.isEmpty()) {
                Toast.makeText(this@CofreActivity, "Falha no compartilhamento.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = ClipData.newUri(contentResolver, "Fotos do Cofre", uris.first())
                for (i in 1 until uris.size) clipData?.addItem(ClipData.Item(uris[i]))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar fotos"))
        }
    }

    private fun criarUriCompartilhamento(item: CofreManager.ItemCofre, index: Int): Uri? {
        return try {
            val arquivoTemp = File(cacheDir, "cofre_multi_${item.timestampMillis}_$index.jpg")
            if (!arquivoTemp.exists() || arquivoTemp.length() == 0L || arquivoTemp.lastModified() < item.arquivo.lastModified()) {
                CofreManager.exportarComoJpegHighQuality(item.arquivo, arquivoTemp) ?: return null
            }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivoTemp)
        } catch (e: Exception) { null }
    }

    private fun confirmarExclusaoEmLote() {
        if (!emModoMultiSelecao) return
        val selecionados = itensRawAtuais.filter { caminhosSelecionados.contains(it.arquivo.absolutePath) }
        if (selecionados.isEmpty()) return

        AlertDialog.Builder(this).setTitle("Excluir ${selecionados.size} fotos?")
            .setMessage("As fotos selecionadas serão apagadas do Cofre permanentemente.")
            .setPositiveButton("EXCLUIR") { dialog, _ ->
                dialog.dismiss()
                selecionados.forEach { CofreManager.excluir(it) }
                limparSelecao(reatualizar = false)
                atualizarContagensChips()
                carregarGrupo(grupoAtual, preservarScroll = true)
            }.setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }.show()
    }

    private fun atualizarBarraInferior() {
        val podeMostrarAcoes = !modoSelecao
        val temSelecao = caminhosSelecionados.isNotEmpty()

        btnSelecionarTudo.visibility = if (podeMostrarAcoes) View.VISIBLE else View.INVISIBLE
        btnCompartilharSelecao.visibility = if (podeMostrarAcoes) View.VISIBLE else View.GONE
        btnExcluirSelecao.visibility = if (podeMostrarAcoes) View.VISIBLE else View.GONE
        btnCompartilharSelecao.alpha = if (temSelecao) 1f else 0.4f
        btnExcluirSelecao.alpha = if (temSelecao) 1f else 0.4f

        tvTextoSelecionarTudo.text = "Limpar"
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()
    override fun onDestroy() { super.onDestroy(); fotoAdapterAtual?.encerrar(); scope.cancel() }
}
