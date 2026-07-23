package com.raylson.jansen.inspetor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = SecurePrefs.get(this, "inspetor_prefs")
            val onboardingConcluido = prefs.getBoolean("onboarding_concluido", false)
            val apelidoSalvo = prefs.getString("apelido", "")?.trim().orEmpty()

            val proximaTela = if (onboardingConcluido && apelidoSalvo.isNotEmpty()) {
                DashboardActivity::class.java
            } else {
                OnboardingActivity::class.java
            }

            startActivity(Intent(this, proximaTela))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }
}
