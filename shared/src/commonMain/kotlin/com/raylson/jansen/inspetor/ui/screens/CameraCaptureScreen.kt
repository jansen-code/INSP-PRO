package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
// ARQUIVO 32: Captura da Câmera[cite: 32]
// -----------------------------------------------------------------
@Composable
fun CameraCaptureScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) { //[cite: 32]
        
        // A UI FLUTUANTE DA CÂMERA[cite: 32]
        // TopBar Flutuante (Pill Shape)[cite: 32]
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), //[cite: 32]
            verticalAlignment = Alignment.CenterVertically //[cite: 32]
        ) {
            // Botão Fechar[cite: 32]
            Box(modifier = Modifier.size(44.dp).background(Color(0x40000000), CircleShape)) //[cite: 32]
            
            Spacer(modifier = Modifier.weight(1f)) //[cite: 32]
            
            // Grupo Central (Grid, Guia, Proporção)[cite: 32]
            Row(
                modifier = Modifier.background(Color(0x40000000), RoundedCornerShape(22.dp)).padding(horizontal = 8.dp, vertical = 4.dp), //[cite: 32]
                verticalAlignment = Alignment.CenterVertically //[cite: 32]
            ) {
                Box(modifier = Modifier.size(36.dp).background(Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { Text("▦", color = Color.White) } //[cite: 32]
                Box(modifier = Modifier.size(36.dp).padding(horizontal = 4.dp).background(Color.Transparent, CircleShape)) // Ícone Guia[cite: 32]
                Box(modifier = Modifier.height(30.dp).background(Color(0xFFF97316), RoundedCornerShape(15.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { //[cite: 32]
                    Text(text = "3:4", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) //[cite: 32]
                }
            }
            
            Spacer(modifier = Modifier.weight(1f)) //[cite: 32]
            Box(modifier = Modifier.size(44.dp)) // Espaçador simétrico[cite: 32]
        }

        // BottomBar (Regua de Zoom + Botão Captura)[cite: 32]
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 20.dp), //[cite: 32]
            horizontalAlignment = Alignment.CenterHorizontally //[cite: 32]
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(92.dp).padding(bottom = 16.dp)) // Placeholder Zoom Ruler[cite: 32]
            
            // Botão de Captura (Anel branco)[cite: 32]
            Box(
                modifier = Modifier.size(76.dp).background(Color.White, CircleShape), //[cite: 32]
                contentAlignment = Alignment.Center //[cite: 32]
            ) {
                // Miolo do botão (que faria a animação)[cite: 32]
                Box(modifier = Modifier.fillMaxSize().padding(4.dp).border(2.dp, Color.Black, CircleShape)) //[cite: 32]
            }
        }
    }
}
