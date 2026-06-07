package id.irnhakim.guardian.ui.screens.setup

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import id.irnhakim.guardian.ui.viewmodel.MainViewModel
import id.irnhakim.guardian.ui.viewmodel.SetupStep

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.setupState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("http://192.168.1.21:3001") }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F1117), Color(0xFF161B27))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Shield icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF5C7CFA), Color(0xFFA78BFA))),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("🛡️", fontSize = 36.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Guardian",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5C7CFA),
            )
            Text(
                "Parental Control Setup",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
            )

            Spacer(Modifier.height(40.dp))

            // Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1C2333),
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Connect to Guardian Server",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF1F5F9),
                    )

                    Spacer(Modifier.height(20.dp))

                    GuardianTextField(
                        label = "Server URL",
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        placeholder = "http://192.168.1.21:3001",
                    )

                    Spacer(Modifier.height(12.dp))

                    // Email
                    GuardianTextField(
                        label = "Parent Email",
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "parent@example.com",
                        keyboardType = KeyboardType.Email,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Password
                    GuardianTextField(
                        label = "Password",
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "••••••••",
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                    )

                    // Error
                    AnimatedVisibility(visible = state.error != null) {
                        state.error?.let { err ->
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x1AEF4444),
                            ) {
                                Text(
                                    err,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color(0xFFF87171),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Submit button
                    Button(
                        onClick = {
                            viewModel.setup(email, password, serverUrl, onSetupComplete)
                        },
                        enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5C7CFA),
                        ),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (state.step) {
                                    SetupStep.CREDENTIALS -> "Connecting..."
                                    SetupStep.REGISTERING -> "Registering device..."
                                    SetupStep.DONE -> "Done!"
                                },
                                color = Color.White,
                            )
                        } else {
                            Text("Connect & Register Device", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "This device will be monitored by the parent account.",
                fontSize = 12.sp,
                color = Color(0xFF475569),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun GuardianTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
) {
    Column {
        Text(label, fontSize = 13.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color(0xFF475569), fontSize = 14.sp) },
            visualTransformation = if (isPassword && !showPassword)
                PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF5C7CFA),
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedContainerColor = Color(0xFF161B27),
                unfocusedContainerColor = Color(0xFF161B27),
                cursorColor = Color(0xFF5C7CFA),
                focusedTextColor = Color(0xFFF1F5F9),
                unfocusedTextColor = Color(0xFFF1F5F9),
            ),
            singleLine = true,
        )
    }
}
