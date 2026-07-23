package com.raylson.jansen.inspetor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// 1. Adicionamos o import da sua tela nova que está lá na pasta shared
import com.raylson.jansen.inspetor.ui.screens.OnboardingScreen 

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // Mantemos isso! É ótimo para a tela cheia
        super.onCreate(savedInstanceState)

        setContent {
            // 2. Trocamos o App() pela sua tela de Onboarding
            OnboardingScreen()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // 3. Trocamos aqui também para o Preview do Android Studio mostrar a sua tela
    OnboardingScreen()
}
