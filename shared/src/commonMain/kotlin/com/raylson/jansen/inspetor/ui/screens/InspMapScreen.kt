package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.core.model.rememberScreenModel

object InspMapScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { InspMapScreenModel() }
        val uiState by screenModel.state.collectAsState()

        var dropdownUtmExpandido by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {

            MapaCanvasPlaceholder(modifier = Modifier.fillMaxSize())

            Column(modifier = Modifier.fillMaxSize()) {

                InspMapCabecalho()

                Spacer(modifier = Modifier.weight(1f))

                InspMapBarraInferior(
                    onVoltar = { navigator.pop() },
                    onAbrirProjetos = { },
                    onNovoProjeto = { screenModel.abrirDialogNovoProjeto() }
                )
            }

            ControlesLaterais(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, top = 120.dp)
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
                dropdownExpandido = dropdownUtmExpandido,
                onDropdownToggle = { dropdownUtmExpandido = it },
                onSelecionarUtm = { zona ->
                    screenModel.selecionarZonaUtm(zona)
                    dropdownUtmExpandido = false
                },
                onCancelar = {
                    screenModel.fecharDialogNovoProjeto()
                    dropdownUtmExpandido = false
                },
                onCriar = {
                    screenModel.salvarProjeto()
                    dropdownUtmExpandido = false
                }
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
private fun ControlesLaterais(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .size(48.dp)
                .clickable { }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = "Câmera",
                    tint = Color(0xFF334155),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .size(48.dp)
                .clickable { }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "Orientação",
                    tint = Color(0xFF334155),
                    modifier = Modifier.size(22.dp)
                )
            }
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
                        Icons.Filled.CreateNewFolder,
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
                    Icons.Filled.Add,
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
                        Icons.Filled.ArrowBack,
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
    dropdownExpandido: Boolean,
    onDropdownToggle: (Boolean) -> Unit,
    onSelecionarUtm: (String) -> Unit,
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
                            .clickable { onDropdownToggle(!dropdownExpandido) },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color(0xFF0F172A),
                            disabledBorderColor = Color(0xFFCBD5E1),
                            disabledLabelColor = Color(0xFF64748B)
                        )
                    )

                    DropdownMenu(
                        expanded = dropdownExpandido,
                        onDismissRequest = { onDropdownToggle(false) }
                    ) {
                        listOf("UTM-23S", "UTM-22S", "UTM-24S", "UTM-23N").forEach { zona ->
                            DropdownMenuItem(
                                text = { Text(zona, fontWeight = if (zona == zonaUtm) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { onSelecionarUtm(zona) }
                            )
                        }
                    }
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

@Composable
private fun MapaCanvasPlaceholder(modifier: Modifier = Modifier) {
    val fundo = Color(0xFFF1F3F4)
    val verdeParque = Color(0xFFDCEBCC)
    val azulAgua = Color(0xFFBFE0EC)
    val brancoRua = Color(0xFFFFFFFF)
    val cinzaRuaFina = Color(0xFFD0D5DD)
    val cinzaRuaExtra = Color(0xFFE8EAED)

    Canvas(modifier = modifier.background(fundo)) {
        val w = size.width
        val h = size.height

        drawRect(
            color = cinzaRuaExtra,
            topLeft = Offset(0f, 0f),
            size = Size(w, h)
        )

        drawLine(
            color = brancoRua,
            start = Offset(0f, h * 0.25f),
            end = Offset(w, h * 0.25f),
            strokeWidth = 28f
        )
        drawLine(
            color = brancoRua,
            start = Offset(0f, h * 0.65f),
            end = Offset(w, h * 0.65f),
            strokeWidth = 20f
        )
        drawLine(
            color = cinzaRuaFina,
            start = Offset(0f, h * 0.45f),
            end = Offset(w, h * 0.45f),
            strokeWidth = 10f
        )

        drawLine(
            color = brancoRua,
            start = Offset(w * 0.2f, 0f),
            end = Offset(w * 0.2f, h),
            strokeWidth = 24f
        )
        drawLine(
            color = brancoRua,
            start = Offset(w * 0.7f, 0f),
            end = Offset(w * 0.7f, h),
            strokeWidth = 20f
        )
        drawLine(
            color = cinzaRuaFina,
            start = Offset(w * 0.45f, 0f),
            end = Offset(w * 0.45f, h),
            strokeWidth = 10f
        )
        drawLine(
            color = cinzaRuaFina,
            start = Offset(w * 0.88f, 0f),
            end = Offset(w * 0.88f, h),
            strokeWidth = 8f
        )

        drawLine(
            color = brancoRua,
            start = Offset(w * 0.35f, h * 0.1f),
            end = Offset(w * 0.55f, h * 0.35f),
            strokeWidth = 14f
        )

        val pathParque1 = Path().apply {
            moveTo(w * 0.25f, h * 0.3f)
            lineTo(w * 0.42f, h * 0.28f)
            lineTo(w * 0.44f, h * 0.42f)
            lineTo(w * 0.27f, h * 0.44f)
            close()
        }
        drawPath(pathParque1, verdeParque)

        val pathParque2 = Path().apply {
            moveTo(w * 0.72f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.06f)
            lineTo(w * 0.88f, h * 0.2f)
            lineTo(w * 0.74f, h * 0.22f)
            close()
        }
        drawPath(pathParque2, verdeParque)

        val pathParque3 = Path().apply {
            moveTo(w * 0.05f, h * 0.7f)
            lineTo(w * 0.18f, h * 0.68f)
            lineTo(w * 0.2f, h * 0.85f)
            lineTo(w * 0.07f, h * 0.87f)
            close()
        }
        drawPath(pathParque3, verdeParque)

        val pathAgua1 = Path().apply {
            moveTo(w * 0.55f, h * 0.5f)
            cubicTo(w * 0.6f, h * 0.48f, w * 0.72f, h * 0.52f, w * 0.68f, h * 0.6f)
            cubicTo(w * 0.64f, h * 0.68f, w * 0.52f, h * 0.64f, w * 0.55f, h * 0.5f)
        }
        drawPath(pathAgua1, azulAgua)

        val pathAgua2 = Path().apply {
            moveTo(w * 0.75f, h * 0.7f)
            cubicTo(w * 0.78f, h * 0.66f, w * 0.92f, h * 0.68f, w * 0.9f, h * 0.78f)
            cubicTo(w * 0.88f, h * 0.88f, w * 0.73f, h * 0.82f, w * 0.75f, h * 0.7f)
        }
        drawPath(pathAgua2, azulAgua)

        drawRect(
            color = Color.White.copy(alpha = 0.7f),
            topLeft = Offset(w * 0.05f, h * 0.08f),
            size = Size(w * 0.12f, h * 0.14f)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.5f),
            topLeft = Offset(w * 0.08f, h * 0.3f),
            size = Size(w * 0.08f, h * 0.1f)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.6f),
            topLeft = Offset(w * 0.78f, h * 0.35f),
            size = Size(w * 0.1f, h * 0.12f)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.4f),
            topLeft = Offset(w * 0.3f, h * 0.72f),
            size = Size(w * 0.14f, h * 0.08f)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = Offset(w * 0.6f, h * 0.15f),
            size = Size(w * 0.06f, h * 0.08f)
        )

        drawLine(
            color = cinzaRuaFina,
            start = Offset(w * 0.2f, h * 0.25f),
            end = Offset(w * 0.7f, h * 0.25f),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
        drawLine(
            color = cinzaRuaFina,
            start = Offset(w * 0.2f, h * 0.65f),
            end = Offset(w * 0.7f, h * 0.65f),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }
}
