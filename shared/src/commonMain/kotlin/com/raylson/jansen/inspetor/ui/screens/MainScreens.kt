package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// -----------------------------------------------------------------
// ARQUIVO 22: Dashboard Principal[cite: 22]
// -----------------------------------------------------------------
@Composable
fun DashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Cabeçalho de Saudação
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(text = "Boa tarde,", fontSize = 22.sp, color = Color(0xFF111827), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Raylson", fontSize = 22.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Black)
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    // Ícone configurações
                }
            }
        }

        // Card Principal da Bomba
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "BOMBA CORRESPONDENTE", color = Color(0xFFBFDBFE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = "BOMBA-01", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF22C55E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        // Ícone Power
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 24: Histórico[cite: 24]
// -----------------------------------------------------------------
@Composable
fun HistoricoScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .systemBarsPadding()
    ) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = "HISTÓRICO", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(text = "Escolha o local para visualizar", color = Color(0xFFDBEAFE), fontSize = 13.sp)
            }
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 25: Controle N.A.[cite: 25]
// -----------------------------------------------------------------
@Composable
fun ControleNAScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .systemBarsPadding()
    ) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = "CONTROLE N.A.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(text = "ORDEM: INFIELD", color = Color(0xFFDBEAFE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
