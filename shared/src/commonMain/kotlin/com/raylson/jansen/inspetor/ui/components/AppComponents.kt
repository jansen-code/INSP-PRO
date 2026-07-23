package com.raylson.jansen.inspetor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Cores Extraídas dos seus XMLs
val CorAzulPrimario = Color(0xFF2563EB) //[cite: 9, 10, 11]
val CorAzulClaro = Color(0xFF3B82F6) //[cite: 3]
val CorTextoEscuro = Color(0xFF0F172A) //[cite: 4, 5, 6]
val CorCardEscuro = Color(0xFF1E2330) //[cite: 7, 8]
val CorBadgeVermelho = Color(0xFFEF4444) //[cite: 5, 7]

// -----------------------------------------------------------------
// ARQUIVO 2: Card Hm-01[cite: 3]
// -----------------------------------------------------------------
@Composable
fun ItemHidrometro() {
    Card(
        shape = RoundedCornerShape(14.dp), //[cite: 3]
        colors = CardDefaults.cardColors(containerColor = CorAzulClaro), //[cite: 3]
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), //[cite: 3]
        modifier = Modifier.size(width = 68.dp, height = 48.dp).padding(horizontal = 9.dp) //[cite: 3]
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "HM-01", //[cite: 3]
                color = Color.White, //[cite: 3]
                fontSize = 12.sp, //[cite: 3]
                fontWeight = FontWeight.Bold, //[cite: 3]
                maxLines = 1 //[cite: 3]
            )
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 3: Card Det 1 Quadrado[cite: 4]
// -----------------------------------------------------------------
@Composable
fun ItemQuadradoFiltro() {
    Card(
        shape = RoundedCornerShape(16.dp), //[cite: 4]
        colors = CardDefaults.cardColors(containerColor = Color.White), //[cite: 4]
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), //[cite: 4]
        modifier = Modifier.padding(6.dp).aspectRatio(1f) // Equivale a 1:1 e constraintDimensionRatio[cite: 4]
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp), //[cite: 4]
            horizontalAlignment = Alignment.CenterHorizontally, //[cite: 4]
            verticalArrangement = Arrangement.Center //[cite: 4]
        ) {
            Text(text = "DET 1", color = CorTextoEscuro, fontWeight = FontWeight.Black, fontSize = 14.sp) //[cite: 4]
            Text(text = "PENDENTE", color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium, fontSize = 15.sp) //[cite: 4]
            Text(text = "--:--", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) //[cite: 4]
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 4: Card Arb-01 com Badge Notificação[cite: 5]
// -----------------------------------------------------------------
@Composable
fun ItemListaNa() {
    // Box pai substitui o ConstraintLayout para empilhar o badge por cima do Card[cite: 5]
    Box(modifier = Modifier.fillMaxWidth().padding(6.dp)) { //[cite: 5]
        Card(
            shape = RoundedCornerShape(16.dp), //[cite: 5]
            colors = CardDefaults.cardColors(containerColor = Color.White), //[cite: 5]
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), //[cite: 5]
            modifier = Modifier.fillMaxWidth() //[cite: 5]
        ) {
            Column(modifier = Modifier.padding(8.dp)) { //[cite: 5]
                // Área do ícone Régua[cite: 5]
                Box(modifier = Modifier.size(44.dp).background(Color.LightGray).padding(bottom = 6.dp)) 
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "ARB-01", color = CorTextoEscuro, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) //[cite: 5]
                    Text(text = ">") // Ícone chevron simplificado[cite: 5]
                }
                Text(text = "Aguardando", color = CorAzulPrimario, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp)) //[cite: 5]
            }
        }
        
        // Badge Notificação (canto superior direito)[cite: 5]
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd) //[cite: 5]
                .padding(top = 2.dp, end = 2.dp) //[cite: 5]
                .size(18.dp) //[cite: 5]
                .background(CorBadgeVermelho, CircleShape) // Equivalente a cornerRadius 9dp e bg #EF4444[cite: 5]
        ) {
            Text(text = "1", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) //[cite: 5]
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 5: Item Registro (HM-01 Ligada)[cite: 6]
// -----------------------------------------------------------------
@Composable
fun ItemRegistro() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp), //[cite: 6]
        verticalAlignment = Alignment.CenterVertically //[cite: 6]
    ) {
        // Ícone Câmera[cite: 6]
        Box(modifier = Modifier.size(44.dp).background(Color(0xFFFEF0EB), RoundedCornerShape(14.dp))) //[cite: 6]
        
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) { //[cite: 6]
            Text(text = "DET-01 / HM-01", color = CorTextoEscuro, fontSize = 14.sp, fontWeight = FontWeight.Bold) //[cite: 6]
            Text(text = "LIGADA", color = CorTextoEscuro, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp)) //[cite: 6]
        }
        Text(text = "Hoje", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.align(Alignment.Top).padding(top = 2.dp)) //[cite: 6]
    }
}

// -----------------------------------------------------------------
// ARQUIVO 6: Item Registro Câmera[cite: 7]
// -----------------------------------------------------------------
@Composable
fun ItemRegistroCamera() {
    Card(
        shape = RoundedCornerShape(14.dp), //[cite: 7]
        colors = CardDefaults.cardColors(containerColor = CorCardEscuro), //[cite: 7]
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp) //[cite: 7]
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 14.dp), //[cite: 7]
                verticalAlignment = Alignment.CenterVertically //[cite: 7]
            ) {
                // Ícone câmera vermelho[cite: 7]
                Box(modifier = Modifier.size(46.dp).background(CorBadgeVermelho, RoundedCornerShape(12.dp))) //[cite: 7]
                
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) { //[cite: 7]
                    Text(text = "REGISTRO DO / HM-01", color = Color(0xFFE8ECF4), fontSize = 13.sp, fontWeight = FontWeight.Bold) //[cite: 7]
                    Text(text = "Toque para fotografar", color = Color(0xFF5A6478), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)) //[cite: 7]
                }
            }
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 7: Botão Gerar Registro[cite: 8]
// -----------------------------------------------------------------
@Composable
fun ItemRegistroFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), //[cite: 8]
        horizontalArrangement = Arrangement.End // Equivalente ao gravity="end"[cite: 8]
    ) {
        Box(
            modifier = Modifier
                .size(64.dp) //[cite: 8]
                .background(CorCardEscuro.copy(alpha = 0.35f), RoundedCornerShape(14.dp)), //[cite: 8]
            contentAlignment = Alignment.Center //[cite: 8]
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { //[cite: 8]
                Text(text = "GR", color = Color(0xFFE8ECF4), fontSize = 16.sp, fontWeight = FontWeight.Bold) //[cite: 8]
                Text(text = "gerar", color = Color(0xFF5A6478), fontSize = 9.sp) //[cite: 8]
            }
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 8: Cabeçalho com Toggle[cite: 9]
// -----------------------------------------------------------------
@Composable
fun ItemRegistroHeader() {
    Column {
        Card(
            shape = RoundedCornerShape(16.dp), //[cite: 9]
            colors = CardDefaults.cardColors(containerColor = CorAzulPrimario), //[cite: 9]
            modifier = Modifier.fillMaxWidth().padding(16.dp) //[cite: 9]
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp), //[cite: 9]
                verticalAlignment = Alignment.CenterVertically //[cite: 9]
            ) {
                // Ícone[cite: 9]
                Box(modifier = Modifier.size(52.dp).background(Color(0xFF1D4ED8), RoundedCornerShape(10.dp))) //[cite: 9]
                
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) { //[cite: 9]
                    Text(text = "DET-01", color = Color(0xFFBFDBFE), fontSize = 9.sp) //[cite: 9]
                    Text(text = "DET-01", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) //[cite: 9]
                }
                
                Box(modifier = Modifier.width(1.dp).height(52.dp).background(CorAzulClaro).padding(end = 14.dp)) // Linha divisória[cite: 9]
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) { //[cite: 9]
                    Text(text = "STATUS", color = Color(0xFFBFDBFE), fontSize = 9.sp, modifier = Modifier.padding(bottom = 6.dp)) //[cite: 9]
                    // Toggle substituto[cite: 9]
                    Text(text = "OFF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) //[cite: 9]
                }
            }
        }
        
        // Seção Registros Recentes[cite: 9]
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), //[cite: 9]
            verticalAlignment = Alignment.CenterVertically //[cite: 9]
        ) {
            Text(text = "REGISTROS RECENTES", color = Color(0xFF5A6478), fontSize = 9.sp) //[cite: 9]
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF252B38)).padding(start = 10.dp)) //[cite: 9]
            Text(text = "Ver Todos", color = CorAzulPrimario, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp)) //[cite: 9]
        }
    }
}

// -----------------------------------------------------------------
// ARQUIVO 9: Linha de Seção[cite: 10]
// -----------------------------------------------------------------
@Composable
fun ItemSecaoHistorico() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) { //[cite: 10]
        Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color(0xFFE2E8F0)).padding(bottom = 10.dp)) //[cite: 10]
        Text(text = "ARB-07", color = CorAzulPrimario, fontSize = 13.sp, fontWeight = FontWeight.Bold) //[cite: 10]
    }
}

// -----------------------------------------------------------------
// ARQUIVO 10: Card Estação[cite: 11]
// -----------------------------------------------------------------
@Composable
fun ItemStation() {
    Card(
        shape = RoundedCornerShape(24.dp), //[cite: 11]
        colors = CardDefaults.cardColors(containerColor = CorAzulPrimario), //[cite: 11]
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp), //[cite: 11]
        modifier = Modifier.size(100.dp).padding(horizontal = 6.dp) //[cite: 11]
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) { //[cite: 11]
            Text(
                text = "DET-01", //[cite: 11]
                color = Color.White, //[cite: 11]
                fontSize = 13.sp, //[cite: 11]
                fontWeight = FontWeight.Bold, //[cite: 11]
                textAlign = TextAlign.Center, //[cite: 11]
                maxLines = 2, //[cite: 11]
                overflow = TextOverflow.Ellipsis //[cite: 11]
            )
        }
    }
}
