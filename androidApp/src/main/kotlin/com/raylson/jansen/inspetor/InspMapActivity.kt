package com.raylson.jansen.inspetor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cafe.adriel.voyager.navigator.Navigator
import com.raylson.jansen.inspetor.ui.screens.InspMapScreen

class InspMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Navigator(screen = InspMapScreen)
        }
    }
}
