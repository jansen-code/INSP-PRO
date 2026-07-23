package com.raylson.jansen.inspetor

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * Borda prateada brilhante (estilo alumÃ­nio escovado) que gira continuamente.
 *
 * Recria o efeito do CSS conic-gradient rotativo usado em:
 *   github.com/trananhtuat/css-animate-button-border
 *
 * CaracterÃ­sticas:
 *  â€¢ Desenha SOMENTE a borda (stroke) â€” o interior fica transparente,
 *    deixando o conteÃºdo do CardView aparecer.
 *  â€¢ Acompanha cantos arredondados (cornerRadius configurÃ¡vel).
 *  â€¢ Gradiente sweep com tons branco / prata / cinza claro, dando
 *    aspecto de luz batendo em alumÃ­nio polido.
 *  â€¢ Implementa Animatable: basta chamar start() / stop().
 *
 * Uso tÃ­pico (dentro do onBindViewHolder):
 *
 *   val borda = SilverBorderDrawable(
 *       strokeWidthPx = dp(2).toFloat(),
 *       cornerRadiusPx = dp(14).toFloat()
 *   )
 *   card.foreground = borda
 *   borda.start()
 *
 *   // para parar:
 *   borda.stop()
 *   card.foreground = null
 */
class SilverBorderDrawable(
    private val strokeWidthPx: Float,
    private val cornerRadiusPx: Float,
    private val durationMs: Long = 1800L
) : Drawable(), Animatable {

    // ---------- Pintura da borda ----------
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Cores do "alumÃ­nio brilhante": branco -> prata -> cinza -> prata -> branco
    // O ponto "quente" (branco) Ã© o brilho que gira ao redor da borda.
    private val sweepColors = intArrayOf(
        0xFFFFFFFF.toInt(), // branco puro (highlight)
        0xFFE8E8EC.toInt(), // prata clara
        0xFFB8BCC4.toInt(), // prata mÃ©dia
        0xFF8A8E96.toInt(), // cinza escuro (sombra)
        0xFFB8BCC4.toInt(), // prata mÃ©dia
        0xFFE8E8EC.toInt(), // prata clara
        0xFFFFFFFF.toInt()  // branco puro (fecha o loop)
    )
    private val sweepPositions = floatArrayOf(0f, 0.15f, 0.35f, 0.5f, 0.65f, 0.85f, 1f)

    private val borderPath = Path()
    private val tmpRect = RectF()

    private var rotation = 0f
    private var running = false

    private val animator: ValueAnimator =
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotation = it.animatedValue as Float
                invalidateSelf()
            }
        }

    // ---------- Drawable ----------

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPath()
        rebuildShader()
    }

    private fun rebuildPath() {
        val b = bounds
        if (b.isEmpty) return
        // Inset metade do stroke para a borda ficar inteira dentro do bounds
        val inset = strokeWidthPx / 2f
        tmpRect.set(
            b.left + inset,
            b.top + inset,
            b.right - inset,
            b.bottom - inset
        )
        borderPath.reset()
        borderPath.addRoundRect(tmpRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
    }

    private fun rebuildShader() {
        val b = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        // SweepGradient base; a rotaÃ§Ã£o Ã© aplicada via Matrix em draw()
        paint.shader = SweepGradient(cx, cy, sweepColors, sweepPositions)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        // Aplica rotaÃ§Ã£o atual no shader (mais leve que rotacionar o canvas).
        val m = Matrix().apply {
            postRotate(rotation, cx, cy)
        }
        paint.shader?.setLocalMatrix(m)

        canvas.drawPath(borderPath, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ---------- Animatable ----------

    override fun start() {
        if (running) return
        running = true
        if (!animator.isStarted) animator.start() else animator.resume()
    }

    override fun stop() {
        if (!running) return
        running = false
        animator.cancel()
        rotation = 0f
        invalidateSelf()
    }

    override fun isRunning(): Boolean = running
}