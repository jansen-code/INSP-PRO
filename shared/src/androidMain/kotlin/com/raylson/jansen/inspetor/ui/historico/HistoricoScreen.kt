package com.raylson.jansen.inspetor.ui.historico

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator

object HistoricoScreen : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val screenModel = rememberScreenModel { HistoricoScreenModel(context.applicationContext) }
        val state by screenModel.state.collectAsState()

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    screenModel.recarregarGrade()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var rotationState by remember { mutableStateOf(0f) }
        val animatedRotation by animateFloatAsState(targetValue = rotationState, animationSpec = tween(420))
        var showLimparDialog by remember { mutableStateOf(false) }

        if (showLimparDialog) {
            AlertDialog(
                onDismissRequest = { showLimparDialog = false },
                title = { Text("Limpar Histórico", fontWeight = FontWeight.Bold) },
                text = { Text("Tem certeza que deseja apagar todos os registros e fotos? Esta ação não pode ser desfeita.") },
                confirmButton = {
                    TextButton(onClick = { 
                        screenModel.limparHistorico()
                        showLimparDialog = false
                        Toast.makeText(context, "Histórico limpo!", Toast.LENGTH_SHORT).show()
                    }) { Text("APAGAR", color = Color.Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showLimparDialog = false }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }

        Scaffold(
            containerColor = Color.White
        ) { paddingValues ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTÓRICO DE\nREGISTROS",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        lineHeight = 32.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                            .clickable {
                                rotationState += 360f
                                screenModel.animarRotacaoHistorico()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "?", color = Color.White, fontSize = 28.sp, modifier = Modifier.rotate(animatedRotation))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SELECIONE (MODO " + state.mode.name.replace("_", "-") + ")",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "LIMPAR TUDO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        modifier = Modifier.clickable { showLimparDialog = true }.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    state.itensDaGrade.forEach { item ->
                        when (item) {
                            is GradeComposeItem.Secao -> {
                                item(span = { GridItemSpan(4) }) {
                                    Text(
                                        text = item.titulo,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                            }
                            is GradeComposeItem.Card -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF1F5F9))
                                            .clickable {
                                                if (item.temRegistro) {
                                                    val intent = Intent().setClassName(context.packageName, "com.raylson.jansen.inspetor.HistoricoActivity")
                                                    intent.putExtra("EDIT_ITEM", item.nome)
                                                    context.startActivity(intent)
                                                } else {
                                                    Toast.makeText(context, "Sem registro para " + item.nome, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = item.nome,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                            if (item.temRegistro) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
