package com.raylson.jansen.inspetor.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.raylson.jansen.inspetor.domain.ItemHm
import com.raylson.jansen.inspetor.domain.LagoNA

actual fun vibrateStrong() {
    // TODO: Plugar o Vibrator do Android aqui futuramente
}

actual fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

actual fun decodeImageSafely(rawBytes: ByteArray, maxSide: Int): ByteArray? {
    // TODO: Plugar a sua lógica de compressão e EXIF do Android
    return rawBytes 
}

actual fun pickImageFromGallery(onResult: (ByteArray?) -> Unit) {
    // TODO: Plugar o ActivityResultLauncher (Intent de galeria)
}

actual fun shareImage(bytes: ByteArray, label: String) {
    // TODO: Plugar o Intent.ACTION_SEND
}

actual object OverlayRenderer {
    actual fun gerarRegistroHm(item: ItemHm, horaOverride: String?): RegistroGerado {
        // TODO: Chamar o seu motor antigo (ImageHelper)
        return RegistroGerado(ByteArray(0), ByteArray(0))
    }

    actual fun gerarRegistroNA(lago: LagoNA, valorNA: String?, horaOverride: String): RegistroGerado {
        // TODO: Chamar o seu motor antigo (ImageHelper)
        return RegistroGerado(ByteArray(0), ByteArray(0))
    }

    actual fun gerarRegistroLivre(item: ItemHm, horaOverride: String?): RegistroGerado {
        // TODO: Chamar o seu motor antigo (ImageHelper)
        return RegistroGerado(ByteArray(0), ByteArray(0))
    }
}