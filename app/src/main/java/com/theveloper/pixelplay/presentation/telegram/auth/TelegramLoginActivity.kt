package com.theveloper.pixelplay.presentation.telegram.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import org.drinkless.tdlib.TdApi
import androidx.compose.runtime.getValue
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

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

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
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
        // Expressive Login UI
        val gradientColors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surface
        )
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        FilledIconButton(
                            onClick = onFinish,
                            modifier = Modifier.padding(start = 8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = Brush.verticalGradient(gradientColors))
                    .padding(paddingValues)
            ) {
                if (isLoading) {
                    // Expressive Loading State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Connecting to Telegram...",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    // Determine current step for indicator
                    val currentStep = when (authState) {
                        is TdApi.AuthorizationStateWaitPhoneNumber -> 0
                        is TdApi.AuthorizationStateWaitCode -> 1
                        is TdApi.AuthorizationStateWaitPassword -> 2
                        else -> -1
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Telegram Branding Header
                        TelegramBrandingHeader()
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Step Indicator
                        if (currentStep >= 0) {
                            StepIndicator(
                                currentStep = currentStep,
                                totalSteps = 3
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                        
                        // Animated Content for Auth States
                        AnimatedContent(
                            targetState = authState,
                            transitionSpec = {
                                (slideInHorizontally { width -> width } + fadeIn())
                                    .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                            },
                            label = "AuthStateTransition"
                        ) { state ->
                            when (state) {
                                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                                    ExpressivePhoneNumberInput(
                                        phoneNumber = phoneNumber,
                                        onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                                        onSend = viewModel::sendPhoneNumber
                                    )
                                }
                                is TdApi.AuthorizationStateWaitCode -> {
                                    ExpressiveCodeInput(
                                        code = code,
                                        onCodeChanged = viewModel::onCodeChanged,
                                        onCheck = viewModel::checkCode
                                    )
                                }
                                is TdApi.AuthorizationStateWaitPassword -> {
                                    ExpressivePasswordInput(
                                        password = password,
                                        onPasswordChanged = viewModel::onPasswordChanged,
                                        onCheck = viewModel::checkPassword
                                    )
                                }
                                is TdApi.AuthorizationStateLoggingOut -> StatusMessage("Logging out...")
                                is TdApi.AuthorizationStateClosing -> StatusMessage("Closing...")
                                is TdApi.AuthorizationStateClosed -> StatusMessage("Session Closed")
                                null -> StatusMessage("Initializing Telegram...")
                                else -> StatusMessage("State: ${state?.javaClass?.simpleName}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramBrandingHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Telegram-style icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_send_24),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = "Connect Telegram",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Stream music from your Telegram channels",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { step ->
            val isActive = step <= currentStep
            val isCurrent = step == currentStep
            
            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "stepScale"
            )
            
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(if (isCurrent) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun StatusMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePhoneNumberInput(
    phoneNumber: String,
    onPhoneNumberChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 16.dp, cornerRadiusTL = 16.dp,
        cornerRadiusBR = 16.dp, cornerRadiusBL = 16.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Phone,
            title = "Phone Number",
            subtitle = "Enter your Telegram phone number with country code"
        )
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChanged,
            label = { Text("Phone Number", fontFamily = GoogleSansRounded) },
            placeholder = { Text("+1 234 567 8900") },
            leadingIcon = { 
                Icon(
                    Icons.Rounded.Phone, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                ) 
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = inputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )
        
        Spacer(Modifier.height(32.dp))
        
        ExpressiveButton(
            text = "Send Code",
            onClick = onSend,
            enabled = phoneNumber.isNotBlank()
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCodeInput(
    code: String,
    onCodeChanged: (String) -> Unit,
    onCheck: () -> Unit
) {
    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 16.dp, cornerRadiusTL = 16.dp,
        cornerRadiusBR = 16.dp, cornerRadiusBL = 16.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Sms,
            title = "Verification Code",
            subtitle = "Enter the code sent to your Telegram app"
        )
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChanged,
            label = { Text("Code", fontFamily = GoogleSansRounded) },
            placeholder = { Text("12345") },
            leadingIcon = { 
                Icon(
                    Icons.Rounded.Sms, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                ) 
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = inputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )
        
        Spacer(Modifier.height(32.dp))
        
        ExpressiveButton(
            text = "Verify",
            onClick = onCheck,
            enabled = code.isNotBlank()
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePasswordInput(
    password: String,
    onPasswordChanged: (String) -> Unit,
    onCheck: () -> Unit
) {
    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 16.dp, cornerRadiusTL = 16.dp,
        cornerRadiusBR = 16.dp, cornerRadiusBL = 16.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Lock,
            title = "Two-Factor Password",
            subtitle = "Enter your 2FA password to continue"
        )
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            label = { Text("Password", fontFamily = GoogleSansRounded) },
            leadingIcon = { 
                Icon(
                    Icons.Rounded.Lock, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                ) 
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = inputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )
        
        Spacer(Modifier.height(32.dp))
        
        ExpressiveButton(
            text = "Verify Password",
            onClick = onCheck,
            enabled = password.isNotBlank()
        )
    }
}

@Composable
private fun AuthStepHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )
    
    MediumExtendedFloatingActionButton(
        text = { 
            Text(
                text = text,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            ) 
        },
        icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
        onClick = onClick,
        expanded = true,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource
    )
}
