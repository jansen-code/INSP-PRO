package com.raylson.jansen.inspetor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.ln

class CameraCaptureActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var previewContainer: View
    private lateinit var previewView: PreviewView
    private lateinit var gridOverlay: View
    private lateinit var btnCaptureRing: CardView
    private lateinit var btnCaptureInner: View
    private lateinit var btnClose: ImageView
    private lateinit var tvRatio: TextView
    private lateinit var btnGrid: TextView
    
    // ══ NOVO: Variável do Botão de Liga/Desliga da Guia ══
    private lateinit var btnToggleGuia: ImageView
    private var isGuiaVisible: Boolean = true 
    
    // Referências aos Guias
    private lateinit var guiaEnquadramento: GuiaEnquadramentoView
    private lateinit var zoomRuler: ZoomRulerView
    
    // Referências do Foco e Régua
    private lateinit var focoAnimadoView: FocoAnimadoView
    private lateinit var reguaVerticalView: ReguaVerticalView
    private var fatorNitidezAtual = 0f

    private var camera: Camera? = null
    private var pendingZoom: Float? = null

    private val zoomThrottleHandler = Handler(Looper.getMainLooper())
    private var pendingZoomRunnable: Runnable? = null
    private var lastZoomApplyTime = 0L
    private val ZOOM_THROTTLE_MS = 45L

    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService? = null
    private var orientationEventListener: OrientationEventListener? = null

    private var selectedRatio: String = ConfiguracoesActivity.PROP_4x5
    private var isGridVisible: Boolean = false
    private var aplicarCorte: Boolean = false 
    private var mostrarMiraBomba: Boolean = false 
    private var altaResolucao: Boolean = false

    private var ratiosList = listOf(
        ConfiguracoesActivity.PROP_4x5,
        ConfiguracoesActivity.PROP_3x4,
        ConfiguracoesActivity.PROP_1x1,
        ConfiguracoesActivity.PROP_9x16,
        ConfiguracoesActivity.PROP_FULL
    )


    private val UI_ZOOM_MIN = 0.6f
    private val UI_ZOOM_MAX = 100f

    companion object {
        const val EXTRA_RATIO = "extra_ratio"
        const val EXTRA_APLICAR_CORTE = "extra_aplicar_corte"
        const val EXTRA_IS_EXTRAVASOR = "extra_is_extravasor"
        // ═══ NOVO: quando true, pula o teto de 1920px aplicado normalmente
        // a toda foto capturada. Usado hoje só pelo Scanner de Documentos —
        // depois de recortar só a página de dentro da foto inteira, sobra
        // bem menos pixel pra um texto pequeno/manuscrito ficar legível.
        // As outras estações continuam exatamente como sempre foram. ═══
        const val EXTRA_ALTA_RESOLUCAO = "extra_alta_resolucao"
        const val RESULT_PHOTO_PATH = "photo_path"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_camera_capture)

            bindViews()
            setupUIElements()
            setupZoomRuler()
            
            setupFocusTouch()
            setupReguaControles()

            aplicarCorte = intent.getBooleanExtra(EXTRA_APLICAR_CORTE, false)
            altaResolucao = intent.getBooleanExtra(EXTRA_ALTA_RESOLUCAO, false)
            selectedRatio = intent.getStringExtra(EXTRA_RATIO) ?: ImageHelper.lerProporcao(this)
            mostrarMiraBomba = intent.getBooleanExtra("extra_mostrar_mira", false)

            // ══ LÓGICA DO BOTÃO DA GUIA (DET-01) ══
            isGuiaVisible = mostrarMiraBomba

            if (mostrarMiraBomba) {
                btnToggleGuia.alpha = 1.0f
            } else {
                btnToggleGuia.alpha = 0.3f
            }

            btnToggleGuia.setOnClickListener {
                if (mostrarMiraBomba) {
                    isGuiaVisible = !isGuiaVisible
                    guiaEnquadramento.visibility = if (isGuiaVisible && selectedRatio != ConfiguracoesActivity.PROP_FULL) View.VISIBLE else View.GONE
                    btnToggleGuia.alpha = if (isGuiaVisible) 1.0f else 0.5f
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                } else {
                    Toast.makeText(this@CameraCaptureActivity, "Válido somente para os painéis do DET-01", Toast.LENGTH_SHORT).show()
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    // Animação de erro (balançar)
                    it.animate().translationX(15f).setDuration(50).withEndAction {
                        it.animate().translationX(-15f).setDuration(50).withEndAction {
                            it.animate().translationX(0f).start()
                        }.start()
                    }.start()
                }
            }

            // ══ NOVA REGRA: Limita o botão de proporções se estiver no N.A. ══
            val isNA = intent.getBooleanExtra("extra_is_na", false)
            val isExtravasor = intent.getBooleanExtra(EXTRA_IS_EXTRAVASOR, false)
            
            ratiosList = if (isNA && !isExtravasor) {
                listOf(ConfiguracoesActivity.PROP_4x5, ConfiguracoesActivity.PROP_3x4)
            } else {
                listOf(
                    ConfiguracoesActivity.PROP_4x5, ConfiguracoesActivity.PROP_3x4,
                    ConfiguracoesActivity.PROP_1x1, ConfiguracoesActivity.PROP_9x16,
                    ConfiguracoesActivity.PROP_FULL
                )
            }
            
            if (selectedRatio !in ratiosList && !isExtravasor) {
                selectedRatio = ConfiguracoesActivity.PROP_4x5
            }

            applyRatioConstraint(animate = false)

            btnClose.setOnClickListener { finish() }

            btnGrid.setOnClickListener {
                isGridVisible = !isGridVisible
                gridOverlay.visibility = if (isGridVisible) View.VISIBLE else View.GONE
                btnGrid.alpha = if (isGridVisible) 1.0f else 0.5f
            }

            tvRatio.setOnClickListener { toggleRatio() }

            btnCaptureRing.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btnCaptureInner.animate()
                            .scaleX(0.88f).scaleY(0.88f)
                            .setDuration(80).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        btnCaptureInner.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(80).start()
                        if (event.action == MotionEvent.ACTION_UP) takePhotoSafe()
                    }
                }
                true
            }

            if (allPermissionsGranted()) {
                startCameraSafe()
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }

            cameraExecutor = Executors.newSingleThreadExecutor()

            if (isExtravasor) {
                orientationEventListener = object : OrientationEventListener(this) {
                    private var lastRotation: Int? = null
                    override fun onOrientationChanged(orientation: Int) {
                        if (orientation == ORIENTATION_UNKNOWN) return
                        val rotation = when (orientation) {
                            in 45..134 -> Surface.ROTATION_270
                            in 135..224 -> Surface.ROTATION_180
                            in 225..314 -> Surface.ROTATION_90
                            else -> Surface.ROTATION_0
                        }
                        if (rotation != lastRotation) {
                            lastRotation = rotation
                            imageCapture?.targetRotation = rotation
                        }
                    }
                }
                orientationEventListener?.enable()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Erro de inicialização: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    
    private fun setupReguaControles() {
        reguaVerticalView.onValorMudou = { modo, valor ->
            if (modo == ReguaVerticalView.Modo.BRILHO) {
                camera?.cameraInfo?.exposureState?.let { state ->
                    if (state.isExposureCompensationSupported) {
                        val range = state.exposureCompensationRange
                        val maxEv = if (valor > 0) range.upper.toFloat() else kotlin.math.abs(range.lower).toFloat()
                        val ev = (valor * maxEv * 2.5f).toInt()
                        camera?.cameraControl?.setExposureCompensationIndex(ev.coerceIn(range.lower, range.upper))
                    }
                }
            } else {
                fatorNitidezAtual = valor
            }
        }
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        previewContainer = findViewById(R.id.previewContainer)
        previewView = findViewById(R.id.previewView)
        gridOverlay = findViewById(R.id.gridOverlay)
        btnCaptureRing = findViewById(R.id.btnCaptureRing)
        btnCaptureInner = findViewById(R.id.btnCaptureInner)
        btnClose = findViewById(R.id.btnClose)
        tvRatio = findViewById(R.id.tvRatio)
        btnGrid = findViewById(R.id.btnGrid)
        btnToggleGuia = findViewById(R.id.btnToggleGuia) // ══ BIND DO NOVO BOTÃO ══
        zoomRuler = findViewById(R.id.zoomRuler)
        guiaEnquadramento = findViewById(R.id.guiaEnquadramento) 
        focoAnimadoView = findViewById(R.id.focoAnimadoView) 
        reguaVerticalView = findViewById(R.id.reguaVerticalView) 
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFocusTouch() {
        previewView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y

                focoAnimadoView.animarFoco(x, y)
                
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(x, y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                    
                camera?.cameraControl?.startFocusAndMetering(action)
                v.performClick()
            }
            true 
        }
    }

    private fun setupUIElements() {
        btnCaptureInner.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        tvRatio.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(Color.parseColor("#4D000000"))
            setStroke(2, Color.parseColor("#33FFFFFF"))
        }
        btnGrid.alpha = 0.5f
    }

    private fun setupZoomRuler() {
        zoomRuler.setZoom(1.0f, notify = false)
        zoomRuler.onZoomChanged = { uiZoom ->
            applyZoomToCamera(uiZoom)
        }
    }

    private fun applyZoomToCamera(uiZoom: Float) {
        val cam = camera
        if (cam == null) {
            pendingZoom = uiZoom
            return
        }

        pendingZoomRunnable?.let { zoomThrottleHandler.removeCallbacks(it) }

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastZoomApplyTime

        if (elapsed >= ZOOM_THROTTLE_MS) {
            lastZoomApplyTime = now
            safeSetZoomRatio(cam, uiZoom)
        } else {
            val runnable = Runnable {
                lastZoomApplyTime = SystemClock.elapsedRealtime()
                safeSetZoomRatio(cam, uiZoom)
            }
            pendingZoomRunnable = runnable
            zoomThrottleHandler.postDelayed(runnable, ZOOM_THROTTLE_MS - elapsed)
        }
    }

    private fun mapUiZoomToHardware(uiZoom: Float, minHardware: Float, maxHardware: Float): Float {
        val ui = uiZoom.coerceIn(UI_ZOOM_MIN, UI_ZOOM_MAX)
        val logUiMin = ln(UI_ZOOM_MIN.toDouble())
        val logUiMax = ln(UI_ZOOM_MAX.toDouble())
        val logUi = ln(ui.toDouble())
        val fraction = ((logUi - logUiMin) / (logUiMax - logUiMin)).coerceIn(0.0, 1.0)

        val hwMinF = minHardware.coerceAtLeast(0.1f)
        val hwMaxF = maxHardware.coerceAtLeast(hwMinF + 0.01f)
        val logHwMin = ln(hwMinF.toDouble())
        val logHwMax = ln(hwMaxF.toDouble())
        val logHw = logHwMin + fraction * (logHwMax - logHwMin)
        return exp(logHw).toFloat()
    }

    private fun safeSetZoomRatio(cam: Camera, uiZoom: Float) {
        try {
            val zoomState = cam.cameraInfo.zoomState.value
            val maxZoom = zoomState?.maxZoomRatio ?: 30f
            val minZoom = zoomState?.minZoomRatio ?: UI_ZOOM_MIN
            val hardwareZoom = mapUiZoomToHardware(uiZoom, minZoom, maxZoom).coerceIn(minZoom, maxZoom)
            cam.cameraControl.setZoomRatio(hardwareZoom)
        } catch (_: Exception) {
        }
    }

    private fun toggleRatio() {
        val currentIndex = ratiosList.indexOf(selectedRatio)
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % ratiosList.size
        selectedRatio = ratiosList[nextIndex]

        SecurePrefs.get(this, ConfiguracoesActivity.PREFS_NAME)
            .edit()
            .putString(ConfiguracoesActivity.PREF_PROPORCAO, selectedRatio)
            .apply()

        applyRatioConstraint(animate = true)
    }

    private fun applyRatioConstraint(animate: Boolean) {
        if (animate) {
            val transition = AutoTransition().apply { duration = 200 }
            TransitionManager.beginDelayedTransition(rootLayout, transition)
        }

        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)

        when (selectedRatio) {
            ConfiguracoesActivity.PROP_4x5 -> {
                constraintSet.setDimensionRatio(R.id.previewContainer, "H,4:5")
                tvRatio.text = "4:5"
            }
            ConfiguracoesActivity.PROP_1x1 -> {
                constraintSet.setDimensionRatio(R.id.previewContainer, "H,1:1")
                tvRatio.text = "1:1"
            }
            ConfiguracoesActivity.PROP_9x16 -> {
                constraintSet.setDimensionRatio(R.id.previewContainer, "H,9:16")
                tvRatio.text = "9:16"
            }
            ConfiguracoesActivity.PROP_3x4 -> {
                constraintSet.setDimensionRatio(R.id.previewContainer, "H,3:4")
                tvRatio.text = "3:4"
            }
            else -> {
                constraintSet.setDimensionRatio(R.id.previewContainer, null)
                tvRatio.text = "FULL"
            }
        }
        constraintSet.applyTo(rootLayout)

        // ══ RESPEITA SE A GUIA FOI DESLIGADA PELO BOTÃO ══
        if (mostrarMiraBomba && selectedRatio != ConfiguracoesActivity.PROP_FULL && isGuiaVisible) {
            guiaEnquadramento.visibility = View.VISIBLE
            guiaEnquadramento.setProporcao(selectedRatio)
        } else {
            guiaEnquadramento.visibility = View.GONE
        }
    }

    private fun startCameraSafe() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()

                    cameraProvider.unbindAll()

                    camera = cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )

                    val initialZoom = pendingZoom ?: zoomRuler.getCurrentZoom()
                    pendingZoom = null
                    camera?.let { safeSetZoomRatio(it, initialZoom) }

                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao iniciar câmera: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }, ContextCompat.getMainExecutor(this))

        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao preparar câmera", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun takePhotoSafe() {
        val ic = imageCapture ?: run {
            Toast.makeText(this, "Câmera não pronta", Toast.LENGTH_SHORT).show()
            return
        }

        val photoFile = File(cacheDir, "INSPETOR_RAW_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        ic.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val executor = cameraExecutor
                        ?: Executors.newSingleThreadExecutor().also { cameraExecutor = it }

                    executor.execute {
                        try {
                            val finalPath = processImageSafe(photoFile.absolutePath)
                            runOnUiThread {
                                setResult(RESULT_OK, Intent().apply {
                                    putExtra(RESULT_PHOTO_PATH, finalPath)
                                })
                                finish()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Erro no processamento: ${e.message}", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(applicationContext, "Falha na captura", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }

        private fun processImageSafe(originalPath: String): String {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalPath, bounds)

            val maxSide = if (altaResolucao) Int.MAX_VALUE else 1920
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }

            var bmp = BitmapFactory.decodeFile(originalPath, opts) ?: return originalPath

            bmp = fixExifRotation(originalPath, bmp)

            if (fatorNitidezAtual != 0f) {
                bmp = aplicarNitidezEContraste(bmp, fatorNitidezAtual)
            }

            if (selectedRatio != ConfiguracoesActivity.PROP_FULL) {
                bmp = ImageHelper.recortarPorProporcao(bmp, selectedRatio)
            }

            val qualidadeJpeg = if (altaResolucao) 97 else 85
            val finalFile = File(cacheDir, "INSPETOR_FINAL_${System.currentTimeMillis()}.jpg")
            FileOutputStream(finalFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, qualidadeJpeg, out)
            }

            bmp.recycle()
            File(originalPath).delete()

            finalFile.absolutePath

        } catch (e: Exception) {
            originalPath
        }
    }

    private fun aplicarNitidezEContraste(original: Bitmap, valor: Float): Bitmap {
        val escalaContraste = 1f + (valor * 0.6f) 
        val cm = android.graphics.ColorMatrix()
        val translate = (-0.5f * escalaContraste + 0.5f) * 255f
        
        cm.set(floatArrayOf(
            escalaContraste, 0f, 0f, 0f, translate,
            0f, escalaContraste, 0f, 0f, translate,
            0f, 0f, escalaContraste, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = android.graphics.Paint().apply { 
            colorFilter = android.graphics.ColorMatrixColorFilter(cm) 
        }
        
        val configSegura = original.config ?: Bitmap.Config.ARGB_8888
        val output = Bitmap.createBitmap(original.width, original.height, configSegura)
        
        val canvas = android.graphics.Canvas(output)
        
        canvas.drawBitmap(original, 0f, 0f, paint)
        if (original != output) original.recycle()
        return output
    }

    private fun fixExifRotation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val ori = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (ori) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            }
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) startCameraSafe() else finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationEventListener?.disable()
        pendingZoomRunnable?.let { zoomThrottleHandler.removeCallbacks(it) }
        cameraExecutor?.shutdown()
    }
}
