package com.raylson.jansen.inspetor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * DocumentCropView — ferramenta de recorte/perspectiva 100% própria do
 * INSPETOR. Não usa ML Kit, não usa OpenCV, não tem NENHUMA dependência
 * do Google — é só Canvas + Matrix nativos do Android.
 *
 * Tem duas formas de mexer no quadrilátero:
 *
 *  1) Arrastando um dos 4 CANTOS (bolinha marrom) — move só aquele canto,
 *     livre. É o que corrige a perspectiva (documento fotografado torto
 *     em relação à câmera) e permite "achatar" o documento.
 *
 *  2) Arrastando uma das 4 alças de BORDA (bolinha branca no meio de cada
 *     lado) — move as DUAS pontas daquela borda JUNTAS e na mesma direção,
 *     então a linha nunca sai torta. Puxar a de cima/baixo mexe só no
 *     eixo vertical; puxar a da esquerda/direita mexe só no horizontal.
 *     É a forma "simétrica" pedida — resolve o corte ficar desajustado.
 */
class DocumentCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Cantos em coordenadas DA IMAGEM ORIGINAL (não da tela). Ordem: TL, TR, BR, BL.
    private var cantos: Array<PointF> = Array(4) { PointF() }
    private var bitmap: Bitmap? = null

    private val matrizFotoParaTela = Matrix()
    private val matrizTelaParaFoto = Matrix()
    private var matrizValida = false

    private var canoArrastado = -1   // índice do canto (0-3) sendo arrastado, -1 = nenhum
    private var bordaArrastada = -1  // índice da borda (0=topo,1=direita,2=baixo,3=esquerda), -1 = nenhuma
    private val raioToqueHandlePx = 70f

    // Modo de marcação por toque: quando ativo, cada toque move o canto
    // JÁ EXISTENTE mais PRÓXIMO daquele ponto — não uma ordem fixa. Depois
    // de 4 toques, dispara `onCantosDefinidos` (se atribuído) para que o
    // caller aplique o warp.
    private var tapMode = false
    private var tapsRestantes = 0
    var onCantosDefinidos: (() -> Unit)? = null

    /**
     * Ativa o modo de marcação por toque: os próximos 4 toques ajustam
     * os 4 cantos (cada toque "puxa" o canto mais próximo daquele ponto).
     */
    fun enableTapMode() {
        tapMode = true
        tapsRestantes = 4
    }

    private val corPrincipal = Color.parseColor("#8B5C29")
    private val corHandleBorda = Color.WHITE

    private val paintLinha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = corPrincipal
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val paintHandleCanto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = corPrincipal
        style = Paint.Style.FILL
    }
    private val paintHandleContorno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val paintHandleBorda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = corHandleBorda
        style = Paint.Style.FILL
    }
    private val paintFundoFora = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Fundo claro, no estilo branco do app — nada de escurecer a tela
        // como scanner de terceiro costuma fazer.
        color = Color.argb(235, 248, 250, 252)
        style = Paint.Style.FILL
    }

    /**
     * Define a foto a ser recortada.
     * @param cantosSugeridos se vier do DetectorBordaEngine (TL,TR,BR,BL em
     * coordenadas dessa MESMA foto), usa como ponto de partida do recorte.
     * Se vier null, usa a margem fixa de 6% pra dentro, como antes.
     */
    fun setImagem(bmp: Bitmap, cantosSugeridos: Array<PointF>? = null) {
        bitmap = bmp
        matrizValida = false

        if (cantosSugeridos != null && validarCantos(cantosSugeridos, bmp)) {
            cantos = arrayOf(
                PointF(cantosSugeridos[0].x, cantosSugeridos[0].y),
                PointF(cantosSugeridos[1].x, cantosSugeridos[1].y),
                PointF(cantosSugeridos[2].x, cantosSugeridos[2].y),
                PointF(cantosSugeridos[3].x, cantosSugeridos[3].y)
            )
        } else {
            val m = 0.06f
            cantos = arrayOf(
                PointF(bmp.width * m,       bmp.height * m),        // TL
                PointF(bmp.width * (1 - m), bmp.height * m),        // TR
                PointF(bmp.width * (1 - m), bmp.height * (1 - m)),  // BR
                PointF(bmp.width * m,       bmp.height * (1 - m))   // BL
            )
        }
        requestLayout()
        invalidate()
    }

    /**
     * ═══ NOVO: aplica cantos detectados automaticamente SEM recriar a
     * matriz/bitmap atual (usado pelo botão "detectar borda" no topo do
     * diálogo — a foto continua a mesma, só os 4 cantos mudam). Devolve
     * true se os cantos eram válidos e foram aplicados. ═══
     */
    fun aplicarCantosDetectados(novosCantos: Array<PointF>): Boolean {
        val bmp = bitmap ?: return false
        if (!validarCantos(novosCantos, bmp)) return false
        cantos = arrayOf(
            PointF(novosCantos[0].x, novosCantos[0].y),
            PointF(novosCantos[1].x, novosCantos[1].y),
            PointF(novosCantos[2].x, novosCantos[2].y),
            PointF(novosCantos[3].x, novosCantos[3].y)
        )
        invalidate()
        return true
    }

    /** Sanidade básica: os 4 pontos precisam caber na imagem e formar uma área razoável. */
    private fun validarCantos(c: Array<PointF>, bmp: Bitmap): Boolean {
        if (c.size != 4) return false
        for (p in c) {
            if (p.x < 0f || p.y < 0f || p.x > bmp.width || p.y > bmp.height) return false
        }
        return true
    }

    /** Volta o quadrilátero pra margem inicial, sem trocar a foto. */
    fun resetar() {
        bitmap?.let { setImagem(it) }
    }

    /** Cantos atuais em coordenadas da imagem original — TL, TR, BR, BL. */
    fun getCantosNaImagem(): Array<PointF> = cantos

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        matrizValida = false
    }

    private fun recalcularMatrizSeNecessario() {
        if (matrizValida) return
        val bmp = bitmap ?: return
        if (width == 0 || height == 0 || bmp.width == 0 || bmp.height == 0) return

        matrizFotoParaTela.reset()
        val escala = min(width / bmp.width.toFloat(), height / bmp.height.toFloat())
        val dx = (width - bmp.width * escala) / 2f
        val dy = (height - bmp.height * escala) / 2f
        matrizFotoParaTela.postScale(escala, escala)
        matrizFotoParaTela.postTranslate(dx, dy)
        matrizFotoParaTela.invert(matrizTelaParaFoto)
        matrizValida = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        recalcularMatrizSeNecessario()

        canvas.drawBitmap(bmp, matrizFotoParaTela, null)

        val pTela = cantos.map { pFotoParaTela(it) }

        // Máscara clara fora do quadrilátero
        val telaCompleta = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        }
        val quad = Path().apply {
            moveTo(pTela[0].x, pTela[0].y)
            lineTo(pTela[1].x, pTela[1].y)
            lineTo(pTela[2].x, pTela[2].y)
            lineTo(pTela[3].x, pTela[3].y)
            close()
        }
        telaCompleta.op(quad, Path.Op.DIFFERENCE)
        canvas.drawPath(telaCompleta, paintFundoFora)
        canvas.drawPath(quad, paintLinha)

        // Alças de canto
        pTela.forEach { p ->
            canvas.drawCircle(p.x, p.y, 16f, paintHandleCanto)
            canvas.drawCircle(p.x, p.y, 16f, paintHandleContorno)
        }

        // Alças de borda (meio de cada lado) — corte simétrico
        for (i in 0..3) {
            val a = pTela[i]
            val b = pTela[(i + 1) % 4]
            val meio = PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            canvas.drawCircle(meio.x, meio.y, 11f, paintHandleBorda)
            canvas.drawCircle(meio.x, meio.y, 11f, paintHandleContorno)
        }
    }

    private fun pFotoParaTela(p: PointF): PointF {
        val out = floatArrayOf(p.x, p.y)
        matrizFotoParaTela.mapPoints(out)
        return PointF(out[0], out[1])
    }

    private fun pTelaParaFoto(x: Float, y: Float): PointF {
        val out = floatArrayOf(x, y)
        matrizTelaParaFoto.mapPoints(out)
        return PointF(out[0], out[1])
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return false
        recalcularMatrizSeNecessario()
        // Se estivermos em tapMode, interceptamos toques simples e
        // convertemos direto para pontos na imagem.
        if (tapMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    val pFoto = pTelaParaFoto(event.x, event.y)
                    val fx = pFoto.x.coerceIn(0f, bmp.width.toFloat())
                    val fy = pFoto.y.coerceIn(0f, bmp.height.toFloat())
                    // ═══ CORREÇÃO: antes o toque preenchia cantos[] numa
                    // ORDEM FIXA (TL, TR, BR, BL) — só funcionava se o
                    // usuário tocasse exatamente nessa sequência. Se
                    // tocasse os 2 pontos do lado ESQUERDO primeiro (topo e
                    // baixo), o 2º toque caía no slot que deveria ser
                    // "topo-direita", embaralhando o quadrilátero (linhas se
                    // cruzando, efeito "borboleta"). Agora cada toque move
                    // o canto JÁ EXISTENTE mais PRÓXIMO daquele ponto — não
                    // importa a ordem que o usuário toca os 4 cantos do
                    // caderno, cada um vai pro slot geometricamente certo. ═══
                    val indiceMaisProximo = indiceCantoMaisProximo(fx, fy)
                    cantos[indiceMaisProximo] = PointF(fx, fy)
                    if (tapsRestantes > 0) tapsRestantes--
                    if (tapsRestantes <= 0) {
                        tapMode = false
                        // Notifica o caller que os 4 cantos foram definidos
                        onCantosDefinidos?.invoke()
                    }
                    invalidate()
                    return true
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                canoArrastado = encontrarCantoProximo(event.x, event.y)
                if (canoArrastado == -1) bordaArrastada = encontrarBordaProxima(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(canoArrastado != -1 || bordaArrastada != -1)
                return canoArrastado != -1 || bordaArrastada != -1
            }
            MotionEvent.ACTION_MOVE -> {
                val pFoto = pTelaParaFoto(event.x, event.y)
                val fx = pFoto.x.coerceIn(0f, bmp.width.toFloat())
                val fy = pFoto.y.coerceIn(0f, bmp.height.toFloat())

                if (canoArrastado != -1) {
                    cantos[canoArrastado] = PointF(fx, fy)
                    invalidate()
                    return true
                }
                if (bordaArrastada != -1) {
                    // Arraste SIMÉTRICO — move as duas pontas da borda juntas,
                    // mantendo a linha sempre reta.
                    when (bordaArrastada) {
                        0 -> { // topo (TL-TR): move só em Y
                            cantos[0] = PointF(cantos[0].x, fy)
                            cantos[1] = PointF(cantos[1].x, fy)
                        }
                        1 -> { // direita (TR-BR): move só em X
                            cantos[1] = PointF(fx, cantos[1].y)
                            cantos[2] = PointF(fx, cantos[2].y)
                        }
                        2 -> { // baixo (BR-BL): move só em Y
                            cantos[2] = PointF(cantos[2].x, fy)
                            cantos[3] = PointF(cantos[3].x, fy)
                        }
                        3 -> { // esquerda (BL-TL): move só em X
                            cantos[3] = PointF(fx, cantos[3].y)
                            cantos[0] = PointF(fx, cantos[0].y)
                        }
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                canoArrastado = -1
                bordaArrastada = -1
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun encontrarCantoProximo(x: Float, y: Float): Int {
        val pTela = cantos.map { pFotoParaTela(it) }
        for (i in pTela.indices) {
            if (hypot((x - pTela[i].x).toDouble(), (y - pTela[i].y).toDouble()) <= raioToqueHandlePx) return i
        }
        return -1
    }

    /**
     * Índice (0-3) do canto ATUAL (cantos[]) mais próximo de um ponto em
     * coordenadas DA IMAGEM (não da tela) — usado pelo modo de toque, pra
     * sempre mover o canto certo independente da ordem em que o usuário
     * toca os 4 cantos do documento.
     */
    private fun indiceCantoMaisProximo(fx: Float, fy: Float): Int {
        var melhorIndice = 0
        var menorDistancia = Float.MAX_VALUE
        for (i in cantos.indices) {
            val d = hypot((fx - cantos[i].x).toDouble(), (fy - cantos[i].y).toDouble()).toFloat()
            if (d < menorDistancia) {
                menorDistancia = d
                melhorIndice = i
            }
        }
        return melhorIndice
    }

    private fun encontrarBordaProxima(x: Float, y: Float): Int {
        val pTela = cantos.map { pFotoParaTela(it) }
        for (i in 0..3) {
            val a = pTela[i]
            val b = pTela[(i + 1) % 4]
            val meio = PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            if (hypot((x - meio.x).toDouble(), (y - meio.y).toDouble()) <= raioToqueHandlePx) return i
        }
        return -1
    }
}
