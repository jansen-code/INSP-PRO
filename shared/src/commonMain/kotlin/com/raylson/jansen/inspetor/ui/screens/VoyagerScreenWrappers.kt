package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

/**
 * ═══════════════════════════════════════════════════════════════════
 * VoyagerScreenWrappers.kt
 *
 * `HistoricoScreen()` e `ControleNAScreen()` (MainScreens.kt) são
 * funções @Composable soltas — esse arquivo NÃO é tocado (Regra de
 * Ouro). Para navegar com Voyager (`navigator.push(...)`) precisamos de
 * um `object : Screen`; como não pode ter o mesmo nome da função no
 * mesmo pacote, o wrapper leva o sufixo "Route".
 * ═══════════════════════════════════════════════════════════════════
 */

object HistoricoRoute : Screen {
    @Composable
    override fun Content() {
        HistoricoScreen() // função original de MainScreens.kt, sem alteração
    }
}

object ControleNARoute : Screen {
    @Composable
    override fun Content() {
        ControleNAScreen() // função original de MainScreens.kt, sem alteração
    }
}

object CofreRoute : Screen {
    @Composable
    override fun Content() {
        CofreScreen() // stub em CofreScreens.kt, sem alteração
    }
}

object ConfiguracoesRoute : Screen {
    @Composable
    override fun Content() {
        ConfiguracoesScreen()
    }
}
