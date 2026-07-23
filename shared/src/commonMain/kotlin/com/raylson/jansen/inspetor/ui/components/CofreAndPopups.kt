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

// -----------------------------------------------------------------
// ARQUIVO 20: Item Foto Cofre
// -----------------------------------------------------------------
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

// -----------------------------------------------------------------
// ARQUIVO 21: Item Pasta Cofre
// -----------------------------------------------------------------
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
