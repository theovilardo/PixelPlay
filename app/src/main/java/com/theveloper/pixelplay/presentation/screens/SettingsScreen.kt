package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    paddingValues: PaddingValues,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    // Recopilar el estado de la UI del ViewModel
    val uiState by settingsViewModel.uiState.collectAsState()
    // Estado para controlar la visibilidad del diálogo de directorios
    var showDirectoryDialog by remember { mutableStateOf(false) }

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
//                        style = MaterialTheme.typography.headlineMedium.copy(
//                            fontWeight = FontWeight.SemiBold
//                        )
                    )
                },
                navigationIcon = {
                    if (navController.previousBackStackEntry != null) {
                        FilledIconButton(
                            onClick = { navController.popBackStack() },
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
                    }
                },
//                colors = TopAppBarDefaults.largeTopAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
//                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
//                ),
                //scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            )
        },
        modifier = Modifier
            //.padding(paddingValues)
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset.toPx()
                }
                .verticalScroll(scrollState)
        ) {
            // Sección de gestión de música
            SettingsSection(
                title = "Gestión de Música",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                SettingsCard {
                    SettingsItem(
                        title = "Carpetas de Música Permitidas",
                        subtitle = "Selecciona qué carpetas incluir en tu biblioteca.",
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección de apariencia
            SettingsSection(
                title = "Apariencia",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                SettingsCard {
                    // Selector para Tema Global
                    ThemeSelectorItem(
                        label = "Tema de la Aplicación",
                        description = "Define la apariencia general de PixelPlay.",
                        options = mapOf(
                            ThemePreference.DEFAULT to "PixelPlay Predeterminado",
                            ThemePreference.DYNAMIC to "Dinámico (Sistema)",
                            ThemePreference.ALBUM_ART to "Basado en Carátula"
                        ),
                        selectedKey = uiState.globalThemePreference,
                        onSelectionChanged = { settingsViewModel.setGlobalThemePreference(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Style,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )

                    // Selector para Tema del Reproductor
                    ThemeSelectorItem(
                        label = "Tema del Reproductor",
                        description = "Personaliza cómo se ve el reproductor de música.",
                        options = mapOf(
                            ThemePreference.GLOBAL to "Seguir Tema de Aplicación",
                            ThemePreference.ALBUM_ART to "Basado en Carátula",
                            ThemePreference.DEFAULT to "PixelPlay Predeterminado"
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
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Diálogo para seleccionar directorios
    if (showDirectoryDialog) {
        DirectoryPickerDialog(
            directoryItems = uiState.directoryItems,
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
fun SettingsCard(
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            content()
        }
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
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
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
                    .padding(end = 8.dp)
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
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
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
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
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
                            imageVector = Icons.Rounded.ArrowDropDown,
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
    directoryItems: List<DirectoryItem>,
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

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

                // Footer
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

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

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun SettingsScreen(
//    navController: NavController,
//    paddingValues: PaddingValues,
//    settingsViewModel: SettingsViewModel = hiltViewModel()
//) {
//    // Recopilar el estado de la UI del ViewModel
//    val uiState by settingsViewModel.uiState.collectAsState()
//    // Estado para controlar la visibilidad del diálogo de directorios
//    var showDirectoryDialog by remember { mutableStateOf(false) }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Ajustes") },
//                navigationIcon = {
//                    // Mostrar botón de atrás solo si hay una pantalla anterior en el stack
//                    if (navController.previousBackStackEntry != null) {
//                        IconButton(onClick = { navController.popBackStack() }) {
//                            Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = "Volver")
//                        }
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.surfaceVariant // Usar un color del tema
//                )
//            )
//        },
//        // Aplicar el padding del Scaffold a todo el contenido
//        modifier = Modifier.padding(paddingValues)
//    ) { innerPadding ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding) // Aplicar el padding del Scaffold
//                .padding(16.dp) // Padding adicional para el contenido interno
//        ) {
//            Text(
//                "Gestión de Música",
//                style = MaterialTheme.typography.titleMedium,
//                modifier = Modifier.padding(bottom = 8.dp)
//            )
//            // Item para abrir el diálogo de selección de carpetas
//            SettingsItem(
//                title = "Carpetas de Música Permitidas",
//                subtitle = "Selecciona qué carpetas incluir en tu biblioteca.",
//                onClick = { showDirectoryDialog = true } // Mostrar el diálogo al hacer clic
//            )
//            // Aquí se podrían añadir más opciones de configuración en el futuro
//            Divider() // Separador visual
//            Spacer(modifier = Modifier.height(24.dp)) // Espacio vertical
//
//            Text("Apariencia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
//
//            // Selector para Tema Global
//            ThemeSelectorDropDown(
//                label = "Tema de la Aplicación",
//                options = mapOf(
//                    ThemePreference.DEFAULT to "PixelPlay Predeterminado",
//                    ThemePreference.DYNAMIC to "Dinámico (Sistema)", // Nota: La implementación de Dynamic puede variar
//                    ThemePreference.ALBUM_ART to "Basado en Carátula" // Solo aplica si el tema global permite carátula
//                ),
//                selectedKey = uiState.globalThemePreference,
//                onSelectionChanged = { settingsViewModel.setGlobalThemePreference(it) } // Llamar al ViewModel al cambiar
//            )
//            Spacer(modifier = Modifier.height(8.dp)) // Espacio vertical
//            Divider() // Separador visual
//            Spacer(modifier = Modifier.height(16.dp)) // Espacio vertical
//
//            // Selector para Tema del Reproductor
//            ThemeSelectorDropDown(
//                label = "Tema del Reproductor",
//                options = mapOf(
//                    ThemePreference.GLOBAL to "Seguir Tema de Aplicación",
//                    ThemePreference.ALBUM_ART to "Basado en Carátula",
//                    ThemePreference.DEFAULT to "PixelPlay Predeterminado"
//                ),
//                selectedKey = uiState.playerThemePreference,
//                onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) } // Llamar al ViewModel al cambiar
//            )
//            Divider() // Separador visual
//        }
//    }
//
//    // Diálogo para seleccionar directorios
//    if (showDirectoryDialog) {
//        DirectoryPickerDialog(
//            directoryItems = uiState.directoryItems, // Pasar la lista del estado del ViewModel
//            isLoading = uiState.isLoading, // Pasar el estado de carga del ViewModel
//            onDismiss = { showDirectoryDialog = false }, // Ocultar diálogo al cancelar
//            // Eliminamos onSave, ya que el guardado ocurre en onItemToggle
//            onItemToggle = { directoryItem ->
//                // Llamar a la función toggleDirectoryAllowed del ViewModel, pasando el DirectoryItem
//                settingsViewModel.toggleDirectoryAllowed(directoryItem)
//            }
//        )
//    }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ThemeSelectorDropDown(
//    label: String,
//    options: Map<String, String>, // Clave -> Nombre visible
//    selectedKey: String,
//    onSelectionChanged: (String) -> Unit
//) {
//    var expanded by remember { mutableStateOf(false) } // Estado para controlar si el menú desplegable está expandido
//
//    Column(modifier = Modifier.padding(vertical = 8.dp)) {
//        Text(label, style = MaterialTheme.typography.titleMedium) // Etiqueta del selector
//        Spacer(modifier = Modifier.height(4.dp)) // Espacio vertical
//        ExposedDropdownMenuBox(
//            expanded = expanded,
//            onExpandedChange = { expanded = !expanded }, // Alternar estado expandido
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            OutlinedTextField(
//                value = options[selectedKey] ?: selectedKey, // Mostrar el nombre visible de la opción seleccionada
//                onValueChange = {}, // No editable directamente
//                readOnly = true, // Hacer el campo de texto de solo lectura
//                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, // Icono de flecha
//                modifier = Modifier.menuAnchor().fillMaxWidth(), // Ancla para el menú
//                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors() // Colores del campo de texto
//            )
//            // Menú desplegable con las opciones
//            ExposedDropdownMenu(
//                expanded = expanded,
//                onDismissRequest = { expanded = false } // Ocultar menú al descartar
//            ) {
//                options.forEach { (key, name) ->
//                    DropdownMenuItem(
//                        text = { Text(name) }, // Texto del item del menú
//                        onClick = {
//                            onSelectionChanged(key) // Llamar al callback con la clave seleccionada
//                            expanded = false // Ocultar menú al seleccionar
//                        }
//                    )
//                }
//            }
//        }
//        // Descripción adicional para el selector
//        Text(
//            text = when(label){
//                "Tema de la Aplicación" -> "Define la apariencia general de PixelPlay."
//                "Tema del Reproductor" -> "Personaliza cómo se ve el reproductor de música."
//                else -> "" // Sin descripción si no coincide
//            },
//            style = MaterialTheme.typography.bodySmall,
//            color = MaterialTheme.colorScheme.onSurfaceVariant,
//            modifier = Modifier.padding(top = 4.dp)
//        )
//    }
//}
//
//@Composable
//fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clickable(onClick = onClick) // Hacer la columna clickeable
//            .padding(vertical = 12.dp) // Padding vertical
//    ) {
//        Text(title, style = MaterialTheme.typography.bodyLarge) // Título del item
//        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) // Subtítulo
//    }
//    Divider() // Separador visual
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun DirectoryPickerDialog(
//    directoryItems: List<DirectoryItem>, // Lista de directorios con su estado de permitido
//    isLoading: Boolean, // Estado de carga
//    onDismiss: () -> Unit, // Callback al descartar el diálogo
//    // Eliminamos onSave
//    onItemToggle: (DirectoryItem) -> Unit // Callback al alternar un item, ahora recibe DirectoryItem
//) {
//    AlertDialog(
//        onDismissRequest = onDismiss, // Manejar descarte
//        title = { Text("Seleccionar Carpetas Permitidas") }, // Título del diálogo
//        text = {
//            // Mostrar indicador de carga si está cargando
//            if (isLoading) {
//                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
//                    CircularProgressIndicator()
//                }
//                // Mostrar mensaje si no se encontraron directorios y no está cargando
//            } else if (directoryItems.isEmpty()) {
//                Text("No se encontraron carpetas con archivos de audio o no se pudo acceder a ellas.")
//            } else {
//                // Lista perezosa para mostrar los directorios
//                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) { // Limitar altura para evitar overflow
//                    items(directoryItems, key = { it.path }) { item ->
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .clickable { onItemToggle(item) } // Hacer la fila clickeable y pasar el item completo
//                                .padding(vertical = 8.dp),
//                            verticalAlignment = Alignment.CenterVertically // Alinear verticalmente
//                        ) {
//                            Checkbox(
//                                checked = item.isAllowed, // Estado del checkbox
//                                onCheckedChange = { onItemToggle(item) } // Llamar al callback al cambiar el checkbox
//                            )
//                            Spacer(Modifier.width(16.dp)) // Espacio horizontal
//                            Text(item.displayName, style = MaterialTheme.typography.bodyMedium) // Nombre del directorio
//                        }
//                    }
//                }
//            }
//        },
//        // Botón de confirmación (Guardar)
//        confirmButton = {
//            // El botón Guardar ya no necesita llamar a saveDirectoryPreferences,
//            // ya que el guardado ocurre en cada toggle.
//            // Podríamos eliminar este botón si no hay otras acciones de "guardar" en el diálogo,
//            // o mantenerlo si el usuario espera una confirmación explícita, aunque la acción ya se realizó.
//            // Lo mantendremos por ahora, pero su `onClick` no hace nada relacionado con guardar.
//            Button(onClick = onDismiss, enabled = !isLoading) { // Simplemente cierra el diálogo
//                Text("Aceptar") // Cambiado el texto para reflejar que no guarda, solo confirma
//            }
//        },
//        // Botón de descarte (Cancelar)
//        dismissButton = {
//            TextButton(onClick = onDismiss) { // Cierra el diálogo
//                Text("Cancelar")
//            }
//        },
//        modifier = Modifier.padding(16.dp) // Padding para el diálogo
//    )
//}

// Nota: DirectoryItem debe ser una data class definida en data.model
// data class DirectoryItem(val path: String, val displayName: String, val isAllowed: Boolean)
// La función para generar displayName a partir del path debe estar en utils o DirectoryItem

// Nota: ThemePreference debe ser un objeto o enum con las constantes de String
// object ThemePreference { const val DEFAULT = "default"; const val DYNAMIC = "dynamic"; const val ALBUM_ART = "album_art"; const val GLOBAL = "global" }