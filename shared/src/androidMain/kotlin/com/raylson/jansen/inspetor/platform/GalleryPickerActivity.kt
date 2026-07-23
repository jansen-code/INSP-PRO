package com.raylson.jansen.inspetor.platform

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * ═══════════════════════════════════════════════════════════════════
 * GalleryPickerActivity.kt (androidMain)
 *
 * "Activity-sombra": não tem UI nenhuma (tema transparente), existe só
 * pelo tempo de vida de "abrir o seletor de imagens -> receber Uri ->
 * fechar". Resolve o problema de `AndroidActivityBridge` (um `object`
 * KMP, sem Activity própria) precisar abrir a galeria, que no Android
 * SÓ pode ser feito a partir de uma Activity.
 *
 * Fluxo: AndroidActivityBridge.launchGalleryPicker() -> abre esta
 * Activity via Intent -> ela mesma resolve o picker -> entrega o
 * resultado de volta pro AndroidActivityBridge -> se auto-finaliza.
 *
 * ⚠️ REQUER registro no AndroidManifest.xml:
 *
 * <activity
 *     android:name="com.raylson.jansen.inspetor.platform.GalleryPickerActivity"
 *     android:theme="@style/Theme.Transparent"
 *     android:exported="false"
 *     android:excludeFromRecents="true"
 *     android:noHistory="true" />
 *
 * E em res/values/styles.xml (crie se não existir):
 *
 * <style name="Theme.Transparent" parent="Theme.AppCompat.Translucent.NoTitleBar">
 *     <item name="android:windowIsTranslucent">true</item>
 *     <item name="android:windowBackground">@android:color/transparent</item>
 *     <item name="android:windowNoTitle">true</item>
 *     <item name="android:windowAnimationStyle">@null</item>
 *     <item name="android:windowIsFloating">false</item>
 *     <item name="android:backgroundDimEnabled">false</item>
 * </style>
 * ═══════════════════════════════════════════════════════════════════
 */
class GalleryPickerActivity : ComponentActivity() {

    private lateinit var launcher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (uri == null) {
                AndroidActivityBridge.entregarResultadoGaleria(null)
                finalizar()
                return@registerForActivityResult
            }
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
            AndroidActivityBridge.entregarResultadoGaleria(bytes)
            finalizar()
        }

        abrirSeletorDeImagens()
    }

    /**
     * Port exato da lógica original do DashboardActivity: tenta o Photo
     * Picker moderno (ACTION_PICK_IMAGES) primeiro; se o aparelho não
     * suportar (ActivityNotFoundException), cai pro ACTION_PICK clássico.
     */
    private fun abrirSeletorDeImagens() {
        try {
            val intent = Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            launcher.launch(intent)
        }
    }

    private fun finalizar() {
        finish()
        overridePendingTransition(0, 0)
    }
}
