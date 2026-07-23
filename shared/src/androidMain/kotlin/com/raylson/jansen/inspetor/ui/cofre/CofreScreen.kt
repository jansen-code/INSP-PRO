package com.raylson.jansen.inspetor.ui.cofre

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.raylson.jansen.inspetor.CofreManager

data class CofreVoyagerScreen(
    val onBack: () -> Unit
) : Screen {

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { CofreScreenModel(context.applicationContext) }
        val state by screenModel.state.collectAsState()
        val scrollState = rememberLazyGridState()

        val visualizadorLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            screenModel.recarregar()
        }

        LaunchedEffect(Unit) {
            CofreManager.garantirPastaCofre(context)
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.titulo,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.subtitulo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        if (state.emModoMultiSelecao) {
                            IconButton(onClick = { screenModel.selecionarTudo() }) {
                                Icon(
                                    Icons.Filled.Checklist,
                                    contentDescription = "Selecionar tudo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { screenModel.limparSelecao() }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Limpar seleção",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(
                                onClick = {
                                    screenModel.criarIntentCompartilhamento()?.let { intent ->
                                        visualizadorLauncher.launch(intent)
                                    }
                                },
                                enabled = screenModel.podeCompartilhar()
                            ) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = "Compartilhar",
                                    tint = if (screenModel.podeCompartilhar()) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val stateAtual = state
                                    if (stateAtual.caminhosSelecionados.size == stateAtual.itens.size) {
                                        CofreManager.excluirGrupo(context, stateAtual.grupoAtual)
                                        screenModel.limparSelecao()
                                        screenModel.recarregar()
                                    } else {
                                        screenModel.excluirSelecionados()
                                    }
                                },
                                enabled = screenModel.podeExcluir()
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Excluir",
                                    tint = if (screenModel.podeExcluir()) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (state.emModoMultiSelecao) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val botaoModo = RoundedCornerShape(12.dp)
                            FilledTonalButton(
                                onClick = {
                                    screenModel.criarIntentCompartilhamento()?.let { intent ->
                                        visualizadorLauncher.launch(intent)
                                    }
                                },
                                enabled = screenModel.podeCompartilhar(),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                shape = botaoModo
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartilhar", fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val stateAtual = state
                                    if (stateAtual.caminhosSelecionados.size == stateAtual.itens.size) {
                                        CofreManager.excluirGrupo(context, stateAtual.grupoAtual)
                                        screenModel.limparSelecao()
                                        screenModel.recarregar()
                                    } else {
                                        screenModel.excluirSelecionados()
                                    }
                                },
                                enabled = screenModel.podeExcluir(),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                shape = botaoModo,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Excluir", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CofreManager.Grupo.values().forEach { grupo ->
                            val isSelected = grupo == state.grupoAtual
                            val contagem = state.contagens[grupo] ?: 0
                            FilterChip(
                                selected = isSelected,
                                onClick = { screenModel.selecionarGrupo(grupo) },
                                label = {
                                    Text(
                                        text = "${grupo.rotulo.uppercase()} ($contagem)",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }

                if (state.emModoMultiSelecao) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Modo seleção: ${state.caminhosSelecionados.size} de ${state.itens.size} fotos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                val cellSize = 120.dp

                LazyVerticalGrid(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    columns = GridCells.Adaptive(cellSize),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(
                        items = state.itensMix,
                        key = { item ->
                            when (item) {
                                is String -> "header_$item"
                                is CofreManager.ItemCofre -> "photo_${item.arquivo.absolutePath}"
                                else -> "unknown_${item.hashCode()}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is String -> {
                                Text(
                                    text = if (item.contains("ARQUIVO ANTIGO")) item.uppercase() else item,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.contains("ARQUIVO ANTIGO")) Color.Red
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is CofreManager.ItemCofre -> {
                                PhotoThumbnailItem(
                                    item = item,
                                    isSelected = state.caminhosSelecionados.contains(item.arquivo.absolutePath),
                                    emModoMultiSelecao = state.emModoMultiSelecao,
                                    onToggleSelection = { screenModel.toggleSelecao(item) },
                                    onOpenViewer = {
                                        val intent = Intent(context, Class.forName("com.raylson.jansen.inspetor.CofreVisualizadorActivity")).apply {
                                            putExtra("grupo", item.grupo.name)
                                            putStringArrayListExtra("caminhos", screenModel.listarCaminhosParaVisualizador())
                                            putExtra("posicao_inicial", screenModel.obterPosicaoInicial(item))
                                            putExtra("modo_selecao", false)
                                        }
                                        visualizadorLauncher.launch(intent)
                                    },
                                    cellSize = cellSize
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoThumbnailItem(
    item: CofreManager.ItemCofre,
    isSelected: Boolean,
    emModoMultiSelecao: Boolean,
    onToggleSelection: () -> Unit,
    onOpenViewer: () -> Unit,
    cellSize: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.arquivo.absolutePath) {
        val bmp = CofreManager.carregarMiniatura(context, item.arquivo)
        bitmap = bmp
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(cellSize)
            .then(
                if (isSelected) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (emModoMultiSelecao) onToggleSelection() else onOpenViewer()
                },
                onLongClick = { onToggleSelection() }
            ),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
