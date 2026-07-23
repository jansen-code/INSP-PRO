package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
// ARQUIVO 35: Tela de Configurações[cite: 35]
// -----------------------------------------------------------------
@Composable
fun ConfiguracoesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .systemBarsPadding()
    ) {
        // Cabeçalho[cite: 35]
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 12.dp), //[cite: 35]
            verticalAlignment = Alignment.CenterVertically //[cite: 35]
        ) {
            Card(
                shape = RoundedCornerShape(14.dp), //[cite: 35]
                colors = CardDefaults.cardColors(containerColor = Color.White), //[cite: 35]
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), //[cite: 35]
                modifier = Modifier.size(42.dp) //[cite: 35]
            ) { /* Botão Voltar */ }
            Text(text = "Configurações", color = Color(0xFF0F172A), fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 14.dp)) //[cite: 35]
        }

        // Conteúdo Rolável[cite: 35]
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                shape = RoundedCornerShape(22.dp), //[cite: 35]
                colors = CardDefaults.cardColors(containerColor = Color.White), //[cite: 35]
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), //[cite: 35]
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp) //[cite: 35]
            ) {
                Column(modifier = Modifier.padding(18.dp)) { //[cite: 35]
                    Text(text = "Formato da Câmera", color = Color(0xFF0F172A), fontSize = 20.sp, fontWeight = FontWeight.Bold) //[cite: 35]
                    Text(text = "Essa configuração vale apenas para: ARB-05, ARB-06, ARB-07 e N.A.", color = Color(0xFF64748B), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) //[cite: 35]
                    Text(text = "A foto continua sendo tirada normalmente...", color = Color(0xFF94A3B8), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) //[cite: 35]

                    Spacer(modifier = Modifier.height(18.dp)) //[cite: 35]

                    // Lista de Opções de Rádio (Componentizadas para código limpo)[cite: 35]
                    ItemRadioConfiguracao(titulo = "4:5 (Vertical - Padrão)", selecionado = true) //[cite: 35]
                    ItemRadioConfiguracao(titulo = "3:4 (Vertical - Recomendado)") //[cite: 35]
                    ItemRadioConfiguracao(titulo = "9:16 (Vertical - Tela cheia)") //[cite: 35]
                    ItemRadioConfiguracao(titulo = "1:1 (Quadrado)") //[cite: 35]
                    ItemRadioConfiguracao(titulo = "Full (Proporção original do celular)", isUltimo = true) //[cite: 35]
                }
            }
        }

        // Botão Salvar (Fixo no Rodapé)[cite: 35]
        Card(
            shape = RoundedCornerShape(18.dp), //[cite: 35]
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB)), //[cite: 35]
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp), //[cite: 35]
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp).padding(bottom = 20.dp) //[cite: 35]
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { //[cite: 35]
                Text(text = "Salvar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) //[cite: 35]
            }
        }
    }
}

@Composable
private fun ItemRadioConfiguracao(titulo: String, selecionado: Boolean = false, isUltimo: Boolean = false) {
    Card(
        shape = RoundedCornerShape(18.dp), //[cite: 35]
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), //[cite: 35]
        modifier = Modifier.fillMaxWidth().padding(bottom = if (isUltimo) 0.dp else 12.dp) //[cite: 35]
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).padding(16.dp), //[cite: 35]
            verticalAlignment = Alignment.CenterVertically //[cite: 35]
        ) {
            RadioButton(
                selected = selecionado,
                onClick = null, // Controlado pelo pai[cite: 35]
                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2563EB)) //[cite: 35]
            )
            Text(text = titulo, color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)) //[cite: 35]
        }
    }
}
