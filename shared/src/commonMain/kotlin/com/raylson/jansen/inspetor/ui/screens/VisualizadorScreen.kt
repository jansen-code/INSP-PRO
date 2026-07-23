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

// Importação Corrigida
import inspetor.shared.generated.resources.Res

@Composable
fun VisualizadorScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) 
    ) {
        // Observação: Para esta tela rodar corretamente sem erros adicionais, 
        // certifique-se de referenciar uma imagem que exista dentro do seu Res.drawable 
        // (por exemplo: Res.drawable.ic_logo_saneamento) em vez de 'sua_imagem_aqui'.
        Image(
            painter = painterResource(Res.drawable.sua_imagem_aqui),
            contentDescription = "Foto em tela cheia",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}