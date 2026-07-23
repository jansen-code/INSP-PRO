package com.raylson.jansen.inspetor.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.raylson.jansen.inspetor.domain.ItemHm
import com.raylson.jansen.inspetor.domain.LagoNA

/**
 * ═══════════════════════════════════════════════════════════════════
 * PlatformMappers.kt (commonMain)
 *
 * Todo acesso "nativo" que a DashboardActivity fazia direto (Vibrator,
 * ACTION_PICK, CameraX, ContentResolver, Canvas/Bitmap) vira `expect fun`
 * aqui. Cada plataforma resolve com sua própria API (Android: as classes
 * de sempre; iOS: AVFoundation/PHPicker/CoreHaptics/CoreGraphics).
 *
 * A câmera em si (abrirCameraComCameraX) NÃO virou expect fun — ela é
 * uma TELA inteira (já convertida: CameraCaptureScreen.kt), então no
 * KMP isso é navegação (Voyager `navigator.push(CameraCaptureScreen(...))`)
 * e não uma chamada de função. O que sobra pra expect/actual é só o que
 * é puramente de plataforma e sem UI própria.
 * ═══════════════════════════════════════════════════════════════════
 */

/** Vibração forte de confirmação (era `vibrarForte()`). */
expect fun vibrateStrong()

/** Decodifica ByteArray -> ImageBitmap pra desenhar em Compose. */
expect fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap?

/**
 * Decodifica com segurança + corrige orientação EXIF + reduz resolução
 * (era `decodificarBitmapSeguro`). Recebe os bytes crus do arquivo
 * escolhido na galeria/cofre e devolve JPEG já normalizado.
 *
 * @param maxSide teto de resolução (era fixo em 1920px, exceto para o
 *   Scanner de Documentos "SC", que preserva a resolução nativa).
 */
expect fun decodeImageSafely(rawBytes: ByteArray, maxSide: Int = 1920): ByteArray?

/**
 * Abre o seletor nativo de imagens da plataforma (era `abrirGaleria`,
 * ACTION_PICK_IMAGES com fallback pra ACTION_PICK). `onResult` recebe os
 * bytes crus do arquivo escolhido, ou null se o usuário cancelou.
 */
expect fun pickImageFromGallery(onResult: (ByteArray?) -> Unit)

/** Compartilha uma imagem já gerada (era `compartilharUri`). */
expect fun shareImage(bytes: ByteArray, label: String)

/**
 * Interface pro motor de geração de overlay/tarja — o que hoje já está
 * centralizado em `ImageHelper.kt` (Canvas + Bitmap). Mantemos como
 * `expect object` porque desenho 2D de baixo nível não é portável 1:1
 * (Android usa android.graphics.Canvas; iOS usaria Skia/CoreGraphics).
 * A LÓGICA de "quais dados vão em qual camada" fica no ScreenModel
 * (commonMain); só o "como desenhar pixel a pixel" fica aqui.
 */
expect object OverlayRenderer {

    /** Equivalente a `gerarParBitmapRegistro` / `gerarParBitmapRegistroEmpilhado`. */
    fun gerarRegistroHm(item: ItemHm, horaOverride: String? = null): RegistroGerado

    /** Equivalente a `gerarParImagemFinalNA`. */
    fun gerarRegistroNA(lago: LagoNA, valorNA: String?, horaOverride: String): RegistroGerado

    /** Equivalente a `gerarParImagemFinalLivre`. */
    fun gerarRegistroLivre(item: ItemHm, horaOverride: String? = null): RegistroGerado
}

/**
 * Par (com tarja + limpa) que o app sempre gerou — era
 * `Pair<Bitmap, Bitmap>` / `Triple<Bitmap, Bitmap, Bitmap>` espalhado
 * pelo código original. Um único tipo nomeado deixa a intenção clara.
 */
data class RegistroGerado(
    val comTarja: ByteArray,
    val limpa: ByteArray
)
