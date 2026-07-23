package com.raylson.jansen.inspetor

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.raylson.jansen.inspetor.ui.screens.DashboardScreen
import com.raylson.jansen.inspetor.ui.screens.OnboardingRoute
import com.raylson.jansen.inspetor.ui.screens.OnboardingScreenModel

/**
 * ═══════════════════════════════════════════════════════════════════
 * App.kt
 *
 * Ponto único de navegação, no lugar de cada tela chamar
 * `startActivity(Intent(this, XActivity::class.java))`.
 *
 * A tela raiz é decidida como no launcher original: se
 * "onboarding_concluido" ainda não foi salvo, começa no Onboarding;
 * senão, direto no Dashboard (era o `if` dentro de MainActivity/
 * SplashScreen que escolhia `OnboardingActivity` vs `DashboardActivity`).
 *
 * Regras de navegação pedidas:
 * ─────────────────────────────────────────────────────────────────
 * • Dashboard -> Histórico:      navigator.push(HistoricoRoute)
 *     (empilha; "voltar" retorna ao Dashboard)
 *
 * • Histórico -> Controle N.A.:  navigator.replace(ControleNARoute)
 *     (troca o topo da pilha; não acumula telas — equivalente a como
 *     era com `startActivity` + `finish()` implícito, sem empilhar
 *     Histórico atrás de Controle N.A.)
 *
 * • De qualquer tela, botão "voltar direto pro Dashboard":
 *     navigator.popUntilRoot()
 *     (usado pelo btnLimparGeral / navegação "home" — limpa a pilha
 *     inteira de uma vez, sem precisar de N popUp() em sequência)
 * ─────────────────────────────────────────────────────────────────
 */
@Composable
fun App() {
    val telaInicial = if (OnboardingScreenModel.onboardingJaConcluido()) DashboardScreen else OnboardingRoute
    Navigator(screen = telaInicial)
}

/*
 * ─────────────────────────────────────────────────────────────────
 * Exemplo de uso dentro de HistoricoRoute.Content() (ver
 * VoyagerScreenWrappers.kt) para ligar os botões da própria tela de
 * Histórico às regras acima:
 *
 * object HistoricoRoute : Screen {
 *     @Composable
 *     override fun Content() {
 *         val navigator = LocalNavigator.currentOrThrow
 *
 *         HistoricoScreen(
 *             onAbrirControleNA = { navigator.replace(ControleNARoute) },
 *             onVoltarParaDashboard = { navigator.popUntilRoot() }
 *         )
 *     }
 * }
 *
 * (Isso implica adicionar esses dois parâmetros de callback à função
 * `HistoricoScreen()` em MainScreens.kt quando for ligar os botões reais
 * — não incluído aqui pra não alterar esse arquivo sem necessidade,
 * conforme a Regra de Ouro.)
 * ─────────────────────────────────────────────────────────────────
 */
