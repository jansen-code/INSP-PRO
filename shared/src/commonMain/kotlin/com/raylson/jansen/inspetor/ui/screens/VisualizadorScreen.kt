package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import inspetor_flutter.shared.generated.resources.Res
// Adicione o import da sua imagem aqui

@Composable
fun VisualizadorScreen() {
    // Box substitui o FrameLayout, com fundo preto[cite: 2]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) 
    ) {
        // Image substitui o ImageView em tela cheia[cite: 2]
        Image(
            painter = painterResource(Res.drawable.sua_imagem_aqui), // Substitua pela imagem real
            contentDescription = "Foto em tela cheia", //[cite: 2]
            contentScale = ContentScale.Fit, // Equivalente ao scaleType="fitCenter"[cite: 2]
            modifier = Modifier.fillMaxSize()
        )
    }
}
