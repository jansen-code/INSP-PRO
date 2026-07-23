package com.raylson.jansen.inspetor.ui.screens

// Repare que TODOS os imports são androidx.compose ou org.jetbrains.compose.
// NENHUM import do Android nativo!
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
// Import para as imagens do KMP (ajuste conforme a configuração do seu KMP resources)
import org.jetbrains.compose.resources.painterResource
import inspetor_flutter.shared.generated.resources.Res
import inspetor_flutter.shared.generated.resources.ic_logo_saneamento

// Cores baseadas no seu XML
val AzulPrimario = Color(0xFF2563EB)
val TextoEscuro = Color(0xFF111827)
val TextoDica = Color(0xFF9CA3AF)
val FundoInput = Color(0xFFF3F4F6) // Cor aproximada de um input cinza claro

@Composable
fun OnboardingScreen() {
    // Gerenciamento de estado (substitui o getText() do EditText)
    var apelido by remember { mutableStateOf("") }

    // Column substitui o ConstraintLayout para empilhar itens verticalmente
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(), // O equivalente KMP ao android:fitsSystemWindows="true"
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
                // TODO: Ação ao clicar no botão
                println("Apelido digitado: $apelido") 
            }
        )
    }
}

@Composable
private fun LogoContainer() {
    // Box substitui o FrameLayout do seu XML
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
    ) {
        // Círculo Tracejado Externo desenhado no próprio Compose
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 2.dp, color = AzulPrimario.copy(alpha = 0.5f), shape = CircleShape)
        )

        // Círculo Branco Interno com Sombra (substitui a tag View com elevation)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .background(color = Color.White, shape = CircleShape)
        ) {
            // A Logo
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
    // Row substitui o LinearLayout horizontal do XML
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .height(56.dp)
            .background(color = FundoInput, shape = RoundedCornerShape(12.dp)) // Equivalente ao bg_input_rounded
            .padding(horizontal = 18.dp)
    ) {
        // BasicTextField nos dá controle total sem as linhas padrão do Material
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = TextoEscuro,
                fontSize = 16.sp
                // fontFamily = AppFontUiRegular // Adicione sua fonte aqui depois
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words, // textCapWords do XML
                imeAction = ImeAction.Done // imeOptions="actionDone" do XML
            ),
            singleLine = true, // maxLines="1" do XML
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(text = "Como se chama?", color = TextoDica, fontSize = 16.sp) // Hint do XML
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun MonitorButton(onClick: () -> Unit) {
    // Button substitui o AppCompatButton do XML
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AzulPrimario),
        shape = RoundedCornerShape(14.dp) // Equivalente ao bg_button_rounded
    ) {
        Text(
            text = "MONITORAR",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
            // fontFamily = AppFontUiMedium // Adicione sua fonte aqui depois
        )
    }
}
