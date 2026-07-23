package com.raylson.jansen.inspetor

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class ConfiguracoesActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME     = "inspetor_prefs"
        const val PREF_PROPORCAO = "proporcao_camera"
        const val PROP_4x5       = "4x5"   // ← NOVO: proporção padrão
        const val PROP_3x4       = "3x4"
        const val PROP_9x16      = "9x16"
        const val PROP_1x1       = "1x1"
        const val PROP_FULL      = "full"

        // Aliases para compatibilidade com DashboardActivity
        const val KEY_CAMERA_RATIO = PREF_PROPORCAO
        const val RATIO_4X5        = PROP_4x5
        const val RATIO_3X4        = PROP_3x4
        const val RATIO_9X16       = PROP_9x16
        const val RATIO_1X1        = PROP_1x1
        const val RATIO_FULL       = PROP_FULL
    }

    private lateinit var prefs: SharedPreferences
    // ← PADRÃO alterado de PROP_3x4 para PROP_4x5
    private var selecaoAtual = PROP_4x5

    // Cores inline — sem dependência de colors.xml
    private val corSelecionado  = 0xFFDBEAFE.toInt()  // azul claro
    private val corNormal       = 0xFFF8FAFC.toInt()  // cinza claro

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes)

        prefs = SecurePrefs.get(this, PREFS_NAME)
        // ← Default do SharedPreferences também alterado para PROP_4x5
        selecaoAtual = prefs.getString(PREF_PROPORCAO, PROP_4x5) ?: PROP_4x5

        // Views — CardView recebe o ID btnVoltar (corrigido no XML)
        val btnVoltar  = findViewById<CardView>(R.id.btnVoltar)
        val btnSalvar  = findViewById<CardView>(R.id.btnSalvar)
        val card4x5    = findViewById<CardView>(R.id.cardOption4x5)   // ← NOVO
        val card3x4    = findViewById<CardView>(R.id.cardOption3x4)
        val card9x16   = findViewById<CardView>(R.id.cardOption9x16)
        val card1x1    = findViewById<CardView>(R.id.cardOption1x1)
        val cardFull   = findViewById<CardView>(R.id.cardOptionFull)
        val radio4x5   = findViewById<RadioButton>(R.id.radio_4x5)    // ← NOVO
        val radio3x4   = findViewById<RadioButton>(R.id.radio_3x4)
        val radio9x16  = findViewById<RadioButton>(R.id.radio_9x16)
        val radio1x1   = findViewById<RadioButton>(R.id.radio_1x1)
        val radioFull  = findViewById<RadioButton>(R.id.radio_full)

        fun atualizar(prop: String) {
            selecaoAtual = prop

            radio4x5.isChecked  = prop == PROP_4x5
            radio3x4.isChecked  = prop == PROP_3x4
            radio9x16.isChecked = prop == PROP_9x16
            radio1x1.isChecked  = prop == PROP_1x1
            radioFull.isChecked = prop == PROP_FULL

            card4x5.setCardBackgroundColor(if (prop == PROP_4x5)  corSelecionado else corNormal)
            card3x4.setCardBackgroundColor(if (prop == PROP_3x4)  corSelecionado else corNormal)
            card9x16.setCardBackgroundColor(if (prop == PROP_9x16) corSelecionado else corNormal)
            card1x1.setCardBackgroundColor(if (prop == PROP_1x1)  corSelecionado else corNormal)
            cardFull.setCardBackgroundColor(if (prop == PROP_FULL) corSelecionado else corNormal)
        }

        atualizar(selecaoAtual)

        card4x5.setOnClickListener  { atualizar(PROP_4x5)  }   // ← NOVO
        card3x4.setOnClickListener  { atualizar(PROP_3x4)  }
        card9x16.setOnClickListener { atualizar(PROP_9x16) }
        card1x1.setOnClickListener  { atualizar(PROP_1x1)  }
        cardFull.setOnClickListener { atualizar(PROP_FULL)  }

        btnSalvar.setOnClickListener {
            prefs.edit().putString(PREF_PROPORCAO, selecaoAtual).apply()
            Toast.makeText(this, "Configuração salva!", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnVoltar.setOnClickListener { finish() }
    }
}
