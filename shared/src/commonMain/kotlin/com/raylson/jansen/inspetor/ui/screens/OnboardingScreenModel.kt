package com.raylson.jansen.inspetor.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import com.raylson.jansen.inspetor.platform.SecureStorage
import com.raylson.jansen.inspetor.platform.createSecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ═══════════════════════════════════════════════════════════════════
 * OnboardingScreenModel.kt
 *
 * Port de OnboardingActivity.kt. A tela original só fazia 3 coisas:
 * validar o apelido, salvar em SecurePrefs("inspetor_prefs") com as
 * chaves "apelido" e "onboarding_concluido", e navegar pro Dashboard.
 * ═══════════════════════════════════════════════════════════════════
 */
data class OnboardingUiState(
    val apelido: String = "",
    val erro: String? = null
)

class OnboardingScreenModel : ScreenModel {

    private val prefs: SecureStorage = createSecureStorage("inspetor_prefs")

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onApelidoMudou(valor: String) {
        _state.update { it.copy(apelido = valor, erro = null) }
    }

    /**
     * Era `salvarEContinuar()`. Retorna true se validou e salvou —
     * a tela usa o retorno pra decidir se navega pro Dashboard.
     */
    fun salvarEContinuar(): Boolean {
        val apelido = _state.value.apelido.trim()
        if (apelido.isEmpty()) {
            _state.update { it.copy(erro = "Digite seu nome ou apelido") }
            return false
        }

        prefs.putString("apelido", apelido)
        prefs.putBoolean("onboarding_concluido", true)
        return true
    }

    companion object {
        /** Era o `if (onboarding_concluido) DashboardActivity else OnboardingActivity` da splash/launcher. */
        fun onboardingJaConcluido(): Boolean =
            createSecureStorage("inspetor_prefs").getBoolean("onboarding_concluido", false)
    }
}
