package com.raylson.jansen.inspetor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DialogConfirmacaoVazaoScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(24.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFEFF6FF), RoundedCornerShape(14.dp)))
                    Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(text = "CONFIRMAÇÃO", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Tem vazão?", color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 16.dp).background(Color(0xFFE2E8F0)))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(46.dp).padding(end = 8.dp)
                    ) { Text(text = "NÃO", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(46.dp).padding(start = 8.dp)
                    ) { Text(text = "SIM", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun ItemFotoCofre(selecionado: Boolean = false) {
    Column(modifier = Modifier.padding(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxSize()
            ) { /* ImageView placeholder */ }
            if (selecionado) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .border(1.4.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                ) { Text(text = "✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            text = "1. N.A - 05.07.2026 14:30h",
            color = Color(0xFF64748B),
            fontSize = 10.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun ItemPastaCofre() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE)),
        modifier = Modifier.fillMaxWidth().height(128.dp).padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(44.dp).background(Color.White, RoundedCornerShape(14.dp)))
            Text(text = "DET-01", color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text(text = "0 fotos", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}