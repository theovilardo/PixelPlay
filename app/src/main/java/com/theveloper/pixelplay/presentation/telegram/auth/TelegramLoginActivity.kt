package com.theveloper.pixelplay.presentation.telegram.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import org.drinkless.tdlib.TdApi
import androidx.compose.runtime.getValue
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class TelegramLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixelPlayTheme {
                TelegramLoginScreen(onFinish = { finish() })
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TelegramLoginScreen(
    viewModel: TelegramLoginViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val authState by viewModel.authorizationState.collectAsState(initial = null)
    val isLoading by viewModel.isLoading.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val code by viewModel.code.collectAsState()
    val password by viewModel.password.collectAsState()
    var showSearchSheet by remember { mutableStateOf(false) }

    if (showSearchSheet) {
        com.theveloper.pixelplay.presentation.telegram.channel.TelegramChannelSearchSheet(
            onDismissRequest = { showSearchSheet = false },
            onSongSelected = { song -> 
                viewModel.downloadAndPlay(song)
            }
        )
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.playbackRequest.collect { song: com.theveloper.pixelplay.data.model.Song ->
             val intent = android.content.Intent(context, com.theveloper.pixelplay.MainActivity::class.java).apply {
                 action = "com.theveloper.pixelplay.ACTION_PLAY_SONG"
                 putExtra("song", song as android.os.Parcelable)
                 addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
             }
             context.startActivity(intent)
             onFinish()
        }
    }

    if (authState is TdApi.AuthorizationStateReady && !isLoading) {
        // Show the new Dashboard
        com.theveloper.pixelplay.presentation.telegram.dashboard.TelegramDashboardScreen(
            onAddChannel = { showSearchSheet = true },
            onBack = onFinish
        )
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    when (authState) {
                        is TdApi.AuthorizationStateWaitPhoneNumber -> {
                            PhoneNumberInput(
                                phoneNumber = phoneNumber,
                                onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                                onSend = viewModel::sendPhoneNumber
                            )
                        }
                        is TdApi.AuthorizationStateWaitCode -> {
                            CodeInput(
                                code = code,
                                onCodeChanged = viewModel::onCodeChanged,
                                onCheck = viewModel::checkCode
                            )
                        }
                        is TdApi.AuthorizationStateWaitPassword -> {
                            PasswordInput(
                                password = password,
                                onPasswordChanged = viewModel::onPasswordChanged,
                                onCheck = viewModel::checkPassword
                            )
                        }
                        // Ready state is handled above
                        is TdApi.AuthorizationStateLoggingOut -> Text("Logging out...")
                        is TdApi.AuthorizationStateClosing -> Text("Closing...")
                        is TdApi.AuthorizationStateClosed -> Text("Session Closed")
                        null -> Text("Initializing TDLib...")
                        else -> Text("State: ${authState?.javaClass?.simpleName}")
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneNumberInput(
    phoneNumber: String,
    onPhoneNumberChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter Telegram Phone Number")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChanged,
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSend) {
            Text("Send Code")
        }
    }
}

@Composable
fun CodeInput(
    code: String,
    onCodeChanged: (String) -> Unit,
    onCheck: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter Verification Code")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChanged,
            label = { Text("Code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCheck) {
            Text("Verify")
        }
    }
}

@Composable
fun PasswordInput(
    password: String,
    onPasswordChanged: (String) -> Unit,
    onCheck: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter 2FA Password")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            label = { Text("Password") },
             visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCheck) {
            Text("Verify Password")
        }
    }
}
