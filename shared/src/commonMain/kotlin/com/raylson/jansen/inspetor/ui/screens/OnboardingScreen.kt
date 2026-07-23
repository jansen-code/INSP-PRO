package com.raylson.jansen.inspetor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource

// Importação corrigida
import inspetor.shared.generated.resources.Res
import inspetor.shared.generated.resources.ic_logo_saneamento

val AzulPrimario = Color(0xFF2563EB)
val TextoEscuro = Color(0xFF111827)
val TextoDica = Color(0xFF9CA3AF)
val FundoInput = Color(0xFFF3F4F6)

@Composable
fun OnboardingScreen() {
    var apelido by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        LogoContainer()
        Spacer(modifier = Modifier.height(64.dp))
        OnboardingInput(
            value = apelido,
            onValueChange = { apelido = it }
        )
        Spacer(modifier = Modifier.height(32.dp))
        MonitorButton(
            onClick = { 
                println("Apelido digitado: $apelido") 
            }
        )
    }
}

@Composable
private fun LogoContainer() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
    ) {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 2.dp, color = AzulPrimario.copy(alpha = 0.5f), shape = CircleShape)
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .background(color = Color.White, shape = CircleShape)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_logo_saneamento),
                contentDescription = "Logo INSPETOR",
                modifier = Modifier.size(95.dp)
            )
        }
    }
}

@Composable
private fun OnboardingInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .height(56.dp)
            .background(color = FundoInput, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = TextoEscuro,
                fontSize = 16.sp
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(text = "Como se chama?", color = TextoDica, fontSize = 16.sp)
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun MonitorButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = "MONITORAR",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}