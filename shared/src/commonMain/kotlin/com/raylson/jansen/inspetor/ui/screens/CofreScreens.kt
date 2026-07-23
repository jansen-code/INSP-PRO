package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// -----------------------------------------------------------------
// ARQUIVO 33: Tela Principal do Cofre[cite: 33]
// -----------------------------------------------------------------
@Composable
fun CofreScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).systemBarsPadding()) { //[cite: 33]
        Column(modifier = Modifier.fillMaxSize()) { //[cite: 33]
            // Header Azul Arredondado[cite: 33]
            Card(
                shape = RoundedCornerShape(30.dp), //[cite: 33]
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)), //[cite: 33]
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp), //[cite: 33]
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp) //[cite: 33]
            ) {
                Column(modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 22.dp)) { //[cite: 33]
                    Box(modifier = Modifier.size(46.dp).background(Color.Transparent)) // Placeholder Botão Voltar[cite: 33]
                    Text(text = "GALERIA 2", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 34.dp)) //[cite: 33]
                    Text(text = "Fotos limpas organizadas por estação", color = Color(0xFFDBEAFE), fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) //[cite: 33]
                }
            }

            // Seletores de Grupo (DET-01, N.A., ARB'S)[cite: 33]
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) { //[cite: 33]
                Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF16A34A)), modifier = Modifier.weight(1f).height(50.dp).padding(end = 4.dp)) { //[cite: 33]
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { //[cite: 33]
                        Text(text = "DET-01", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) //[cite: 33]
                        Text(text = "0 fotos", color = Color(0xFFE6FFFFFF), fontSize = 10.sp) //[cite: 33]
                    }
                }
                Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), modifier = Modifier.weight(1f).height(50.dp).padding(horizontal = 4.dp)) { //[cite: 33]
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { //[cite: 33]
                        Text(text = "N.A.", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) //[cite: 33]
                        Text(text = "0 fotos", color = Color(0xFFE6FFFFFF), fontSize = 10.sp) //[cite: 33]
                    }
                }
                Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF97316)), modifier = Modifier.weight(1f).height(50.dp).padding(start = 4.dp)) { //[cite: 33]
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { //[cite: 33]
                        Text(text = "ARB'S", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) //[cite: 33]
                        Text(text = "0 fotos", color = Color(0xFFE6FFFFFF), fontSize = 10.sp) //[cite: 33]
                    }
                }
            }
            
            // Área da Lista / Estado Vazio[cite: 33]
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) { //[cite: 33]
                 Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { //[cite: 33]
                     // Ícone Pasta Vazia Placeholder[cite: 33]
                     Text(text = "Nenhuma foto aqui ainda", color = Color(0xFF94A3B8), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) //[cite: 33]
                 }
            }
            
            // Bottom AppBar do Cofre[cite: 33]
            Row(modifier = Modifier.fillMaxWidth().height(68.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) { //[cite: 33]
                // Ícones de Ação[cite: 33]
            }
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 34: Visualizador do Cofre (Tela Cheia)[cite: 34]
// -----------------------------------------------------------------
@Composable
fun CofreVisualizadorScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { //[cite: 34]
        // 1. Carrossel de Fotos (Fundo)[cite: 34]
        // Substituído no KMP por um HorizontalPager no futuro[cite: 34]
        
        // 2. Barra Superior Translúcida[cite: 34]
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 14.dp), //[cite: 34]
            verticalAlignment = Alignment.CenterVertically //[cite: 34]
        ) {
            Card(shape = CircleShape, colors = CardDefaults.cardColors(containerColor = Color(0x40000000)), modifier = Modifier.size(42.dp)) { } // Fechar[cite: 34]
            Spacer(modifier = Modifier.weight(1f)) //[cite: 34]
            Card(shape = CircleShape, colors = CardDefaults.cardColors(containerColor = Color(0x40000000)), modifier = Modifier.size(42.dp).padding(end = 10.dp)) { } // Compartilhar[cite: 34]
            Card(shape = CircleShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)), modifier = Modifier.size(42.dp)) { } // Excluir[cite: 34]
        }
        
        // 3. Rodapé[cite: 34]
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x66000000))) { //[cite: 34]
            Text(
                text = "1. N.A - 05.07.2026 14:30h", //[cite: 34]
                color = Color.White, //[cite: 34]
                fontSize = 13.sp, //[cite: 34]
                fontWeight = FontWeight.Bold, //[cite: 34]
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp) //[cite: 34]
            )
        }
    }
}
