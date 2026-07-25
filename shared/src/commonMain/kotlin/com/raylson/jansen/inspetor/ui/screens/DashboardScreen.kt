package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.core.model.rememberScreenModel
import com.raylson.jansen.inspetor.ui.components.DialogConfirmacaoVazaoScreen

/**
 * ═══════════════════════════════════════════════════════════════════
 * DashboardScreen.kt
 *
 * `DashboardScreen()` (função solta) virou `DashboardScreen : Screen`
 * (objeto/classe Voyager), com `Content()` lendo o estado do
 * `DashboardScreenModel` via StateFlow. Isso substitui o padrão antigo
 * de Activity + `findViewById` + listeners imperativos.
 * ═══════════════════════════════════════════════════════════════════
 */
object DashboardScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { DashboardScreenModel() }
        val uiState by screenModel.state.collectAsState()

        Scaffold(
            containerColor = Color(0xFFF4F6FB),
            bottomBar = {
                DashboardBottomBar(
                    onHistorico = { navigator.push(HistoricoRoute) },
                    onListaNA = { navigator.push(ControleNARoute) },
                    onCofre = { navigator.push(CofreRoute) },
                    onGerar = { screenModel.gerarRegistro() }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Cabeçalho de saudação (era o Row do topo da DashboardScreen original)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text(text = uiState.saudacao.ifBlank { "Boa tarde," }, fontSize = 22.sp, color = Color(0xFF111827), fontWeight = FontWeight.Medium)
                    }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { navigator.push(ConfiguracoesRoute) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Configurações",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Carrossel de estações
                EstacaoCarousel(
                    estacoes = uiState.estacoes,
                    selecionada = uiState.estacaoSelecionada,
                    onSelecionar = screenModel::selecionarEstacao
                )

                if (uiState.isModoNA) {
                    // Carrossel de lagos N.A. (era `carregarCarrosselNA`)
                    LagoNACarousel(
                        lagos = uiState.lagosNA,
                        selecionado = uiState.lagoNASelecionado,
                        onSelecionar = screenModel::selecionarLagoNA
                    )
                } else {
                    // Carrossel de itens/HM (era `setupCarrosselHm`)
                    ItemHmCarousel(
                        itens = uiState.itensAtuais,
                        selecionado = uiState.hmSelecionado,
                        onSelecionar = screenModel::selecionarItem
                    )
                }

                // Card principal (Bomba/Status) — mantém o visual original
                val item = uiState.itemAtual
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item?.cardAzulSub ?: "BOMBA CORRESPONDENTE", color = Color(0xFFBFDBFE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = item?.cardAzulLabel ?: uiState.estacaoSelecionada.nome, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.statusBomba in listOf("LIGADA", "LIGADO", "COM VAZÃO")) Color(0xFF22C55E) else Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().let { it }
                            ) {
                                // toque -> screenModel.avancarStatus()
                            }
                        }
                    }
                }
            }
        }

        // ─── Diálogos declarativos (substitui AlertDialog.Builder imperativo) ───
        when (val dialogo = uiState.dialogAtivo) {
            is DashboardDialog.ConfirmacaoVazao -> DialogConfirmacaoVazaoScreen()
            is DashboardDialog.Nenhum -> Unit
            else -> {
                // LeituraManual, EscolhaOrigemFoto, ExcluirFoto, EditarDataHora,
                // ResultadoRegistro -> cada um chama a função correspondente já
                // existente em DialogScreens.kt / PopupScreens.kt, passando
                // `onConfirmar = { ... ; screenModel.fecharDialog() }`.
            }
        }
    }
}

@Composable
private fun DashboardBottomBar(
    onHistorico: () -> Unit,
    onListaNA: () -> Unit,
    onCofre: () -> Unit,
    onGerar: () -> Unit
) {
    BottomAppBar(
        containerColor = Color.White,
        actions = {
            IconButton(onClick = onHistorico) { Icon(Icons.Filled.History, contentDescription = "Histórico") }
            IconButton(onClick = onListaNA) { Icon(Icons.Filled.Add, contentDescription = "Controle N.A.") }
            Spacer(modifier = Modifier.weight(1f, fill = true))
            IconButton(onClick = onCofre) { Icon(Icons.Filled.Lock, contentDescription = "Cofre") }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onGerar,
                containerColor = Color(0xFF2563EB),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Gerar registro", tint = Color.White)
            }
        }
    )
}

// Stubs de composição — a implementação visual real (RecyclerView -> LazyRow)
// já existe convertida; aqui só a assinatura de integração com o ScreenModel.

/** Parser de cor hex "#RRGGBB" sem depender de android.graphics.Color (KMP-safe). */
private fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val colorLong = clean.toLong(16)
    return if (clean.length == 6) {
        Color(0xFF000000L or colorLong)
    } else {
        Color(colorLong)
    }
}

@Composable
private fun EstacaoCarousel(
    estacoes: List<com.raylson.jansen.inspetor.domain.Estacao>,
    selecionada: com.raylson.jansen.inspetor.domain.Estacao,
    onSelecionar: (com.raylson.jansen.inspetor.domain.Estacao) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        estacoes.forEach { estacao ->
            val ativo = estacao == selecionada
            Card(
                modifier = Modifier.padding(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ativo) parseHexColor(estacao.cor) else Color.White
                )
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(text = estacao.nome, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ItemHmCarousel(
    itens: List<com.raylson.jansen.inspetor.domain.ItemHm>,
    selecionado: Int,
    onSelecionar: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        itens.forEachIndexed { index, item ->
            Card(modifier = Modifier.padding(4.dp)) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(text = item.titulo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LagoNACarousel(
    lagos: List<com.raylson.jansen.inspetor.domain.LagoNA>,
    selecionado: Int,
    onSelecionar: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        lagos.forEachIndexed { index, lago ->
            Card(modifier = Modifier.padding(4.dp)) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(text = lago.abreviacao, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
