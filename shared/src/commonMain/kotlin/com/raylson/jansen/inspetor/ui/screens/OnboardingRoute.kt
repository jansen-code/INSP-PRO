package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

/**
 * ═══════════════════════════════════════════════════════════════════
 * OnboardingRoute.kt
 *
 * Agora com a fiação completa: `OnboardingScreen()` (em
 * OnboardingScreen.kt) ganhou 4 parâmetros opcionais aditivos, então
 * dá pra controlar tudo a partir do `OnboardingScreenModel` sem alterar
 * o comportamento default da função original.
 * ═══════════════════════════════════════════════════════════════════
 */
object OnboardingRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { OnboardingScreenModel() }
        val uiState by screenModel.state.collectAsState()

        OnboardingScreen(
            apelido = uiState.apelido,
            onApelidoChange = screenModel::onApelidoMudou,
            erro = uiState.erro,
            onConfirmar = {
                // Era `salvarEContinuar()` -> startActivity(Dashboard) + finish().
                // `replace` em vez de `push`: não queremos poder "voltar" pro
                // Onboarding depois de confirmado (mesmo efeito do finish() original).
                if (screenModel.salvarEContinuar()) {
                    navigator.replace(DashboardScreen)
                }
            }
        )
    }
}
