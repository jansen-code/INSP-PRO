package com.raylson.jansen.inspetor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

/**
 * ═══ TUTORIAL DO DASHBOARD — arquivo isolado (Regra de Ouro: mantém o
 * DashboardActivity.kt limpo, sem nenhuma lógica de tutorial dentro dele).
 *
 * Feito 100% com Canvas/View nativos do Android — NENHUMA biblioteca nova,
 * não precisa mexer no build.gradle (só remover a dependência antiga do
 * TapTargetView, que não é mais usada — ver aviso no final do arquivo).
 *
 * ═══ VERSÃO 3 — correções desta rodada ═══
 *   1) O card de texto agora é MEDIDO com o título/descrição do passo atual
 *      ANTES de decidir onde posicionar (a versão anterior media com o
 *      texto do passo anterior, o que jogava o card pra cima e cortava
 *      em cima do elemento destacado).
 *   2) A altura do card agora tem um teto: se não couber inteiro no espaço
 *      disponível (acima ou abaixo do alvo), o conteúdo vira scroll
 *      interno — o card NUNCA mais vaza pra fora da tela nem cobre o
 *      próprio alvo.
 *   3) Botão "Pular" saiu de cima da status bar e agora vive dentro do
 *      próprio card, no canto superior direito — sempre num lugar
 *      previsível, nunca sobrepõe conteúdo.
 *   4) Passos cujo botão está invisível/oculto no momento (ex: toggle que
 *      só existe no modo N.A.) são pulados automaticamente.
 *   5) Novo botão "‹ Voltar" pra corrigir se passou rápido demais.
 *   6) Passos de RecyclerView (carrosséis) agora podem receber um cálculo
 *      de retângulo customizado que abraça só os cards VISÍVEIS de
 *      verdade, em vez do RecyclerView inteiro — evita destacar área
 *      maior que o conteúdo real.
 *
 * USO (a partir do DashboardActivity, depois que a tela já carregou):
 *
 *   TutorialDashboard.iniciarSeNecessario(
 *       activity = this,
 *       passos = listOf(
 *           TutorialDashboard.Passo(tvApelido, "Bem-vindo!", "..."),
 *           TutorialDashboard.Passo(
 *               view = carrosselEstacoes, titulo = "...", descricao = "...",
 *               calcularRetangulo = { TutorialDashboard.retanguloVisivelDe(carrosselEstacoes) }
 *           ),
 *           ...
 *       )
 *   )
 * ═══
 */
class TutorialDashboard private constructor(private val activity: Activity) {

    data class Passo(
        val view: View,
        val titulo: String,
        val descricao: String,
        val corDestaque: Int = Color.parseColor("#EF4444"),
        // Espaço extra ao redor do view realçado (em dp)
        val folgaDp: Float = 10f,
        // Botões pequenos/redondos (ícones da barra inferior, avatar etc.)
        // ficam com destaque em CÍRCULO em vez de retângulo arredondado.
        val circular: Boolean = false,
        // Chamado toda vez que este passo aparece (ex: mover o carrossel sozinho)
        val aoEntrar: (() -> Unit)? = null,
        // Chamado quando o passo deixa de ser o ativo (ex: parar autoplay)
        val aoSair: (() -> Unit)? = null,
        // ═══ NOVO: para RecyclerViews, permite calcular o retângulo a
        // partir dos filhos realmente visíveis (evita "vazar" pra área
        // vizinha quando o RecyclerView mede mais alto que o conteúdo). ═══
        val calcularRetangulo: (() -> RectF)? = null
    )

    private var passos: List<Passo> = emptyList()
    private var indice = 0
    private var aoFinalizarTudo: (() -> Unit)? = null

    private lateinit var overlay: FrameLayout
    private lateinit var spotlight: SpotlightView
    private lateinit var cardContainer: FrameLayout
    private lateinit var card: CardView
    private lateinit var scrollConteudo: AlturaLimitadaScrollView
    private lateinit var tvPasso: TextView
    private lateinit var tvTitulo: TextView
    private lateinit var tvDescricao: TextView
    private lateinit var tvVoltar: TextView
    private lateinit var tvProximo: TextView
    private lateinit var tvPular: TextView
    private lateinit var dots: LinearLayout

    companion object {
        private const val PREF_KEY = "tutorial_dashboard_visto_v3"

        /** Verifica se o tutorial já foi visto (ou pulado) antes. */
        fun jaVisto(activity: Activity): Boolean {
            val prefs = SecurePrefs.get(activity, "inspetor_prefs")
            return prefs.getBoolean(PREF_KEY, false)
        }

        /** Marca como visto sem precisar mostrar (ex: botão "resetar tutorial" em configurações). */
        fun marcarComoVisto(activity: Activity) {
            SecurePrefs.get(activity, "inspetor_prefs").edit().putBoolean(PREF_KEY, true).apply()
        }

        /**
         * Inicia o tutorial se ele ainda não foi visto. Chame depois que a
         * tela já estiver totalmente carregada (views com tamanho/posição
         * definidos), tipo no fim do onCreate.
         */
        fun iniciarSeNecessario(activity: Activity, passos: List<Passo>, aoFinalizar: (() -> Unit)? = null) {
            if (!jaVisto(activity)) iniciar(activity, passos, aoFinalizar)
        }

        /** Força o tutorial a começar, mesmo que já tenha sido visto antes. */
        fun iniciar(activity: Activity, passos: List<Passo>, aoFinalizar: (() -> Unit)? = null) {
            if (passos.isEmpty()) return
            val instancia = TutorialDashboard(activity)
            instancia.passos = passos
            instancia.aoFinalizarTudo = aoFinalizar
            instancia.montarOverlay()
            instancia.mostrarPassoValido(0, 1)
        }

        /**
         * Une o retângulo de todos os filhos VISÍVEIS de um RecyclerView
         * (em vez de usar a altura/largura medida do RecyclerView inteiro,
         * que pode incluir espaço reservado além do conteúdo desenhado).
         * Use isso no `calcularRetangulo` dos passos que apontam pra
         * carrosséis, pra garantir que o destaque abraça só os cards.
         */
        fun retanguloVisivelDe(recycler: RecyclerView): RectF {
            var esquerda = Float.MAX_VALUE
            var topo = Float.MAX_VALUE
            var direita = -Float.MAX_VALUE
            var fundo = -Float.MAX_VALUE

            for (i in 0 until recycler.childCount) {
                val filho = recycler.getChildAt(i) ?: continue
                if (filho.width <= 0 || filho.height <= 0) continue

                val parteVisivel = Rect()
                if (!filho.getLocalVisibleRect(parteVisivel) || parteVisivel.isEmpty) continue

                val pos = IntArray(2)
                filho.getLocationInWindow(pos)

                esquerda = minOf(esquerda, pos[0] + parteVisivel.left.toFloat())
                topo = minOf(topo, pos[1] + parteVisivel.top.toFloat())
                direita = maxOf(direita, pos[0] + parteVisivel.right.toFloat())
                fundo = maxOf(fundo, pos[1] + parteVisivel.bottom.toFloat())
            }

            if (esquerda == Float.MAX_VALUE) {
                // Nenhum filho renderizado ainda (RecyclerView vazio) — usa a
                // área visível do próprio RecyclerView como fallback.
                val pos = IntArray(2)
                recycler.getLocationInWindow(pos)
                val parteVisivel = Rect()
                val temParteVisivel = recycler.getLocalVisibleRect(parteVisivel) && !parteVisivel.isEmpty
                return if (temParteVisivel) {
                    RectF(
                        pos[0] + parteVisivel.left.toFloat(),
                        pos[1] + parteVisivel.top.toFloat(),
                        pos[0] + parteVisivel.right.toFloat(),
                        pos[1] + parteVisivel.bottom.toFloat()
                    )
                } else {
                    RectF(
                        pos[0].toFloat(), pos[1].toFloat(),
                        pos[0] + recycler.width.toFloat(), pos[1] + recycler.height.toFloat()
                    )
                }
            }

            return RectF(esquerda, topo, direita, fundo)
        }

        private fun dp(activity: Activity, valor: Float): Float =
            valor * activity.resources.displayMetrics.density
    }

    // ───────────────────────── construção do overlay ─────────────────────────

    private fun montarOverlay() {
        val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        overlay = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        spotlight = SpotlightView(activity)
        overlay.addView(spotlight, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ── Card explicativo (contém título, descrição, progresso, pular e próximo — tudo junto) ──
        cardContainer = FrameLayout(activity).apply { alpha = 0f }
        card = montarCard()
        cardContainer.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        overlay.addView(
            cardContainer,
            FrameLayout.LayoutParams(
                activity.resources.displayMetrics.widthPixels - dp(activity, 40f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(overlay)
    }

    private fun montarCard(): CardView {
        val ctx = activity
        val cardView = CardView(ctx).apply {
            radius = dp(ctx, 18f)
            cardElevation = dp(ctx, 10f)
            setCardBackgroundColor(Color.parseColor("#1E293B"))
        }

        val conteudo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f).toInt(), dp(ctx, 14f).toInt(), dp(ctx, 20f).toInt(), dp(ctx, 18f).toInt())
        }

        // ── Linha do topo: "PASSO X DE Y" à esquerda, "Pular ✕" à direita ──
        // (substitui o antigo botão flutuante que ficava por cima da status bar)
        val linhaTopo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvPasso = TextView(ctx).apply {
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.04f
        }
        val espacoTopo = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tvPular = TextView(ctx).apply {
            text = "PULAR  ✕"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(ctx, 10f).toInt(), dp(ctx, 6f).toInt(), dp(ctx, 4f).toInt(), dp(ctx, 6f).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { finalizar() }
        }
        linhaTopo.addView(tvPasso, espacoTopo)
        linhaTopo.addView(tvPular)

        tvTitulo = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(ctx, 4f).toInt(), 0, 0)
        }

        tvDescricao = TextView(ctx).apply {
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14.5f
            setPadding(0, dp(ctx, 8f).toInt(), 0, 0)
            setLineSpacing(dp(ctx, 2f), 1f)
        }

        // ── Descrição fica dentro de um scroll com altura máxima ajustável:
        // se o card não tiver espaço suficiente na tela pro texto todo, em
        // vez de cortar ou empurrar o card por cima do alvo, o texto rola
        // internamente. ──
        scrollConteudo = AlturaLimitadaScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(tvDescricao)
        }

        val linhaInferior = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 16f).toInt(), 0, 0)
        }

        dots = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val espacoDots = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        tvVoltar = TextView(ctx).apply {
            text = "‹ VOLTAR"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(ctx, 10f).toInt(), dp(ctx, 10f).toInt(), dp(ctx, 10f).toInt(), dp(ctx, 10f).toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { voltar() }
        }

        tvProximo = TextView(ctx).apply {
            text = "PRÓXIMO  →"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(ctx, 18f).toInt(), dp(ctx, 10f).toInt(), dp(ctx, 18f).toInt(), dp(ctx, 10f).toInt())
            background = criarFundoBotao()
            isClickable = true
            isFocusable = true
            setOnClickListener { avancar() }
        }

        linhaInferior.addView(dots, espacoDots)
        linhaInferior.addView(tvVoltar)
        linhaInferior.addView(tvProximo)

        conteudo.addView(linhaTopo)
        conteudo.addView(tvTitulo)
        conteudo.addView(scrollConteudo)
        conteudo.addView(linhaInferior)
        cardView.addView(conteudo)
        return cardView
    }

    private fun criarFundoBotao(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(activity, 24f)
            setColor(Color.parseColor("#2563EB"))
        }
    }

    private fun montarDots() {
        dots.removeAllViews()
        for (i in passos.indices) {
            val ativo = i == indice
            val bolinha = View(activity).apply {
                val tamanho = dp(activity, if (ativo) 9f else 6f).toInt()
                layoutParams = LinearLayout.LayoutParams(tamanho, tamanho).apply {
                    marginEnd = dp(activity, 5f).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (ativo) Color.parseColor("#2563EB") else Color.parseColor("#475569"))
                }
            }
            dots.addView(bolinha)
        }
    }

    // ───────────────────────── sequência dos passos ─────────────────────────

    /**
     * Mostra o primeiro passo válido a partir de [indiceAlvo], andando na
     * direção [direcao] (1 = pra frente, -1 = pra trás). Passos cujo view
     * está invisível/oculto (ex: toggle exclusivo do modo N.A.) são pulados
     * automaticamente em vez de quebrar o tutorial.
     */
    private fun mostrarPassoValido(indiceAlvo: Int, direcao: Int) {
        var i = indiceAlvo
        while (i in passos.indices) {
            val p = passos[i]
            if (p.view.visibility == View.VISIBLE && p.view.width > 0 && p.view.height > 0) {
                mostrarPasso(i)
                return
            }
            i += direcao
        }
        if (direcao > 0) finalizar()
        // se for pra trás e não achar nenhum passo válido antes, simplesmente não faz nada
    }

    private fun mostrarPasso(novoIndice: Int) {
        if (novoIndice != indice && indice in passos.indices) {
            passos[indice].aoSair?.invoke()
        }

        indice = novoIndice
        val passo = passos[indice]

        // Espera o layout do view-alvo estar pronto (garante posição correta
        // mesmo se o passo anterior mudou o scroll/estado da tela).
        passo.view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                passo.view.viewTreeObserver.removeOnPreDrawListener(this)
                animarParaPasso(passo)
                return true
            }
        })
        passo.view.requestLayout()
    }

    private fun animarParaPasso(passo: Passo) {
        // ═══ Retângulo do alvo: usa o cálculo customizado (ex: filhos
        // visíveis de um RecyclerView) quando fornecido; senão, os limites
        // exatos da própria view — nunca mais que isso. ═══
        val base = passo.calcularRetangulo?.invoke() ?: rectVisivelDaView(passo.view)

        // ═══ CORREÇÃO: Subtrai a posição do overlay (que fica abaixo da
        // status bar) das coordenadas absolutas do elemento, alinhando
        // o buraco perfeitamente com o botão na tela. ═══
        val overlayPos = IntArray(2)
        overlay.getLocationInWindow(overlayPos)
        base.offset(-overlayPos[0].toFloat(), -overlayPos[1].toFloat())

        val folgaPx = dp(activity, passo.folgaDp)
        val destino = RectF(base.left - folgaPx, base.top - folgaPx, base.right + folgaPx, base.bottom + folgaPx)

        // Nunca deixa o destaque vazar pra fora da área real do overlay.
        destino.left = destino.left.coerceAtLeast(0f)
        destino.top = destino.top.coerceAtLeast(0f)
        destino.right = destino.right.coerceAtMost(overlay.width.toFloat())
        destino.bottom = destino.bottom.coerceAtMost(overlay.height.toFloat())

        spotlight.animarSpotlightPara(destino, passo.corDestaque, passo.circular)

        // ── Atualiza todo o conteúdo do card ANTES de medir/posicionar ──
        // (a versão anterior posicionava com o texto do passo ANTERIOR
        // ainda no TextView, o que calculava uma altura errada e podia
        // empurrar o card por cima do próprio elemento destacado)
        montarDots()
        tvPasso.text = "PASSO ${indice + 1} DE ${passos.size}"
        tvTitulo.text = passo.titulo
        tvDescricao.text = passo.descricao
        tvVoltar.visibility = if (indice > 0) View.VISIBLE else View.GONE
        tvProximo.text = if (indice == passos.size - 1) "ENTENDI ✓" else "PRÓXIMO  →"

        posicionarCard(destino)

        cardContainer.animate().cancel()
        if (cardContainer.alpha == 0f) cardContainer.translationY = dp(activity, 12f)
        cardContainer.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()

        passo.aoEntrar?.invoke()
    }

    private fun posicionarCard(destino: RectF) {
        val topoVisivel = 0f
        val fundoVisivel = overlay.height.toFloat()
        val margemSeguranca = dp(activity, 16f)
        val margemAlvo = dp(activity, 18f)
        val alturaMinima = dp(activity, 96f)

        // ── Mede a altura NATURAL do card com o conteúdo do passo atual ──
        val larguraCard = activity.resources.displayMetrics.widthPixels - dp(activity, 40f).toInt()
        scrollConteudo.alturaMaximaPx = -1 // mede sem limite primeiro, pra saber o tamanho "ideal"
        card.measure(
            View.MeasureSpec.makeMeasureSpec(larguraCard, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val alturaNatural = card.measuredHeight.toFloat()

        val espacoAbaixo = (fundoVisivel - margemSeguranca) - (destino.bottom + margemAlvo)
        val espacoAcima = (destino.top - margemAlvo) - (topoVisivel + margemSeguranca)

        val usarAbaixo = espacoAbaixo >= espacoAcima
        val espacoDisponivel = (if (usarAbaixo) espacoAbaixo else espacoAcima).coerceAtLeast(alturaMinima)

        // Se o texto não couber inteiro no espaço disponível, limita a
        // altura do card a esse espaço — o excesso vira scroll interno em
        // vez de vazar da tela ou empurrar o card por cima do alvo.
        if (alturaNatural > espacoDisponivel) {
            val alturaFixaExtra = alturaNatural - scrollConteudo.measuredHeight // topo+rodapé do card fora do scroll
            scrollConteudo.alturaMaximaPx = (espacoDisponivel - alturaFixaExtra).toInt().coerceAtLeast(dp(activity, 48f).toInt())
        } else {
            scrollConteudo.alturaMaximaPx = -1
        }

        val params = cardContainer.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = dp(activity, 20f).toInt()
        params.rightMargin = dp(activity, 20f).toInt()

        if (usarAbaixo) {
            params.gravity = Gravity.TOP or Gravity.START
            val topoIdeal = destino.bottom + margemAlvo
            val topoMaximo = fundoVisivel - margemSeguranca - alturaMinima
            params.topMargin = topoIdeal.toInt().coerceIn(margemSeguranca.toInt(), topoMaximo.toInt().coerceAtLeast(margemSeguranca.toInt()))
            params.bottomMargin = 0
        } else {
            params.gravity = Gravity.BOTTOM or Gravity.START
            val bottomIdeal = fundoVisivel - destino.top + margemAlvo
            val bottomMaximo = fundoVisivel - topoVisivel - margemSeguranca - alturaMinima
            params.bottomMargin = bottomIdeal.toInt().coerceIn(margemSeguranca.toInt(), bottomMaximo.toInt().coerceAtLeast(margemSeguranca.toInt()))
            params.topMargin = 0
        }
        cardContainer.layoutParams = params
    }

    private fun rectVisivelDaView(view: View): RectF {
        val parteVisivel = Rect()
        val posicao = IntArray(2)
        view.getLocationInWindow(posicao)

        return if (view.getLocalVisibleRect(parteVisivel) && !parteVisivel.isEmpty) {
            RectF(
                posicao[0] + parteVisivel.left.toFloat(),
                posicao[1] + parteVisivel.top.toFloat(),
                posicao[0] + parteVisivel.right.toFloat(),
                posicao[1] + parteVisivel.bottom.toFloat()
            )
        } else {
            RectF(
                posicao[0].toFloat(),
                posicao[1].toFloat(),
                posicao[0] + view.width.toFloat(),
                posicao[1] + view.height.toFloat()
            )
        }
    }

    private fun avancar() {
        mostrarPassoValido(indice + 1, 1)
    }

    private fun voltar() {
        mostrarPassoValido(indice - 1, -1)
    }

    private fun finalizar() {
        if (indice in passos.indices) {
            passos[indice].aoSair?.invoke()
        }
        SecurePrefs.get(activity, "inspetor_prefs").edit().putBoolean(PREF_KEY, true).apply()
        val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        overlay.animate().alpha(0f).setDuration(200).withEndAction {
            root.removeView(overlay)
            aoFinalizarTudo?.invoke()
        }.start()
    }

    // ───────────────────────── scroll com altura máxima ajustável ─────────────────────────

    /**
     * ScrollView comum, mas com um teto de altura configurável em tempo de
     * execução (`alturaMaximaPx`). Usado pra garantir que a descrição do
     * passo NUNCA empurre o card pra fora da tela — se não couber, rola
     * internamente em vez de vazar.
     */
    private class AlturaLimitadaScrollView(context: android.content.Context) : ScrollView(context) {
        var alturaMaximaPx: Int = -1 // -1 = sem limite

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val limite = alturaMaximaPx
            val specFinal = if (limite >= 0) {
                View.MeasureSpec.makeMeasureSpec(limite, View.MeasureSpec.AT_MOST)
            } else {
                heightMeasureSpec
            }
            super.onMeasure(widthMeasureSpec, specFinal)
        }
    }

    // ───────────────────────── view do spotlight + círculo pincelado ─────────────────────────

    /**
     * View que desenha: (1) um fundo escuro LEVE (não a tela toda escura),
     * (2) um "buraco" arredondado recortado exatamente na área do elemento
     * explicado, e (3) um traço vermelho animado contornando essa área,
     * como se alguém tivesse circulando com um marcador/pincel.
     */
    private class SpotlightView(context: android.content.Context) : View(context) {

        private val paintFundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(115, 15, 23, 42)
        }
        private val paintBuraco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private val paintPincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private var camadaOffscreen: Canvas? = null
        private var bitmapOffscreen: android.graphics.Bitmap? = null

        private var retanguloAtual = RectF()
        private var formaCircular = false
        private var progressoTraco = 0f // 0f a 1f — quanto do círculo pincelado já foi "desenhado"

        private var animRetangulo: ValueAnimator? = null
        private var animTraco: ValueAnimator? = null

        init { setLayerType(LAYER_TYPE_SOFTWARE, null) } // necessário pro xfermode CLEAR funcionar

        fun animarSpotlightPara(destino: RectF, cor: Int, circular: Boolean = false) {
            formaCircular = circular
            paintPincel.color = cor
            paintPincel.strokeWidth = destino.height() * 0.02f + resources.displayMetrics.density * 2.5f

            val origem = RectF(retanguloAtual)
            val jaTinhaAlvo = origem.width() > 0f

            animRetangulo?.cancel()
            animTraco?.cancel()

            if (!jaTinhaAlvo) {
                retanguloAtual = RectF(destino)
                invalidate()
                iniciarAnimacaoTraco()
                return
            }

            animRetangulo = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 380
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    retanguloAtual = RectF(
                        origem.left + (destino.left - origem.left) * f,
                        origem.top + (destino.top - origem.top) * f,
                        origem.right + (destino.right - origem.right) * f,
                        origem.bottom + (destino.bottom - origem.bottom) * f
                    )
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        iniciarAnimacaoTraco()
                    }
                })
                start()
            }
        }

        private fun iniciarAnimacaoTraco() {
            progressoTraco = 0f
            animTraco = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 550
                startDelay = 80
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    progressoTraco = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                bitmapOffscreen = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                camadaOffscreen = Canvas(bitmapOffscreen!!)
            }
        }

        override fun onDraw(canvas: Canvas) {
            val bmp = bitmapOffscreen ?: return
            bmp.eraseColor(Color.TRANSPARENT)
            val c = camadaOffscreen ?: return

            // 1) fundo leve
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintFundo)

            // 2) buraco no elemento explicado — círculo (ícones pequenos) ou
            // retângulo arredondado (cards, carrosséis, textos)
            if (retanguloAtual.width() > 0f) {
                if (formaCircular) {
                    c.drawOval(retanguloAtual, paintBuraco)
                } else {
                    val raioCanto = retanguloAtual.height() * 0.28f
                    c.drawRoundRect(retanguloAtual, raioCanto, raioCanto, paintBuraco)
                }
            }

            canvas.drawBitmap(bmp, 0f, 0f, null)

            // 3) círculo "pincelado" animado — dois traços levemente
            // deslocados, imitando alguém circulando à mão livre duas vezes
            // com um marcador (fica mais orgânico que uma linha perfeita).
            if (retanguloAtual.width() > 0f && progressoTraco > 0f) {
                desenharPincelParcial(canvas, retanguloAtual, 0f, 1f)
                val menor = RectF(
                    retanguloAtual.left + 5f, retanguloAtual.top + 5f,
                    retanguloAtual.right - 5f, retanguloAtual.bottom - 5f
                )
                desenharPincelParcial(canvas, menor, 0.06f, 0.82f)
            }
        }

        private fun desenharPincelParcial(canvas: Canvas, rect: RectF, atrasoInicio: Float, escalaDuracao: Float) {
            val progressoLocal = ((progressoTraco - atrasoInicio) / escalaDuracao).coerceIn(0f, 1f)
            if (progressoLocal <= 0f) return

            val caminho = Path()
            if (formaCircular) {
                caminho.addOval(rect, Path.Direction.CW)
            } else {
                val raioCanto = rect.height() * 0.3f
                caminho.addRoundRect(rect, raioCanto, raioCanto, Path.Direction.CW)
            }

            val medida = PathMeasure(caminho, true)
            val trecho = Path()
            medida.getSegment(0f, medida.length * progressoLocal, trecho, true)
            canvas.drawPath(trecho, paintPincel)
        }
    }
}

/*
 ═══ LIMPEZA DE GRADLE ═══
 O build.gradle atual ainda tem esta linha:

     implementation 'com.getkeepsafe.taptargetview:taptargetview:1.13.3'

 Ela não é mais usada — o tutorial é 100% Canvas/View nativo desde a
 substituição do TapTargetView. Pode remover essa linha do build.gradle
 pra não carregar uma biblioteca à toa no APK. Nenhuma outra dependência
 precisa mudar.
 */
