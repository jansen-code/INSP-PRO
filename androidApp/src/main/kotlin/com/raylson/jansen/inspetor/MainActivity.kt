package com.raylson.jansen.inspetor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.raylson.jansen.inspetor.platform.AndroidContextHolder

// Import do Onboarding mantido caso queira religar rapidamente pra teste
import com.raylson.jansen.inspetor.ui.screens.OnboardingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // ═══ NOVO: precisa acontecer antes de qualquer SecureStorage ser
        // criado (EncryptedSharedPreferences precisa de um Context de
        // aplicação) — substitui o `this` que a Activity antiga passava
        // direto pro SecurePrefs.get(this, ...). ═══
        AndroidContextHolder.init(applicationContext)

        setContent {
            // Trocamos o OnboardingScreen direto pelo App(), que hospeda
            // o Navigator do Voyager com o DashboardScreen como raiz.
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
