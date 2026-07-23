package com.raylson.jansen.inspetor

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

class FocoAnimadoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // A "Caneta" que vai desenhar o círculo branco vazado
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE // Apenas a borda (vazado)
        strokeWidth = 4f * resources.displayMetrics.density // Grossura da linha adaptável
        isAntiAlias = true // Bordas suaves, sem serrilhado
        alpha = 0 // Começa totalmente invisível
    }

    private var focusX = 0f
    private var focusY = 0f
    private var radius = 0f

    private var animatorSet: AnimatorSet? = null

    /**
     * Função que a Câmera vai chamar toda vez que o inspetor tocar na tela.
     */
    fun animarFoco(x: Float, y: Float) {
        focusX = x
        focusY = y
        
        // Se o usuário tocar várias vezes rápido, cancela a animação anterior
        animatorSet?.cancel()

        // O tamanho do círculo adaptado para qualquer tamanho de tela
        val maxRadius = 55f * resources.displayMetrics.density
        val minRadius = 30f * resources.displayMetrics.density
        
        // 1. Animação de Encolher (O "Pulso" do Instagram)
        // Vai do tamanho grande para o pequeno muito rápido (250 milissegundos)
        val shrinkAnimator = ValueAnimator.ofFloat(maxRadius, minRadius).apply {
            duration = 250 
            // O Decelerate faz ele começar rápido e frear suavemente no final (efeito mola)
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                radius = it.animatedValue as Float
                paint.alpha = 255 // Fica 100% visível
                invalidate() // Manda a tela redesenhar imediatamente
            }
        }

        // 2. Animação de Desaparecer (Fade Out)
        val fadeOutAnimator = ValueAnimator.ofInt(255, 0).apply {
            duration = 300
            startDelay = 800 // Fica parado na tela por quase 1 segundo antes de sumir
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                paint.alpha = it.animatedValue as Int
                invalidate()
            }
        }

        // Toca as duas animações em sequência (Primeiro pulsa, espera e depois some)
        animatorSet = AnimatorSet().apply {
            playSequentially(shrinkAnimator, fadeOutAnimator)
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Só gasta processamento para desenhar se o círculo estiver visível
        if (paint.alpha > 0) {
            canvas.drawCircle(focusX, focusY, radius, paint)
        }
    }
}
