package com.raylson.jansen.inspetor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

class ZoomRulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val ZOOM_MIN = 0.6f
    private val ZOOM_MAX = 100.0f
    private val ZOOM_START = 1.0f
    private val anchors = listOf(0.6f, 1f, 2f, 4f, 10f, 20f, 30f, 50f, 100f)

    private var currentZoom: Float = ZOOM_START
    private var scrollPx: Float = 0f
    private var lastTouchX: Float = 0f
    
    // Variáveis para distinguir Clique de Arrasto
    private var startTouchX: Float = 0f
    private var isDragging: Boolean = false
    
    private var rulerTotalPx: Float = 0f

    var onZoomChanged: ((zoom: Float) -> Unit)? = null

    private val dp = context.resources.displayMetrics.density
    
    // Sensibilidade dobrada!
    private val SENSITIVITY = 3.5f 

    private val paintTickWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * dp
        strokeCap = Paint.Cap.ROUND
        alpha = 230
    }
    
    // AS CORES ORIGINAIS VOLTARAM AQUI:
    private val paintTickYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107") // Amarelo
        strokeWidth = 3f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * dp
        textAlign = Paint.Align.CENTER
        alpha = 220
    }
    private val paintPill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6000000") // Escuro opaco
    }
    private val paintPillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107") // Amarelo
        textSize = 14f * dp
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val tickHeightSmall = 8f * dp
    private val tickHeightAnchor = 16f * dp
    private val tickHeightCursor = 22f * dp
    private val pillH = 32f * dp
    private val pillPadH = 16f * dp

    private fun zoomToFraction(z: Float): Float {
        val logMin = log10(ZOOM_MIN.toDouble())
        val logMax = log10(ZOOM_MAX.toDouble())
        return ((log10(z.toDouble()) - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
    }

    private fun fractionToZoom(f: Float): Float {
        val logMin = log10(ZOOM_MIN.toDouble())
        val logMax = log10(ZOOM_MAX.toDouble())
        return (10.0.pow(logMin + f.toDouble() * (logMax - logMin))).toFloat()
            .coerceIn(ZOOM_MIN, ZOOM_MAX)
    }

    private fun zoomToPx(z: Float): Float = zoomToFraction(z) * rulerTotalPx
    private fun pxToZoom(px: Float): Float = fractionToZoom(px / rulerTotalPx)

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        rulerTotalPx = w * 3.2f
        scrollPx = zoomToPx(currentZoom) - w / 2f
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rulerTotalPx == 0f) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val cx = viewWidth / 2f

        // 1. PILL NO TOPO
        val pillTop = 4f * dp
        val zoomText = formatZoom(currentZoom)
        val textW = paintPillText.measureText(zoomText)
        val pillW = textW + pillPadH * 2
        val pillLeft = cx - pillW / 2f
        canvas.drawRoundRect(
            RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH),
            pillH / 2f, pillH / 2f,
            paintPill
        )
        canvas.drawText(
            zoomText,
            cx,
            pillTop + pillH/2f + paintPillText.textSize * 0.35f,
            paintPillText
        )

        // 2. RÉGUA ABAIXO DO PILL
        val tickCenterY = pillTop + pillH + 20f * dp
        val labelY = tickCenterY + tickHeightAnchor + 14f * dp

        // ticks pequenos
        val totalSteps = 200
        for (i in 0..totalSteps) {
            val frac = i.toFloat() / totalSteps
            val screenX = frac * rulerTotalPx - scrollPx
            if (screenX < -20f || screenX > viewWidth + 20f) continue
            canvas.drawLine(screenX, tickCenterY - tickHeightSmall/2,
                screenX, tickCenterY + tickHeightSmall/2, paintTickWhite)
        }

        // âncoras
        for (anchor in anchors) {
            val screenX = zoomToPx(anchor) - scrollPx
            if (screenX < -40f || screenX > viewWidth + 40f) continue
            canvas.drawLine(screenX, tickCenterY - tickHeightAnchor/2,
                screenX, tickCenterY + tickHeightAnchor/2, paintTickWhite)
            val label = when {
                abs(anchor - 0.6f) < 0.01f -> ".6"
                anchor == 100f -> "100"
                else -> anchor.toInt().toString()
            }
            canvas.drawText(label, screenX, labelY, paintLabel)
        }

        // cursor indicador do centro
        canvas.drawLine(cx, tickCenterY - tickHeightCursor/2,
            cx, tickCenterY + tickHeightCursor/2, paintTickYellow)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                startTouchX = event.x
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                // Se moveu o dedo mais de 10 pixels, entende que é "Arrastar"
                if (abs(event.x - startTouchX) > 10f) {
                    isDragging = true
                }
                
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    lastTouchX = event.x
                    scrollPx = (scrollPx - dx * SENSITIVITY).coerceIn(
                        zoomToPx(ZOOM_MIN) - width / 2f,
                        zoomToPx(ZOOM_MAX) - width / 2f
                    )
                    currentZoom = pxToZoom(scrollPx + width / 2f).coerceIn(ZOOM_MIN, ZOOM_MAX)
                    invalidate()
                    onZoomChanged?.invoke(currentZoom)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Se NÃO arrastou o dedo, então foi um CLIQUE!
                if (!isDragging && event.action == MotionEvent.ACTION_UP) {
                    val cx = width / 2f
                    val distCentro = event.x - cx
                    
                    // Puxa a régua para o ponto exato que o utilizador clicou
                    scrollPx = (scrollPx + distCentro).coerceIn(
                        zoomToPx(ZOOM_MIN) - cx,
                        zoomToPx(ZOOM_MAX) - cx
                    )
                    currentZoom = pxToZoom(scrollPx + cx).coerceIn(ZOOM_MIN, ZOOM_MAX)
                }
                
                // Arredonda para ficar exato
                val snapped = (currentZoom * 10f).toInt() / 10f
                setZoom(snapped, notify = true)
            }
        }
        return true
    }

    fun setZoom(zoom: Float, notify: Boolean = false) {
        currentZoom = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
        if (rulerTotalPx > 0f) scrollPx = zoomToPx(currentZoom) - width / 2f
        invalidate()
        if (notify) onZoomChanged?.invoke(currentZoom)
    }

    fun getCurrentZoom(): Float = currentZoom

    private fun formatZoom(z: Float): String = when {
        z >= 99.5f -> "100×"
        z >= 9.95f -> "${z.toInt()}×"
        abs(z - 0.6f) < 0.05f -> "0.6×"
        else -> String.format(Locale.US, "%.1f×", z)
    }
}
