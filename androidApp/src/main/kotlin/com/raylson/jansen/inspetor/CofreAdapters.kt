package com.raylson.jansen.inspetor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Adapter do grid de fotos do Cofre com suporte a TÍTULOS DE SUBPASTAS e multi-seleção.
 *
 * NOTA: o antigo CofrePastaAdapter (grade de "pastas" clicáveis) foi
 * removido — os 3 grupos (DET-01 / N.A. / ARB'S) agora são botões fixos
 * desenhados direto no activity_cofre.xml, não uma lista.
 */
class CofreFotoAdapter(
    private val itensMix: MutableList<Any>, // Recebe String (Header) e ItemCofre (Foto)
    private val emModoSelecao: () -> Boolean,
    private val estaSelecionado: (CofreManager.ItemCofre) -> Boolean,
    private val onAbrir: (CofreManager.ItemCofre, ImageView) -> Unit,
    private val onLongPressOuToggleSelecao: (CofreManager.ItemCofre) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TIPO_HEADER = 0
        const val TIPO_FOTO = 1
    }

    // ═══ PERFORMANCE: era um pool fixo de 4 threads em qualquer aparelho.
    // Agora escala com os núcleos reais do processador (a maioria dos
    // celulares tem 6-8), aproveitando melhor o hardware pra decodificar
    // mais miniaturas em paralelo. ═══
    private val executor: ExecutorService = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors()).coerceIn(4, 8)
    )
    private val cacheMiniaturas = LinkedHashMap<String, Bitmap>(64, 0.75f, true)
    private val cacheLimite = 72

    override fun getItemViewType(position: Int): Int {
        return if (itensMix[position] is String) TIPO_HEADER else TIPO_FOTO
    }

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewWithTag("tvTituloHeader")
    }

    class FotoVH(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardFotoCofre)
        val img: ImageView = view.findViewById(R.id.imgMiniaturaCofre)
        val check: MaterialCardView = view.findViewById(R.id.checkFotoCofre)
        val tvTick: TextView = view.findViewById(R.id.tvCheckTickFotoCofre)
        val tvRotulo: TextView = view.findViewById(R.id.tvRotuloFotoCofre)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val dm = parent.resources.displayMetrics
        if (viewType == TIPO_HEADER) {
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (24f * dm.density).toInt()
                    bottomMargin = (8f * dm.density).toInt()
                    marginStart = (6f * dm.density).toInt()
                    marginEnd = (6f * dm.density).toInt()
                }
            }
            val tv = TextView(parent.context).apply {
                tag = "tvTituloHeader"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#64748B"))
                isAllCaps = true
            }
            val linha = View(parent.context).apply {
                setBackgroundColor(Color.parseColor("#E2E8F0"))
                layoutParams = LinearLayout.LayoutParams(0, (1f * dm.density).toInt(), 1f).apply {
                    marginStart = (12f * dm.density).toInt()
                }
            }
            container.addView(tv)
            container.addView(linha)
            return HeaderVH(container)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cofre_foto, parent, false)
            return FotoVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderVH) {
            holder.tvTitulo.text = itensMix[position] as String
        } else if (holder is FotoVH) {
            val item = itensMix[position] as CofreManager.ItemCofre
            val path = item.arquivo.absolutePath
            val modoSelecao = emModoSelecao()
            val selecionado = estaSelecionado(item)

            holder.tvRotulo.text = item.dataHoraFormatada
            holder.img.tag = path

            val cacheada = synchronized(cacheMiniaturas) { cacheMiniaturas[path] }
            if (cacheada != null) {
                holder.img.setImageBitmap(cacheada)
                holder.img.background = null
            } else {
                // ═══ Em vez de limpar pra um branco puro (que gera o
                // "flash" incômodo entre uma foto e outra), mostra um
                // cinza suave enquanto a miniatura real ainda carrega. ═══
                holder.img.setImageBitmap(null)
                holder.img.setBackgroundColor(Color.parseColor("#E8ECF1"))
                executor.execute {
                    val bmp = CofreManager.carregarMiniatura(holder.img.context, item.arquivo)
                    holder.img.post {
                        if (holder.img.tag == path && bmp != null) {
                            holder.img.setImageBitmap(bmp)
                            holder.img.background = null
                            synchronized(cacheMiniaturas) {
                                cacheMiniaturas[path] = bmp
                                if (cacheMiniaturas.size > cacheLimite) {
                                    val chaveMaisAntiga = cacheMiniaturas.entries.firstOrNull()?.key
                                    if (chaveMaisAntiga != null) cacheMiniaturas.remove(chaveMaisAntiga)
                                }
                            }
                        }
                    }
                }
            }

            aplicarEstadoVisual(holder, modoSelecao, selecionado)

            holder.card.setOnClickListener {
                if (emModoSelecao()) onLongPressOuToggleSelecao(item) else onAbrir(item, holder.img)
            }
            holder.card.setOnLongClickListener {
                onLongPressOuToggleSelecao(item)
                true
            }
        }
    }

    // ═══ Caixinha de seleção: some por completo fora do modo de seleção.
    // Vazia (contorno cinza, sem tick) = não selecionada.
    // Verde sólida com "✓" branco = selecionada. ═══
    private fun aplicarEstadoVisual(holder: FotoVH, modoSelecao: Boolean, selecionado: Boolean) {
        holder.check.visibility = if (modoSelecao) View.VISIBLE else View.GONE
        if (modoSelecao && selecionado) {
            holder.check.setCardBackgroundColor(Color.parseColor("#22C55E"))
            holder.check.strokeWidth = 0
            holder.tvTick.text = "✓"
        } else if (modoSelecao) {
            holder.check.setCardBackgroundColor(Color.WHITE)
            holder.check.strokeWidth = (1.4f * holder.check.resources.displayMetrics.density).toInt()
            holder.tvTick.text = ""
        }
        holder.card.alpha = if (modoSelecao && !selecionado) 0.85f else 1f
    }

    override fun getItemCount() = itensMix.size

    fun removerItem(item: CofreManager.ItemCofre) {
        val pos = itensMix.indexOf(item)
        if (pos != -1) {
            itensMix.removeAt(pos)
            notifyItemRemoved(pos)

            // Limpa o cabeçalho se ele ficar vazio
            if (pos > 0 && itensMix[pos - 1] is String) {
                if (pos == itensMix.size || itensMix[pos] is String) {
                    itensMix.removeAt(pos - 1)
                    notifyItemRemoved(pos - 1)
                }
            }
        }
    }

    fun encerrar() = executor.shutdownNow()
}
