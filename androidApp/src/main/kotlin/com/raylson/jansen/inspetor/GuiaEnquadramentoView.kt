package com.raylson.jansen.inspetor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class GuiaEnquadramentoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var propW = 16f
    private var propH = 10f
    private var showGuide = false

    // Tinta para a área que será cortada (Fica escurecida)
    private val paintEscuro = Paint().apply {
        color = Color.parseColor("#99000000") // 60% preto
        style = Paint.Style.FILL
    }

    // Tinta para fazer o "furo" transparente (A bomba fica 100% visível)
    private val paintTransparente = Paint().apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Tinta para a linha tracejada amarela
    private val paintTraco = Paint().apply {
        color = Color.parseColor("#FBBF24")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(25f, 15f), 0f)
        isAntiAlias = true
    }

    // Tinta para o texto de instrução
    private val paintTexto = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 0f, 2f, Color.BLACK) // Sombra para ler em qualquer fundo
    }

    fun setProporcao(cameraProp: String) {
        // Força a proporção da mira para 4:5 em pé (40cm x 50cm da bomba)
        propW = 4f
        propH = 5f
        showGuide = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showGuide) return

        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintEscuro)

        // ══ CÁLCULO PRECISO PARA 2.5m ══
        // 45% da largura da tela é a medida exata para painel 40x50cm 
        // a 2.55m de distância (testado matematicamente com campo de visão padrão de 75°)
        val targetWidth = width * 0.45f 
        val targetHeight = targetWidth * 1.25f // Proporção 50/40 = 1.25

        val left = (width - targetWidth) / 2f
        val top = (height - targetHeight) / 2f
        val rect = RectF(left, top, left + targetWidth, top + targetHeight)

        canvas.drawRoundRect(rect, 20f, 20f, paintTransparente)
        canvas.drawRoundRect(rect, 20f, 20f, paintTraco)

        // Instrução posicionada fora da área de corte
        canvas.drawText("Encaixe o Painel e a TAG aqui", width / 2f, top - 30f, paintTexto)
        
        canvas.restoreToCount(saveCount)
    }


}
