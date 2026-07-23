package com.raylson.jansen.inspetor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var etApelido: EditText
    private lateinit var btnMonitorar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Deixa a barra de status branca com ícones escuros para combinar com o novo design
        window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        // AQUI ESTÁ A MÁGICA: Agora ele puxa o seu XML novo em vez do código antigo!
        setContentView(R.layout.activity_onboarding)

        etApelido = findViewById(R.id.etApelido)
        btnMonitorar = findViewById(R.id.btnMonitorar)

        bindInteractions()
    }

    private fun bindInteractions() {
        etApelido.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                salvarEContinuar()
                true
            } else {
                false
            }
        }

        btnMonitorar.setOnClickListener {
            salvarEContinuar()
        }
    }

    private fun salvarEContinuar() {
        val apelido = etApelido.text.toString().trim()
        if (apelido.isEmpty()) {
            etApelido.error = "Digite seu nome ou apelido"
            etApelido.requestFocus()
            return
        }

        esconderTeclado()

        SecurePrefs.get(this, "inspetor_prefs")
            .edit()
            .putString("apelido", apelido)
            .putBoolean("onboarding_concluido", true)
            .apply()

        startActivity(Intent(this, DashboardActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun esconderTeclado() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etApelido.windowToken, 0)
    }
}
