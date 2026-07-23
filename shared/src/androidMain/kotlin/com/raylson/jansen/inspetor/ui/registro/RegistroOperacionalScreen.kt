package com.raylson.jansen.inspetor.ui.registro

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.raylson.jansen.inspetor.ImageHelper

object RegistroOperacionalScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current

        val screenModel = rememberScreenModel { 
            RegistroOperacionalScreenModel(context.applicationContext) 
        }
        val state by screenModel.state.collectAsState()

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val photoPath = result.data?.getStringExtra("photo_path")
                if (!photoPath.isNullOrEmpty() && state.pedidoAbrirCameraIndex >= 0) {
                    val bmp = ImageHelper.carregarComExif(photoPath)
                    if (bmp != null) {
                        screenModel.vincularBitmapAoItem(state.pedidoAbrirCameraIndex, bmp)
                    }
                }
            }
        }

        // --- AQUI ESTÁ A CORREÇÃO DA PONTE DA CÂMERA ---
        LaunchedEffect(state.pedidoAbrirCameraIndex) {
            if (state.pedidoAbrirCameraIndex >= 0) {
                val prefs = context.getSharedPreferences("Configuracoes", Context.MODE_PRIVATE)
                val proporcaoSalva = prefs.getString("pref_proporcao", "4:5")

                val intent = Intent().setClassName(context.packageName, "com.raylson.jansen.inspetor.CameraCaptureActivity").apply {
                    putExtra("extra_ratio", proporcaoSalva)
                    // Informa a câmera que estamos no DET-01 para habilitar a guia da bomba
                    putExtra("extra_mostrar_mira", state.estacaoAtual == "DET-01")
                }
                cameraLauncher.launch(intent)
                screenModel.confirmarCameraAberta()
            }
        }

        Scaffold(
            topBar = { TopBarEstacoes(state, screenModel) },
            containerColor = Color(0xFFF4F6FB)
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { HeaderSection(state, screenModel) }
                itemsIndexed(state.itens) { index, item ->
                    ItemCameraCard(item = item, index = index, screenModel = screenModel)
                }
                item { FooterSection(state, screenModel) }
            }
        }

        if (state.resultadoImagem != null) {
            DialogResultado(state, screenModel)
        }
    }

    @Composable
    private fun TopBarEstacoes(state: RegistroOperacionalState, screenModel: RegistroOperacionalScreenModel) {
        val estacoes = listOf("DET-01", "ARB-05", "ARB-06", "ARB-07", "ARB-08", "ARB-09")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            estacoes.forEach { nome ->
                val selecionado = state.estacaoAtual == nome
                val corFundo = if (selecionado) android.graphics.Color.parseColor(screenModel.corEstacao(nome)) else android.graphics.Color.parseColor("#1F2937")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(corFundo))
                        .clickable { screenModel.carregarEstacao(nome) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = nome, color = if (selecionado) Color.White else Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    @Composable
    private fun HeaderSection(state: RegistroOperacionalState, screenModel: RegistroOperacionalScreenModel) {
        val config = screenModel.configAtual() ?: return
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(text = config.rotuloHeader, fontSize = 12.sp, color = Color.Gray)
            Text(text = config.tituloHeader, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            if (!config.usaStatusPorItem) {
                Button(
                    onClick = { screenModel.alternarStatusGlobal() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(screenModel.corStatus(state.statusBomba)))),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(text = state.statusBomba, fontWeight = FontWeight.Bold) }
            } else {
                Text(text = "REGISTROS / STATUS POR ITEM", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun ItemCameraCard(item: ItemRegistro, index: Int, screenModel: RegistroOperacionalScreenModel) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = if (item.concluido) Color(0xFF0D1F0D) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().clickable { screenModel.abrirOrigemFoto(index) }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(item.corIcone))))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (item.concluido) Color.White else Color.Black, modifier = Modifier.weight(1f))
                    if (item.statusDisponiveis.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(android.graphics.Color.parseColor(screenModel.corStatus(item.statusAtual))))
                                .clickable { screenModel.alternarStatusItem(index) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(text = item.statusAtual, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (item.concluido && item.bitmap != null) {
                    Image(
                        bitmap = item.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(text = item.dataHora, color = Color(0xFF22C55E), fontSize = 12.sp)
                        Text(text = "REFAZER", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { screenModel.abrirOrigemFoto(index) })
                    }
                } else {
                    Text(text = "Toque para fotografar / galeria", color = Color(0xFF5A6478), fontSize = 14.sp)
                }
            }
        }
    }

    @Composable
    private fun FooterSection(state: RegistroOperacionalState, screenModel: RegistroOperacionalScreenModel) {
        val algumConcluido = state.itens.any { it.concluido }
        Button(
            onClick = { screenModel.gerarImagem() },
            enabled = algumConcluido,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 16.dp)
        ) { Text(text = "GERAR REGISTRO OPERACIONAL", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }

    @Composable
    private fun DialogResultado(state: RegistroOperacionalState, screenModel: RegistroOperacionalScreenModel) {
        Dialog(onDismissRequest = { screenModel.fecharDialogResultado() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Image(bitmap = state.resultadoImagem!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { screenModel.salvarImagem(state.resultadoImagem!!); screenModel.fecharDialogResultado() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("BAIXAR") }
                        Button(onClick = { screenModel.compartilharImagem(state.resultadoImagem!!); screenModel.fecharDialogResultado() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("COMPARTILHAR") }
                    }
                    TextButton(onClick = { screenModel.fecharDialogResultado() }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { Text("FECHAR", color = Color.Gray) }
                }
            }
        }
    }
}
