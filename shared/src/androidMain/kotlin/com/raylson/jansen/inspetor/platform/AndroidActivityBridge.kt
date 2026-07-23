package com.raylson.jansen.inspetor.platform

import android.content.Intent

/**
 * ═══════════════════════════════════════════════════════════════════
 * AndroidActivityBridge.kt (androidMain)
 *
 * `pickImageFromGallery` (expect fun, chamado de dentro do
 * ScreenModel/commonMain, sem nenhuma Activity de referência) precisa
 * abrir a galeria — algo que só uma Activity pode fazer no Android.
 *
 * Solução: lançar uma `GalleryPickerActivity` transparente (ver esse
 * arquivo) a partir do `applicationContext`, e guardar o callback aqui
 * até ela devolver o resultado. Como tudo roda no mesmo processo, um
 * `object` (singleton em memória) é suficiente — não precisa Intent
 * extra nem serialização do callback.
 *
 * Assinatura pedida: `fun launchGalleryPicker(onResult: (ByteArray?) -> Unit)`
 * ═══════════════════════════════════════════════════════════════════
 */
object AndroidActivityBridge {

    private var pendingGalleryCallback: ((ByteArray?) -> Unit)? = null

    /** Chamado por `pickImageFromGallery` (PlatformMappers.android.kt). */
    fun launchGalleryPicker(onResult: (ByteArray?) -> Unit) {
        // Só uma seleção de galeria pendente por vez — se chamar de novo
        // antes da primeira terminar, a mais antiga é descartada (mesmo
        // comportamento implícito que o requestCode fixo "203" tinha no
        // app original: uma captura por vez).
        pendingGalleryCallback = onResult

        val context = AndroidContextHolder.appContext
        val intent = Intent(context, GalleryPickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Chamado pela GalleryPickerActivity assim que tem (ou não tem) resultado. */
    internal fun entregarResultadoGaleria(bytes: ByteArray?) {
        val callback = pendingGalleryCallback
        pendingGalleryCallback = null
        callback?.invoke(bytes)
    }
}
