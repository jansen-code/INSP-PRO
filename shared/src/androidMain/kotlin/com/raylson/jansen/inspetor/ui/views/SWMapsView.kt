package com.raylson.jansen.inspetor.ui.views

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cafe.adriel.voyager.core.model.ScreenModel
import com.raylson.jansen.inspetor.ui.screens.InspMapScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

class SWMapsView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val mapView: MapView
    private val mapController: org.osmdroid.api.IMapController
    private val compassOverlay: CompassOverlay
    private val rotationGestureOverlay: RotationGestureOverlay
    private val currentMarker: Marker
    private var savedMarkers: MutableList<Marker> = mutableListOf()

    init {
        Configuration.getInstance().apply {
            setUserAgentValue("com.raylson.jansen.inspetor")
            setMapCoordinatesZoomLevelFixEnabled(true)
            setScaleOverlaysEnabled(true)
            setAnchorUseDrawableOnly(true)
        }

        mapView = MapView(context).also { view ->
            Configuration.getInstance().apply {
                setUserAgentValue("com.raylson.jansen.inspetor")
            }
        }

        mapController = mapView.mapController

        compassOverlay = CompassOverlay(context, mapView).also { mapView.overlays.add(it) }
        rotationGestureOverlay = RotationGestureOverlay(context, mapView).also { mapView.overlays.add(it) }

        currentMarker = Marker(mapView).apply {
            position = GeoPoint(0.0, 0.0)
            title = "Posição Atual RTK"
            setMarkerOptions { setIconResourceId(android.R.drawable.ic_menu_mylocation) }
            isDraggable = false
        }
        mapView.overlays.add(currentMarker)

        mapView.setMultiTouchControls(true)
    }

    fun setContent(
        screenModel: InspMapScreenModel,
        onFotoCapturada: (String) -> Unit,
        onToggleBt: () -> Unit,
        onSalvarPonto: () -> Unit
    ) {
        val composeView: ComposeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeEnd)
            setContent {
                MapScreenComposable(
                    screenModel = screenModel,
                    onFotoCapturada = onFotoCapturada,
                    onToggleBt = onToggleBt,
                    onSalvarPonto = onSalvarPonto
                )
            }
        }

        addView(mapView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeAllViews()
        mapView.onDestroy()
    }

    fun onResume() {
        mapView.onResume()
    }

    fun onPause() {
        mapView.onPause()
    }

    companion object {
        @Composable
        fun MapScreenComposable(
            screenModel: InspMapScreenModel,
            onFotoCapturada: (String) -> Unit,
            onToggleBt: () -> Unit,
            onSalvarPonto: () -> Unit
        ) {
            val uiState by screenModel.state.collectAsState()

            LaunchedEffect(uiState.latitudeAtual, uiState.longitudeAtual, uiState.btConectado) {
                kotlinx.coroutines.delay(100)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                CardStatusTop(
                    btConectado = uiState.btConectado,
                    precisaoMetros = uiState.precisaoMetros,
                    latitude = uiState.latitudeAtual,
                    longitude = uiState.longitudeAtual,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                )

                ControlesLaterais(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp, top = 120.dp),
                    onToggleBt = onToggleBt,
                    onSalvarPonto = onSalvarPonto
                )

                if (uiState.btConectado) {
                    BadgeRtk(
                        precisao = uiState.precisaoMetros,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 14.dp, top = 80.dp)
                    )
                }
            }

            if (uiState.dialogNovoProjetoAberto) {
                DialogNovoProjeto(
                    nomeProjeto = uiState.nomeProjetoTemp,
                    onNomeProjetoChange = screenModel::atualizarNomeProjeto,
                    dataHora = uiState.dataHoraTemp,
                    onDataHoraChange = screenModel::atualizarDataHora,
                    zonaUtm = uiState.zonaUtmSelecionada,
                    onCancelar = screenModel::fecharDialogNovoProjeto,
                    onCriar = screenModel::salvarProjeto
                )
            }
        }

        @Composable
        private fun CardStatusTop(
            btConectado: Boolean,
            precisaoMetros: Double,
            latitude: Double,
            longitude: Double,
            modifier: Modifier = Modifier
        ) {
            val isFixed = precisaoMetros < 0.05
            val statusColor = if (isFixed) Color(0xFF22C55E) else Color(0xFFF59E0B)
            val statusText = if (btConectado) "RTK CONECTADO" else "RTK DESCONECTADO"

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = modifier
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = if (isFixed) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Text(
                            text = "Precisão: %.2fm".format(precisaoMetros),
                            color = Color(0xFF475569),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Lat:",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                            Text(
                                text = "%.6f".format(latitude),
                                color = Color(0xFF0F172A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Lon:",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                            Text(
                                text = "%.6f".format(longitude),
                                color = Color(0xFF0F172A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        @Composable
        private fun ControlesLaterais(
            modifier: Modifier = Modifier,
            onToggleBt: () -> Unit,
            onSalvarPonto: () -> Unit
        ) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingActionButton(
                    onClick = onToggleBt,
                    containerColor = Color(0xFF2563EB),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Orientação",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                FloatingActionButton(
                    onClick = onSalvarPonto,
                    containerColor = Color(0xFF10B981),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = "Novo Projeto",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        @Composable
        private fun BadgeRtk(precisao: Double, modifier: Modifier = Modifier) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22C55E).copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = modifier
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                    )
                    Text(
                        text = "RTK %.2fm".format(precisao),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        @Composable
        private fun InspMapCabecalho() {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        text = "INSPMAP",
                        color = Color(0xFF0F172A),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "INSPEÇÃO GEOTÉCNICA",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp
                    )
                }
            }
        }

        @Composable
        private fun InspMapBarraInferior(
            onVoltar: () -> Unit,
            onAbrirProjetos: () -> Unit,
            onNovoProjeto: () -> Unit
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onAbrirProjetos() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.CreateNewFolder,
                                contentDescription = "Abrir Projetos",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = onNovoProjeto,
                        containerColor = Color(0xFF2563EB),
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = "Novo Projeto",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onVoltar() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        private fun DialogNovoProjeto(
            nomeProjeto: String,
            onNomeProjetoChange: (String) -> Unit,
            dataHora: String,
            onDataHoraChange: (String) -> Unit,
            zonaUtm: String,
            onCancelar: () -> Unit,
            onCriar: () -> Unit
        ) {
            AlertDialog(
                onDismissRequest = onCancelar,
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White,
                title = {
                    Text(
                        text = "Novo Projeto",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color(0xFF0F172A)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = nomeProjeto,
                            onValueChange = onNomeProjetoChange,
                            label = { Text("Nome do Projeto") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = dataHora,
                            onValueChange = onDataHoraChange,
                            label = { Text("Data e Hora") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box {
                            OutlinedTextField(
                                value = zonaUtm,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Sistema de Coordenadas") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color(0xFF0F172A),
                                    disabledBorderColor = Color(0xFFCBD5E1),
                                    disabledLabelColor = Color(0xFF64748B)
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onCriar,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        enabled = nomeProjeto.isNotBlank()
                    ) {
                        Text("Criar Projeto", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onCancelar,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Medium)
                    }
                }
            )
        }

        private fun criarArquivoFoto(context: Context): File? {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.DESCRIPTION, "Foto capturada pela câmera InspMap")
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

                val file = File(uri.path)
                if (!file.parentFile?.exists() == true) {
                    file.parentFile?.mkdirs()
                }
                return file
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}