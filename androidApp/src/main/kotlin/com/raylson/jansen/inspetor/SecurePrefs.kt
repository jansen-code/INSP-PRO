package com.raylson.jansen.inspetor

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    fun get(context: Context, prefName: String): SharedPreferences {
        // Cria uma chave mestra altamente segura baseada no hardware do celular
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return try {
            // Retorna o SharedPreferences criptografado (AES256)
            EncryptedSharedPreferences.create(
                context,
                prefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // ═══ MIGRAÇÃO AUTOMÁTICA ═══
            // Se já existir um arquivo "prefName" criado ANTES da migração
            // (SharedPreferences comum, em texto puro), o EncryptedSharedPreferences
            // não consegue abri-lo (formato XML incompatível) e lança exceção aqui.
            // Como o app já estava limpando/recriando esses dados normalmente,
            // a solução segura é apagar o arquivo legado e recriar do zero,
            // já no formato criptografado.
            android.util.Log.w(
                "SecurePrefs",
                "Arquivo '$prefName' incompatível (provavelmente legado, não criptografado). Recriando do zero.",
                e
            )
            try {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit().clear().apply()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(prefName)
                }
            } catch (_: Exception) { /* segue mesmo se a limpeza do legado falhar */ }

            EncryptedSharedPreferences.create(
                context,
                prefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
