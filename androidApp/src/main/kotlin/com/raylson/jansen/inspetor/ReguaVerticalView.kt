package com.raylson.jansen.inspetor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

class ReguaVerticalView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    enum class Modo { BRILHO, NITIDEZ, VETORIZACAO }
    var modoAtual = Modo.BRILHO
        private set

    var valorBrilho = 0f
    var valorNitidez = 0f
    var valorVetorizacao = 0f
    var onValorMudou: ((Modo, Float) -> Unit)? = null
    var alinharEsquerda = false

    private val dp = resources.displayMetrics.density

    // ═══ Variáveis da Animação da Gaveta ═══
        // ═══ Variáveis da Animação da Gaveta ═══
    private var isGavetaAberta = false
    private var gavetaOffsetY = -52f * dp // Aqui fica negativo (empurra pra cima)
    private val alturaBarra by lazy { 52f * dp } // A altura continua POSITIVA! // Altura da faixa escura (mais fina)

    // Dimensões da abinha (puxador), ancorada no canto inferior ESQUERDO
    private val abaLargura by lazy { 52f * dp }
    private val abaAltura by lazy { 20f * dp }
    private val abaMargemEsquerda by lazy { 14f * dp }
    private val abaRaioCurva by lazy { 10f * dp }

    // ── Tintas e Estilos ──
    private val paintFundoGaveta = Paint().apply {
        color = Color.parseColor("#99000000") // 60% preto para a barra de ponta a ponta
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintSeta = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        setShadowLayer(2f * dp, 0f, 1f * dp, Color.BLACK)
    }

    private val paintTrack = Paint().apply {
        strokeWidth = 2f * dp
        color = Color.argb(100, 255, 255, 255)
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val paintThumb = Paint().apply {
        color = Color.parseColor("#FBBF24")
        isAntiAlias = true
        style = Paint.Style.FILL
        setShadowLayer(3f * dp, 0f, 1f * dp, Color.parseColor("#99000000"))
    }

    // Tinta para os traços normais
    private val paintTraco = Paint().apply {
        strokeWidth = 1.5f * dp
        color = Color.WHITE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(2f * dp, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    // ═══ NOVO: Tinta exclusiva para o traço ZERO (Amarelo) ═══
    private val paintTracoZero = Paint().apply {
        strokeWidth = 2.5f * dp
        color = Color.parseColor("#FBBF24") 
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(2f * dp, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    private val paintBotaoFundo = Paint().apply { color = Color.parseColor("#66000000"); isAntiAlias = true }

    private val paintTextoIcone = Paint().apply {
        textSize = 14f * dp; color = Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true
        setShadowLayer(3f * dp, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    private val paintTextoLabel = Paint().apply {
        textSize = 9f * dp; color = Color.parseColor("#E2E8F0"); textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        setShadowLayer(3f * dp, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    // paintTextoNumeros foi removido pois o design agora é limpo!

    private var isDragging = false
    private var btnCx = 0f
    private var btnCy = 0f
    private val btnRaio = 18f * dp

    // Área da abinha calculada a cada onDraw (usada também no touch)
    private var abaLeft = 0f
    private var abaRight = 0f
    private var abaTop = 0f
    private var abaBottom = 0f

    private fun isHorizontal() = width > height

    private fun valorDoModoAtivo(): Float = when (modoAtual) {
        Modo.BRILHO      -> valorBrilho
        Modo.NITIDEZ     -> valorNitidez
        Modo.VETORIZACAO -> valorVetorizacao
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val horizontal = isHorizontal()
        val valorAtual = valorDoModoAtivo()

        if (horizontal) {
            // ════════════════════════════════════════════════════════
            // 1. MODO DIÁLOGO FINAL (HORIZONTAL COM GAVETA ANIMADA)
            // ════════════════════════════════════════════════════════

            canvas.drawRect(0f, gavetaOffsetY, width.toFloat(), gavetaOffsetY + alturaBarra, paintFundoGaveta)

            abaLeft = abaMargemEsquerda
            abaRight = abaLeft + abaLargura
            abaTop = gavetaOffsetY + alturaBarra
            abaBottom = abaTop + abaAltura

            val abaPath = Path().apply {
                moveTo(abaLeft, abaTop)
                lineTo(abaLeft, abaBottom - abaRaioCurva)
                quadTo(abaLeft, abaBottom, abaLeft + abaRaioCurva, abaBottom)
                lineTo(abaRight - abaRaioCurva, abaBottom)
                quadTo(abaRight, abaBottom, abaRight, abaBottom - abaRaioCurva)
                lineTo(abaRight, abaTop)
                close()
            }
            canvas.drawPath(abaPath, paintFundoGaveta)

            val setaCx = abaLeft + (abaLargura / 2f)
            val setaCy = abaTop + (abaAltura / 2f) + (2f * dp)
            val tamanhoSeta = 6f * dp

            val pathSeta = Path()
            if (isGavetaAberta) {
                pathSeta.moveTo(setaCx - tamanhoSeta, setaCy + tamanhoSeta / 2)
                pathSeta.lineTo(setaCx, setaCy - tamanhoSeta / 2)
                pathSeta.moveTo(setaCx, setaCy - tamanhoSeta / 2)
                pathSeta.lineTo(setaCx + tamanhoSeta, setaCy + tamanhoSeta / 2)
            } else {
                pathSeta.moveTo(setaCx - tamanhoSeta, setaCy - tamanhoSeta / 2)
                pathSeta.lineTo(setaCx, setaCy + tamanhoSeta / 2)
                pathSeta.moveTo(setaCx, setaCy + tamanhoSeta / 2)
                pathSeta.lineTo(setaCx + tamanhoSeta, setaCy - tamanhoSeta / 2)
            }
            canvas.drawPath(pathSeta, paintSeta)

            btnCx = (12f * dp) + btnRaio
            btnCy = gavetaOffsetY + (alturaBarra / 2f)

            canvas.drawCircle(btnCx, btnCy, btnRaio, paintBotaoFundo)

            val textoIcone = when (modoAtual) { Modo.BRILHO -> "☀️"; Modo.NITIDEZ -> "◭"; Modo.VETORIZACAO -> "✦" }
            val textoLabel = when (modoAtual) { Modo.BRILHO -> "Luz"; Modo.NITIDEZ -> "Nit"; Modo.VETORIZACAO -> "Vet" }
            val textOffsetIcon = (paintTextoIcone.descent() + paintTextoIcone.ascent()) / 2
            canvas.drawText(textoIcone, btnCx, btnCy - textOffsetIcon - (4f * dp), paintTextoIcone)
            canvas.drawText(textoLabel, btnCx, btnCy + (8f * dp), paintTextoLabel)

            val reguaLeft = btnCx + btnRaio + (24f * dp)
            val reguaRight = width - (20f * dp)
            val reguaLargura = reguaRight - reguaLeft
            val reguaCenterX = reguaLeft + (reguaLargura / 2f)

            canvas.drawLine(reguaLeft, btnCy, reguaRight, btnCy, paintTrack)

            val espacoTraco = reguaLargura / 20
            for (i in -10..10) {
                val x = reguaCenterX + (i * espacoTraco)
                
                // ═══ NOVO: Lógica dos traços cruzando a linha e zero amarelo ═══
                if (i == 0) {
                    val tamanhoZero = 8f * dp
                    canvas.drawLine(x, btnCy - tamanhoZero, x, btnCy + tamanhoZero, paintTracoZero)
                } else {
                    val isTracoMaior = (i % 5 == 0)
                    val tamanhoTraco = if (isTracoMaior) 5f * dp else 2.5f * dp
                    canvas.drawLine(x, btnCy - tamanhoTraco, x, btnCy + tamanhoTraco, paintTraco)
                }
            }
            val thumbX = reguaCenterX + (valorAtual * (reguaLargura / 2f))
            canvas.drawCircle(thumbX, btnCy, 7.5f * dp, paintThumb)

        } else {
            // ════════════════════════════════════════════════════════
            // 2. MODO CÂMERA AO VIVO (VERTICAL SIMPLES E TRANSPARENTE)
            // ════════════════════════════════════════════════════════
            btnCx = if (alinharEsquerda) btnRaio + (12f * dp) else width - btnRaio - (12f * dp)
            btnCy = btnRaio + (12f * dp)

            canvas.drawCircle(btnCx, btnCy, btnRaio, paintBotaoFundo)

            val textoIcone = when (modoAtual) { Modo.BRILHO -> "☀️"; Modo.NITIDEZ -> "◭"; Modo.VETORIZACAO -> "✦" }
            val textoLabel = when (modoAtual) { Modo.BRILHO -> "Luz"; Modo.NITIDEZ -> "Nit"; Modo.VETORIZACAO -> "Vet" }
            val textOffsetIcon = (paintTextoIcone.descent() + paintTextoIcone.ascent()) / 2
            canvas.drawText(textoIcone, btnCx, btnCy - textOffsetIcon - (4f * dp), paintTextoIcone)
            canvas.drawText(textoLabel, btnCx, btnCy + (8f * dp), paintTextoLabel)

            val maxTamanho = 280f * dp
            val h = height.toFloat()
            var top = btnCy + btnRaio + (20f * dp)
            var bottom = h - (20f * dp)
            var reguaAltura = bottom - top
            if (reguaAltura > maxTamanho) {
                val centro = top + (reguaAltura / 2f)
                top = centro - (maxTamanho / 2f)
                bottom = centro + (maxTamanho / 2f)
                reguaAltura = maxTamanho
            }
            val reguaCenterY = top + (reguaAltura / 2f)

            canvas.drawLine(btnCx, top, btnCx, bottom, paintTrack)

            val espacoTraco = reguaAltura / 20
            for (i in -10..10) {
                val y = reguaCenterY + (i * espacoTraco)
                
                // ═══ NOVO: Lógica dos traços cruzando a linha e zero amarelo ═══
                if (i == 0) {
                    val tamanhoZero = 8f * dp
                    canvas.drawLine(btnCx - tamanhoZero, y, btnCx + tamanhoZero, y, paintTracoZero)
                } else {
                    val isTracoMaior = (i % 5 == 0)
                    val tamanhoTraco = if (isTracoMaior) 5f * dp else 2.5f * dp
                    canvas.drawLine(btnCx - tamanhoTraco, y, btnCx + tamanhoTraco, y, paintTraco)
                }
            }
            val thumbY = reguaCenterY - (valorAtual * (reguaAltura / 2f))
            canvas.drawCircle(btnCx, thumbY, 7.5f * dp, paintThumb)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        val horizontal = isHorizontal()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (horizontal) {
                    val toqueEsquerda = abaLeft - (12f * dp)
                    val toqueDireita = abaRight + (12f * dp)
                    val toqueTop = abaTop - (10f * dp)
                    val toqueBottom = abaBottom + (16f * dp)

                    if (x in toqueEsquerda..toqueDireita && y in toqueTop..toqueBottom) {
                        alternarGaveta()
                        return true
                    }

                    if (!isGavetaAberta) return false
                }

                if (Math.hypot((x - btnCx).toDouble(), (y - btnCy).toDouble()) <= btnRaio * 1.5f) {
                    modoAtual = when (modoAtual) {
                        Modo.BRILHO      -> Modo.NITIDEZ
                        Modo.NITIDEZ     -> Modo.VETORIZACAO
                        Modo.VETORIZACAO -> Modo.BRILHO
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); invalidate()
                    onValorMudou?.invoke(modoAtual, valorDoModoAtivo())
                    return true
                }

                val areaValida = if (horizontal) {
                    x > btnCx + btnRaio && Math.abs(y - btnCy) < 40f * dp
                } else {
                    if (alinharEsquerda) (x < btnCx + (60f * dp) && y > btnCy + btnRaio) else (x > btnCx - (60f * dp) && y > btnCy + btnRaio)
                }

                if (areaValida) { isDragging = true; return true }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    var novoValor = 0f

                    if (horizontal) {
                        val reguaLeft = btnCx + btnRaio + (24f * dp)
                        val reguaRight = width - (20f * dp)
                        val reguaLargura = reguaRight - reguaLeft
                        val reguaCenterX = reguaLeft + (reguaLargura / 2f)
                        novoValor = (x - reguaCenterX) / (reguaLargura / 2f)
                    } else {
                        val maxTamanho = 280f * dp
                        val h = height.toFloat()
                        var top = btnCy + btnRaio + (20f * dp)
                        var reguaAltura = h - (20f * dp) - top
                        if (reguaAltura > maxTamanho) {
                            val centro = top + (reguaAltura / 2f)
                            top = centro - (maxTamanho / 2f)
                            reguaAltura = maxTamanho
                        }
                        val reguaCenterY = top + (reguaAltura / 2f)
                        novoValor = (reguaCenterY - y) / (reguaAltura / 2f)
                    }

                    novoValor = novoValor.coerceIn(-1f, 1f)

                    when (modoAtual) {
                        Modo.BRILHO -> { valorBrilho = novoValor; onValorMudou?.invoke(modoAtual, valorBrilho) }
                        Modo.NITIDEZ -> { valorNitidez = novoValor; onValorMudou?.invoke(modoAtual, valorNitidez) }
                        Modo.VETORIZACAO -> { valorVetorizacao = novoValor; onValorMudou?.invoke(modoAtual, valorVetorizacao) }
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return true
    }

    private fun alternarGaveta() {
        val alvo = if (isGavetaAberta) -alturaBarra else 0f
        val anim = ValueAnimator.ofFloat(gavetaOffsetY, alvo)
        anim.duration = 300 
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            gavetaOffsetY = it.animatedValue as Float
            invalidate()
        }
        anim.start()
        isGavetaAberta = !isGavetaAberta
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
