package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Sync // Importar icono de Sync
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.DirectoryItem
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playerViewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    // Recopilar el estado de la UI del ViewModel
    val uiState by settingsViewModel.uiState.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    // Estado para controlar la visibilidad del diálogo de directorios
    var showDirectoryDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    val directoryItems: ImmutableList<DirectoryItem> = remember(uiState.directoryItems) {
        val list: List<DirectoryItem> = uiState.directoryItems
        val immutable: ImmutableList<DirectoryItem> = list.toImmutableList() // Now this should work
        immutable
    }

    // Efecto para animaciones de transición
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) {
        transitionState.targetState = true
    }

    val transition = rememberTransition(transitionState, label = "SettingsAppearTransition")

    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 40.dp }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        onClick = onNavigationIconClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .padding(8.dp)
                            .padding(start = 4.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            painterResource(R.drawable.rounded_arrow_back_24),
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset.toPx()
                }
                .verticalScroll(scrollState)
        ) {
            // Sección de gestión de música
            SettingsSection(
                title = "Music Management",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                //SettingsCard {
                Column(
                    modifier = Modifier.clip(
                        shape = RoundedCornerShape(24.dp)
                    )
                ) {
                    SettingsItem(
                        title = "Allowed Directories",
                        subtitle = "Choose the directories you want to get the music files from.",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Abrir selector",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { showDirectoryDialog = true }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    //}
                    SettingsItem(
                        title = "Refresh Library",
                        subtitle = "Rescan MediaStore and update the local database.",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        onClick = { settingsViewModel.refreshLibrary() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección de apariencia
            SettingsSection(
                title = "Appearance",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Column(
                    Modifier
                        .background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clip(
                            shape = RoundedCornerShape(24.dp)
                        )
                ){
                    // Selector para Tema del Reproductor
                    ThemeSelectorItem(
                        label = "Player Theme",
                        description = "Choose the appearance for the floating player.",
                        options = mapOf(
                            ThemePreference.ALBUM_ART to "Album Art", // Default
                            ThemePreference.DYNAMIC to "System Dynamic" // Or ThemePreference.DEFAULT if it means system dynamic
                        ),
                        selectedKey = uiState.playerThemePreference,
                        onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                }
            }

            // Aquí podrías añadir más secciones de configuración
            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(
                title = "AI Integration",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.gemini_ai),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                GeminiApiKeyItem(
                    apiKey = geminiApiKey,
                    onApiKeyChange = { settingsViewModel.onGeminiApiKeyChange(it) },
                    title = "Gemini API Key",
                    subtitle = "Needed for AI-powered features."
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección de Opciones de Desarrollador
            SettingsSection(
                title = "Developer Options",
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Column(
                    Modifier
                        .background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clip(
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    SettingsSwitchItem(
                        title = "Mock Genres",
                        subtitle = "Use hardcoded genres for testing purposes.",
                        checked = uiState.mockGenresEnabled,
                        onCheckedChange = { settingsViewModel.setMockGenresEnabled(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsItem(
                        title = "Force Daily Mix Regeneration",
                        subtitle = "Re-creates the daily mix playlist immediately.",
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_instant_mix_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        onClick = { playerViewModel.forceUpdateDailyMix() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(MiniPlayerHeight + 26.dp))
        }
    }

    // Diálogo para seleccionar directorios
    if (showDirectoryDialog) {
        DirectoryPickerDialog(
            directoryItems = directoryItems,
            isLoading = uiState.isLoadingDirectories,
            onDismiss = { showDirectoryDialog = false },
            onItemToggle = { directoryItem ->
                settingsViewModel.toggleDirectoryAllowed(directoryItem)
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    leadingIcon: @Composable () -> Unit,
    trailingIcon: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                leadingIcon()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                trailingIcon()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorItem(
    label: String,
    description: String,
    options: Map<String, String>,
    selectedKey: String,
    onSelectionChanged: (String) -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { expanded = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                leadingIcon()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = selectedOption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Abrir menú",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    // Dropdown menu con estilo mejorado
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            sheetState = rememberModalBottomSheetState(),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                options.forEach { (key, name) ->
                    val isSelected = key == selectedKey

                    Surface(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelectionChanged(key)
                                expanded = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Seleccionado",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryPickerDialog(
    directoryItems: ImmutableList<DirectoryItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onItemToggle: (DirectoryItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Carpetas de Música",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

//                HorizontalDivider(
//                    modifier = Modifier.padding(horizontal = 24.dp),
//                    color = MaterialTheme.colorScheme.outlineVariant
//                )

                // Contenido
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Escaneando carpetas...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (directoryItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No se encontraron carpetas con archivos de audio",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(directoryItems, key = { it.path }) { item ->
                                DirectoryItemCard(
                                    directoryItem = item,
                                    onToggle = { onItemToggle(item) }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Aceptar")
                    }
                }
            }
        }
    }
}

@Composable
fun DirectoryItemCard(
    directoryItem: DirectoryItem,
    onToggle: () -> Unit
) {
    val checkedState = remember { mutableStateOf(directoryItem.isAllowed) }

    // Actualizar el estado local cuando cambia el directoryItem
    LaunchedEffect(directoryItem) {
        checkedState.value = directoryItem.isAllowed
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (checkedState.value)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (checkedState.value)
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                checkedState.value = !checkedState.value
                onToggle()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono de carpeta
            Icon(
                imageVector = if (checkedState.value) Icons.Filled.Folder else Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (checkedState.value)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Nombre de la carpeta
            Text(
                text = directoryItem.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checkedState.value)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Checkbox
            Checkbox(
                checked = checkedState.value,
                onCheckedChange = {
                    checkedState.value = it
                    onToggle()
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                leadingIcon()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiApiKeyItem(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    title: String,
    subtitle: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.gemini_ai),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_key_vertical_24),
                        contentDescription = null,
                        //modifier = Modifier.size(24.dp) // Adjusted size
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}