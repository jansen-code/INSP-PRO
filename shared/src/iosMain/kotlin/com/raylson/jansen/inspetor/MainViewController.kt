package com.raylson.jansen.inspetor

import androidx.compose.ui.window.ComposeUIViewController
import com.raylson.jansen.inspetor.ui.screens.OnboardingScreen // Importamos a tela KMP

// A ponte para o iOS agora aponta diretamente para o Onboarding
fun MainViewController() = ComposeUIViewController { 
    OnboardingScreen() 
}
