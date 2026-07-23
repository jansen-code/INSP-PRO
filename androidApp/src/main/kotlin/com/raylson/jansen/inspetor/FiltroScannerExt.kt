package com.raylson.jansen.inspetor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * FiltroScannerExt.kt — filtros "mágicos" de scanner (tipo CamScanner),
 * usando só android.graphics.ColorMatrix / ColorMatrixColorFilter.
 * Sem OpenCV, sem ML Kit, sem nenhuma dependência externa.
 *
 * ═══ O QUE UMA ColorMatrix CONSEGUE FAZER E O QUE NÃO CONSEGUE ═══
 * ColorMatrix é uma transformação LINEAR: a mesma fórmula matemática é
 * aplicada IGUAL em todo pixel da foto, sem olhar pra vizinhança dele.
 * Isso é ótimo pra "estourar" um fundo claro pro branco e escurecer texto
 * em fotos com iluminação razoavelmente uniforme — só que ela NÃO faz
 * remoção de sombra "de verdade" (aquela sombra localizada, tipo a mão
 * ou a dobra da folha tampando um canto da foto). Remoção de sombra local
 * de verdade exige processamento adaptativo por região — isso é
 * literalmente o que motor tipo OpenCV/ML Kit fazem por trás dos panos,
 * e está fora do que dá pra fazer só com Android nativo. O que entrego
 * aqui é a técnica padrão dos apps de scanner "leves": contraste e
 * brilho agressivos — resolve bem sombra leve e fundo amarelado/cinza
 * de papel, mas não apaga uma sombra forte e localizada.
 *
 * IMPORTANTE: as duas funções abaixo são pesadas (a de P&B principalmente,
 * que faz uma passada pixel a pixel). Nunca chame direto na main thread —
 * sempre dentro de um `withContext(Dispatchers.Default) { ... }`.
 */

/**
 * Filtro "MÁGICA" — aumenta contraste e brilho de forma agressiva pra
 * estourar o fundo do papel (cinza/amarelado → branco) e escurecer a
 * tinta da caneta/lápis (destaca o texto).
 *
 * ═══ Como a matriz funciona (explicação dos canais RGBA) ═══
 * ColorMatrix é uma matriz 4x5: uma linha por canal de SAÍDA (R,G,B,A).
 * Cada linha diz "de quanto de R, G, B, A da entrada, mais um valor fixo
 * (offset), eu preciso pra montar esse canal de saída":
 *
 *   R' = escala·R + 0·G + 0·B + 0·A + offset
 *   G' = 0·R + escala·G + 0·B + 0·A + offset
 *   B' = 0·R + 0·G + escala·B + 0·A + offset
 *   A' = A                                        (transparência intacta)
 *
 * Só mexemos na diagonal (R com R, G com G, B com B) — ou seja, tratamos
 * os 3 canais de cor IGUAL, sem distorcer o tom de cor, só a intensidade.
 *
 * `escala` (o parâmetro `contraste`) > 1 empurra cada canal pra LONGE do
 * meio-tom (127): valores já claros (o fundo do papel) vão em direção ao
 * 255 (branco), valores já escuros (a tinta) vão em direção ao 0 (preto).
 * É por isso que um cinza-claro de fundo de papel "estoura" pra branco
 * puro, enquanto o preto do texto fica ainda mais escuro — sem os dois
 * se misturarem, porque já começam em lados opostos do meio-tom.
 *
 * `offset` (calculado a partir do parâmetro `brilho`) desloca tudo pra
 * cima igualmente — é o empurrão extra pra garantir que o branco do
 * papel bata 255 de verdade, mesmo com pouca luz na hora da foto.
 */
fun Bitmap.aplicarFiltroMagico(contraste: Float = 1.9f, brilho: Float = 18f): Bitmap {
    val escala = contraste
    // Fórmula padrão de contraste em torno do meio-tom (127.5), somada ao brilho:
    val translacao = (-0.5f * escala + 0.5f) * 255f + brilho

    val cm = ColorMatrix(
        floatArrayOf(
            escala, 0f, 0f, 0f, translacao,
            0f, escala, 0f, 0f, translacao,
            0f, 0f, escala, 0f, translacao,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val saida = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(saida)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(cm)
    }
    canvas.drawBitmap(this, 0f, 0f, paint)
    return saida
}

/**
 * Filtro "PRETO E BRANCO LIMPO" — dessatura 100% e aplica um LIMIAR
 * (threshold) de verdade: cada pixel final é branco puro (255) OU preto
 * puro (0), sem meio-termo. Efeito de "folha impressa", tipo raio-x de
 * documento escaneado.
 *
 * ═══ Por que isso precisa de DUAS etapas (e não só ColorMatrix) ═══
 * Um threshold binário de verdade é uma função EM DEGRAU (se x >= limiar,
 * vira 255; senão, vira 0) — isso é uma descontinuidade, e ColorMatrix só
 * sabe fazer transformações LINEARES/contínuas. Com ColorMatrix sozinha
 * dá pra CHEGAR PERTO de um threshold usando contraste extremo (é o que
 * o filtro Mágico acima já faz, só que mais moderado), mas sempre sobra
 * uma faixa cinza de transição. Por isso aqui a gente faz assim:
 *
 *   1) ColorMatrix com setSaturation(0f) — tira toda a cor (vira escala
 *      de cinza "de verdade", usando os pesos de luminância padrão do
 *      Android: 0.213·R + 0.715·G + 0.072·B — o Android já usa esses
 *      pesos internamente no setSaturation, por isso o verde pesa mais
 *      que o vermelho e o azul: é assim que o olho humano enxerga brilho).
 *   2) Uma passada pixel a pixel decidindo, pra cada um: se o tom de
 *      cinza for MAIOR OU IGUAL a `limiar`, vira branco; senão, preto.
 *      Essa segunda etapa é a única forma de gerar um degrau de verdade,
 *      e é o motivo de essa função ser mais pesada que a Mágica (por
 *      isso é ainda mais importante rodar em Dispatchers.Default).
 *
 * `limiar` vai de 0 a 255 (padrão 150). Se a digitalização sair "toda
 * preta", aumente o limiar; se sair "toda branca" (perdendo o texto),
 * diminua.
 */
fun Bitmap.aplicarFiltroPB(limiar: Int = 150): Bitmap {
    // 1) Dessaturação via ColorMatrix — rápida, feita pelo Canvas/Paint.
    val cmCinza = ColorMatrix().apply { setSaturation(0f) }
    val cinza = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvasCinza = Canvas(cinza)
    val paintCinza = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(cmCinza)
    }
    canvasCinza.drawBitmap(this, 0f, 0f, paintCinza)

    // 2) Threshold binário de verdade, pixel a pixel.
    val w = cinza.width
    val h = cinza.height
    val pixels = IntArray(w * h)
    cinza.getPixels(pixels, 0, w, 0, 0, w, h)
    cinza.recycle()

    for (i in pixels.indices) {
        val p = pixels[i]
        val tom = (p shr 16) and 0xFF // já é cinza aqui, R=G=B — só precisa de 1 canal
        pixels[i] = if (tom >= limiar) Color.WHITE else Color.BLACK
    }

    val saida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    saida.setPixels(pixels, 0, w, 0, 0, w, h)
    return saida
}
