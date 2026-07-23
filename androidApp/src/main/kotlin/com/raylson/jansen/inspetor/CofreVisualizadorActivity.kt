package com.raylson.jansen.inspetor

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CofreVisualizadorActivity — agora um CARROSSEL: arraste a foto pro lado
 * ou pro outro para folhear as demais fotos do mesmo grupo, com zoom/pan
 * individual em cada uma (pinça ou duplo toque). Compartilhar/Excluir
 * sempre agem sobre a foto atualmente visível.
 */
class CofreVisualizadorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GRUPO = "grupo"
        const val EXTRA_CAMINHOS = "caminhos"
        const val EXTRA_POSICAO_INICIAL = "posicao_inicial"
        // Mantido por compatibilidade, caso algo ainda chame passando um único caminho.
        const val EXTRA_CAMINHO_INICIAL = "caminho_inicial"
        // ═══ NOVO: quando true, o carrossel abre em "modo escolha" — some
        // com Compartilhar/Excluir e mostra o botão PEGAR IMAGEM, que
        // devolve a foto atualmente visível pra tela que chamou o Cofre. ═══
        const val EXTRA_MODO_SELECAO = "modo_selecao"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var recycler: RecyclerView
    private lateinit var tvRotulo: TextView
    private lateinit var adapter: VisualizadorPagerAdapter
    private var grupo: CofreManager.Grupo? = null
    private var posicaoAtual = 0
    private var modoSelecao = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cofre_visualizador)

        recycler = findViewById(R.id.recyclerVisualizadorCofre)
        tvRotulo = findViewById(R.id.tvRotuloVisualizador)
        val btnFechar = findViewById<View>(R.id.btnFecharVisualizador)
        val btnCompartilhar = findViewById<View>(R.id.btnCompartilharVisualizador)
        val btnExcluir = findViewById<View>(R.id.btnExcluirVisualizador)
        val btnPegarImagem = findViewById<View>(R.id.btnPegarImagemVisualizador)

        modoSelecao = intent.getBooleanExtra(EXTRA_MODO_SELECAO, false)

        // ═══ Em modo de escolha (vindo do Dashboard), some com as ações
        // de gerenciamento do Cofre (compartilhar/excluir) — o usuário só
        // está aqui pra ESCOLHER uma foto, não pra mexer no acervo — e
        // mostra o botão PEGAR IMAGEM, que confirma a foto atual do
        // carrossel. ═══
        if (modoSelecao) {
            btnCompartilhar.visibility = View.GONE
            btnExcluir.visibility = View.GONE
            btnPegarImagem.visibility = View.VISIBLE
            btnPegarImagem.setOnClickListener { confirmarEscolhaAtual() }
        }

        btnFechar.setOnClickListener { supportFinishAfterTransition() }

        val grupoNome = intent.getStringExtra(EXTRA_GRUPO)
        grupo = grupoNome?.let { nome -> CofreManager.Grupo.values().find { it.name == nome } }

        val caminhos = intent.getStringArrayListExtra(EXTRA_CAMINHOS)
            ?: intent.getStringExtra(EXTRA_CAMINHO_INICIAL)?.let { arrayListOf(it) }
        val posicaoInicial = intent.getIntExtra(EXTRA_POSICAO_INICIAL, 0)

        if (grupo == null || caminhos.isNullOrEmpty()) {
            Toast.makeText(this, "Não foi possível abrir a foto.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        PagerSnapHelper().attachToRecyclerView(recycler)

        adapter = VisualizadorPagerAdapter(mutableListOf())
        recycler.adapter = adapter

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return
                    val pos = lm.findFirstCompletelyVisibleItemPosition().takeIf { it != RecyclerView.NO_POSITION }
                        ?: lm.findFirstVisibleItemPosition()
                    if (pos != RecyclerView.NO_POSITION && pos != posicaoAtual) {
                        posicaoAtual = pos
                        atualizarRotulo()
                    }
                }
            }
        })

        btnCompartilhar.setOnClickListener { compartilharFotoAtual() }
        btnExcluir.setOnClickListener { confirmarExclusao() }

        scope.launch {
            val g = grupo!!
            val itens = withContext(Dispatchers.IO) {
                val todos = CofreManager.listarPorGrupo(this@CofreVisualizadorActivity, g)
                val porCaminho = todos.associateBy { it.arquivo.absolutePath }
                caminhos.mapNotNull { porCaminho[it] }
            }

            if (itens.isEmpty()) {
                Toast.makeText(this@CofreVisualizadorActivity, "Essas fotos não existem mais.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            adapter.atualizarItens(itens)
            posicaoAtual = posicaoInicial.coerceIn(0, itens.size - 1)
            recycler.scrollToPosition(posicaoAtual)
            atualizarRotulo()
        }
    }

    // ═══ Confirma a foto ATUALMENTE visível no carrossel como a escolhida
    // — devolve pra CofreActivity, que repassa pra quem chamou o Cofre
    // (ex: Dashboard), no mesmo formato que o modo de seleção antigo usava. ═══
    private fun confirmarEscolhaAtual() {
        val item = adapter.itemNaPosicao(posicaoAtual) ?: return
        setResult(RESULT_OK, Intent().putExtra(CofreActivity.EXTRA_CAMINHO_SELECIONADO, item.arquivo.absolutePath))
        supportFinishAfterTransition()
    }

    private fun atualizarRotulo() {
        val item = adapter.itemNaPosicao(posicaoAtual) ?: return
        tvRotulo.text = item.rotuloExibicao
    }

    private fun compartilharFotoAtual() {
        val item = adapter.itemNaPosicao(posicaoAtual) ?: return
        scope.launch {
            val uri = withContext(Dispatchers.IO) { prepararUriCompartilhamento(item) }
            if (uri == null) {
                Toast.makeText(this@CofreVisualizadorActivity, "Não foi possível compartilhar essa foto.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intentCompartilhar = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "Foto do Cofre", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intentCompartilhar, "Compartilhar foto"))
        }
    }

    private fun prepararUriCompartilhamento(item: CofreManager.ItemCofre): Uri? {
        return try {
            val arquivoTemp = File(cacheDir, "cofre_share_${item.timestampMillis}.jpg")
            if (!arquivoTemp.exists() || arquivoTemp.length() == 0L || arquivoTemp.lastModified() < item.arquivo.lastModified()) {
                CofreManager.exportarComoJpegHighQuality(item.arquivo, arquivoTemp) ?: return null
            }
            FileProvider.getUriForFile(this@CofreVisualizadorActivity, "$packageName.fileprovider", arquivoTemp)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun confirmarExclusao() {
        val item = adapter.itemNaPosicao(posicaoAtual) ?: return
        AlertDialog.Builder(this)
            .setTitle("Excluir foto?")
            .setMessage("Essa foto será apagada do Cofre permanentemente.")
            .setPositiveButton("EXCLUIR") { dialog, _ ->
                dialog.dismiss()
                CofreManager.excluir(item)
                val restantes = adapter.removerNaPosicao(posicaoAtual)
                if (restantes == 0) {
                    supportFinishAfterTransition()
                } else {
                    posicaoAtual = posicaoAtual.coerceAtMost(restantes - 1)
                    recycler.scrollToPosition(posicaoAtual)
                    atualizarRotulo()
                }
            }
            .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ═══ O botão X já fecha com a animação reversa (zoom de volta pro
    // ponto de origem). O botão/gesto de voltar do sistema não usava essa
    // mesma transição — usa também, pro fechamento ficar simétrico com a
    // abertura, não importa como a tela seja fechada. ═══
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        supportFinishAfterTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Adapter do carrossel — uma página por foto. Carrega o bitmap em
    //  segundo plano e só então aplica o zoom/pan (evita travar a UI).
    // ═══════════════════════════════════════════════════════════════════
    private inner class VisualizadorPagerAdapter(
        private var itens: MutableList<CofreManager.ItemCofre>
    ) : RecyclerView.Adapter<VisualizadorPagerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgPaginaVisualizador)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cofre_visualizador_pagina, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = itens[position]
            val path = item.arquivo.absolutePath
            holder.img.setImageBitmap(null)
            holder.img.tag = path

            scope.launch {
                val bmp = withContext(Dispatchers.IO) { CofreManager.carregar(item.arquivo) }
                if (holder.img.tag == path && bmp != null) {
                    holder.img.setImageBitmap(bmp)
                    configurarZoom(holder.img)
                }
            }
        }

        override fun getItemCount() = itens.size

        fun itemNaPosicao(pos: Int): CofreManager.ItemCofre? = itens.getOrNull(pos)

        fun atualizarItens(novos: List<CofreManager.ItemCofre>) {
            itens = novos.toMutableList()
            notifyDataSetChanged()
        }

        fun removerNaPosicao(pos: Int): Int {
            if (pos in itens.indices) {
                itens.removeAt(pos)
                notifyItemRemoved(pos)
            }
            return itens.size
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Zoom/pan por foto (pinça + duplo toque) — mesma lógica de antes,
    //  agora com UMA MUDANÇA: só "trava" o swipe do carrossel (RecyclerView
    //  pai) enquanto a foto estiver de fato ampliada ou durante o próprio
    //  gesto de pinça. Com a foto no zoom normal (1:1), o dedo desliza e o
    //  carrossel troca de foto normalmente.
    // ═══════════════════════════════════════════════════════════════════
    private fun configurarZoom(iv: ImageView) {
        iv.scaleType = ImageView.ScaleType.MATRIX
        val matrix = Matrix()
        val baseMatrix = Matrix()
        var minScale = 1f
        val maxScale = 6f

        fun currentScale(): Float {
            val vals = FloatArray(9)
            matrix.getValues(vals)
            return vals[Matrix.MSCALE_X]
        }

        fun aplicarFitCenter() {
            val d = iv.drawable ?: return
            val vw = iv.width.toFloat()
            val vh = iv.height.toFloat()
            val iw = d.intrinsicWidth.toFloat()
            val ih = d.intrinsicHeight.toFloat()
            if (vw <= 0f || vh <= 0f || iw <= 0f || ih <= 0f) return
            baseMatrix.reset()
            val s = minOf(vw / iw, vh / ih)
            val dx = (vw - iw * s) / 2f
            val dy = (vh - ih * s) / 2f
            baseMatrix.setScale(s, s)
            baseMatrix.postTranslate(dx, dy)
            minScale = s
            matrix.set(baseMatrix)
            iv.imageMatrix = matrix
        }

        fun corrigirLimites() {
            val d = iv.drawable ?: return
            val vw = iv.width.toFloat()
            val vh = iv.height.toFloat()
            val iw = d.intrinsicWidth.toFloat()
            val ih = d.intrinsicHeight.toFloat()
            val rect = RectF(0f, 0f, iw, ih)
            matrix.mapRect(rect)
            val s = currentScale()
            if (s < minScale) {
                aplicarFitCenter()
                return
            }
            var dx = 0f
            var dy = 0f
            if (rect.width() <= vw) dx = (vw - rect.width()) / 2f - rect.left
            else if (rect.left > 0f) dx = -rect.left
            else if (rect.right < vw) dx = vw - rect.right

            if (rect.height() <= vh) dy = (vh - rect.height()) / 2f - rect.top
            else if (rect.top > 0f) dy = -rect.top
            else if (rect.bottom < vh) dy = vh - rect.bottom

            matrix.postTranslate(dx, dy)
        }

        iv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or2: Int, ob: Int) {
                if (iv.drawable != null && iv.width > 0 && iv.height > 0) {
                    aplicarFitCenter()
                    iv.removeOnLayoutChangeListener(this)
                }
            }
        })
        iv.post { if (iv.drawable != null && iv.width > 0 && iv.height > 0) aplicarFitCenter() }

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val novaEscala = (currentScale() * detector.scaleFactor).coerceIn(minScale, maxScale)
                val fator = novaEscala / currentScale()
                matrix.postScale(fator, fator, detector.focusX, detector.focusY)
                corrigirLimites()
                iv.imageMatrix = matrix
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale() > minScale * 1.05f) {
                    aplicarFitCenter()
                } else {
                    val alvo = minScale * 2.5f
                    matrix.postScale(alvo / currentScale(), alvo / currentScale(), e.x, e.y)
                    corrigirLimites()
                }
                iv.imageMatrix = matrix
                return true
            }
        })

        var lastX = 0f
        var lastY = 0f
        iv.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)

            // ═══ Só bloqueia o swipe do carrossel (RecyclerView pai)
            // enquanto a foto estiver ampliada ou durante a própria pinça —
            // no zoom normal (1:1), deixa o dedo passar pro carrossel
            // trocar de foto. ═══
            val zoomAtivo = currentScale() > minScale * 1.01f
            v.parent?.requestDisallowInterceptTouchEvent(zoomAtivo || scaleDetector.isInProgress)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && zoomAtivo) {
                        matrix.postTranslate(event.x - lastX, event.y - lastY)
                        corrigirLimites()
                        iv.imageMatrix = matrix
                    }
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.performClick()
            }
            // ═══ SEMPRE true: a ImageView precisa continuar recebendo o
            // gesto inteiro (senão duplo-toque/pinça quebram no primeiro
            // toque). Quem decide se o carrossel pai pode ASSUMIR o
            // gesto de arrastar é o requestDisallowInterceptTouchEvent
            // acima, não o retorno daqui. ═══
            true
        }
    }
}
