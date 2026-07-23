package com.raylson.jansen.inspetor.ui.listana

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.raylson.jansen.inspetor.ui.screens.HistoricoRoute

object ListaNAScreen : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val screenModel = rememberScreenModel { ListaNAScreenModel(context.applicationContext) }
        val state by screenModel.state.collectAsState()

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    screenModel.alternarOrdem()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var fabRotation by remember { mutableStateOf(0f) }
        val animatedFabRotation by animateFloatAsState(targetValue = fabRotation, animationSpec = tween(600))

        val editItem = state.editandoItem
        if (editItem != null) {
            EditDialog(
                item = editItem,
                isModoFlwHidro = screenModel.isModoFlwHidro(),
                onDismiss = { screenModel.fecharEdicao() },
                onRestore = {
                    val result = screenModel.restaurarOriginal()
                    when (result) {
                        1 -> Toast.makeText(context, "Restaurado para PENDENTE", Toast.LENGTH_SHORT).show()
                        2 -> Toast.makeText(context, "Valores originais restaurados", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(context, "Nenhum dado para restaurar", Toast.LENGTH_SHORT).show()
                    }
                },
                onSave = { valor, data, hora ->
                    if (valor.isEmpty()) {
                        Toast.makeText(context, "Digite o valor antes de salvar.", Toast.LENGTH_SHORT).show()
                        return@EditDialog
                    }
                    val dataParcial = data.isNotEmpty() && data.length != 10
                    val horaParcial = hora.isNotEmpty() && hora.length != 5
                    if (dataParcial || horaParcial) {
                        Toast.makeText(context, "Preencha DATA (DD.MM.AAAA) e HORA (HH:mm) completas, ou deixe ambas vazias.", Toast.LENGTH_SHORT).show()
                        return@EditDialog
                    }
                    if (screenModel.salvarEdicao(valor, data, hora)) {
                        Toast.makeText(context, "Salvo com sucesso", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Scaffold(
            containerColor = Color(0xFFF8FAFC)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                HeaderCard(
                    titulo = state.tituloControle,
                    subtitulo = state.subtituloModo,
                    textoToggle = state.textoToggle,
                    onToggle = { screenModel.alternarTipoControle() },
                    onBack = { navigator?.popUntilRoot() }
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                ) {
                    items(state.itens.size) { index ->
                        val item = state.itens[index]
                        NAItemCard(
                            item = item,
                            onDoubleTap = { screenModel.abrirEdicao(index) }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.BottomCenter
            ) {
                BottomBar(
                    onHistorico = {
                        navigator?.push(HistoricoRoute)
                    },
                    onFabClick = {
                        fabRotation += 360f
                        screenModel.alternarOrdem()
                    },
                    fabRotation = animatedFabRotation
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(
    titulo: String,
    subtitulo: String,
    textoToggle: String,
    onToggle: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-20).dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            )

            Column(modifier = Modifier.padding(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "<", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = titulo,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = subtitulo,
                    color = Color(0xFFDBEAFE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.clickable { onToggle() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "\u21BB", color = Color(0xFF2563EB), fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = textoToggle,
                            color = Color(0xFF2563EB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NAItemCard(
    item: NARegistro,
    onDoubleTap: () -> Unit
) {
    var lastTapTime by remember { mutableStateOf(0L) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 400) {
                            lastTapTime = 0L
                            onDoubleTap()
                        } else {
                            lastTapTime = now
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.tituloVisual,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (!item.foiRegistrado) {
                Text(
                    text = "PENDENTE",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = item.valor ?: "SEM LEITURA",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = if (item.valor == null) Color(0xFFEF4444) else Color(0xFF2563EB),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (item.foiRegistrado) item.dataHora ?: "--:--" else "--:--",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun EditDialog(
    item: NARegistro,
    isModoFlwHidro: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onSave: (valor: String, data: String, hora: String) -> Unit
) {
    val isHidrometro = isModoFlwHidro && item.tituloVisual.startsWith("HM")

    var valor by remember { mutableStateOf(item.valor ?: "") }
    var data by remember {
        mutableStateOf(
            if (!item.dataHora.isNullOrEmpty()) {
                screenModel_separarDataHora(item.dataHora).first
            } else {
                java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            }
        )
    }
    var hora by remember {
        mutableStateOf(
            if (!item.dataHora.isNullOrEmpty()) {
                screenModel_separarDataHora(item.dataHora).second
            } else {
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            }
        )
    }

    val labelValor = when {
        isModoFlwHidro -> if (isHidrometro) "LEITURA DO HIDR\u00D4METRO" else "VAZ\u00C3O (m\u00B3/hr)"
        else -> "VALOR N.A."
    }

    val hintValor = when {
        isModoFlwHidro -> if (isHidrometro) "+ 000.00 x1m\u00B3/h" else "000.0 m\u00B3/hr"
        else -> "00.00m"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EDI\u00C7\u00C3O MANUAL",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp
                        )
                        Text(
                            text = item.tituloVisual,
                            color = Color(0xFF1E293B),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF))
                            .clickable { onRestore() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "\u21BA", color = Color(0xFF2563EB), fontSize = 18.sp)
                    }
                }

                Text(
                    text = "Voc\u00EA poder\u00E1 restaurar para PENDENTE",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                Text(
                    text = labelValor,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                ) {
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { newValue ->
                            if (isModoFlwHidro) {
                                valor = newValue.filter { it.isDigit() || it == '.' }
                            } else {
                                val digits = newValue.filter { it.isDigit() }.take(4)
                                valor = when {
                                    digits.length >= 4 -> "${digits[0]}${digits[1]}.${digits[2]}${digits[3]}m"
                                    digits.length == 3 -> "${digits[0]}${digits[1]}.${digits[2]}"
                                    digits.length == 2 -> "${digits[0]}${digits[1]}."
                                    else -> digits
                                }
                            }
                        },
                        placeholder = { Text(hintValor, color = Color(0xFF94A3B8)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "DATA",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = data,
                    onValueChange = { newValue ->
                        val digits = newValue.filter { it.isDigit() }.take(8)
                        val sb = StringBuilder()
                        for (i in digits.indices) {
                            sb.append(digits[i])
                            if ((i == 1 || i == 3) && i != digits.lastIndex) sb.append('.')
                        }
                        data = sb.toString()
                    },
                    placeholder = { Text("DD.MM.AAAA", color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF2563EB)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Text(
                    text = "HORA",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = hora,
                    onValueChange = { newValue ->
                        val digits = newValue.filter { it.isDigit() }.take(4)
                        val sb = StringBuilder()
                        for (i in digits.indices) {
                            sb.append(digits[i])
                            if (i == 1 && i != digits.lastIndex) sb.append(':')
                        }
                        hora = sb.toString()
                    },
                    placeholder = { Text("HH:mm", color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF2563EB)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.padding(top = 16.dp, bottom = 22.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "CANCELAR",
                                color = Color(0xFF2563EB),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    ) {
                        TextButton(
                            onClick = { onSave(valor, data, hora) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SALVAR",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    onHistorico: () -> Unit,
    onFabClick: () -> Unit,
    fabRotation: Float
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onHistorico() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u23F0", fontSize = 22.sp, color = Color(0xFF94A3B8))
                }
            }
        }

        FloatingActionButton(
            onClick = onFabClick,
            containerColor = Color(0xFF2563EB),
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-34).dp)
        ) {
            Text(
                text = "\u270E",
                fontSize = 22.sp,
                modifier = Modifier.rotate(fabRotation)
            )
        }
    }
}

private fun screenModel_separarDataHora(texto: String?): Pair<String, String> {
    if (texto.isNullOrBlank()) return "" to ""
    val partes = texto.split("//").map { it.trim() }
    val data = partes.getOrNull(0) ?: ""
    val hora = (partes.getOrNull(1) ?: "").removeSuffix("h").trim()
    return data to hora
}
