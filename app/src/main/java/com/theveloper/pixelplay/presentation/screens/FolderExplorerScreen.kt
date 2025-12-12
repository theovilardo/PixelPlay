package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.theveloper.pixelplay.presentation.components.FileExplorerContent
import com.theveloper.pixelplay.presentation.viewmodel.DirectoryEntry
import java.io.File

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun FolderExplorerScreen(
//    fromSetup: Boolean,
//    onClose: () -> Unit,
//    onDone: () -> Unit,
//    currentPath: File,
//    directoryChildren: List<DirectoryEntry>,
//    smartViewEnabled: Boolean,
//    isLoading: Boolean,
//    isAtRoot: Boolean,
//    rootDirectory: File,
//    onNavigateTo: (File) -> Unit,
//    onNavigateUp: () -> Unit,
//    onNavigateHome: () -> Unit,
//    onToggleAllowed: (File) -> Unit,
//    onRefresh: () -> Unit,
//    onSmartViewToggle: (Boolean) -> Unit
//) {
//    BackHandler(enabled = true) {
//        if (isAtRoot || smartViewEnabled) {
//            onClose()
//        } else {
//            onNavigateUp()
//        }
//    }
//
//    LaunchedEffect(fromSetup) {
//        if (fromSetup && isAtRoot) {
//            onNavigateHome()
//        }
//    }
//
//    Scaffold(
//        modifier = Modifier.fillMaxSize(),
//        topBar = {
//            TopAppBar(
//                title = {
//                    Text(
//                        text = if (fromSetup) "Setup: Choose music folders" else "Music folders",
//                        style = MaterialTheme.typography.titleMedium
//                    )
//                },
//                navigationIcon = {
//                    IconButton(onClick = {
//                        if (isAtRoot || smartViewEnabled) onClose() else onNavigateUp()
//                    }) {
//                        Icon(
//                            imageVector = Icons.Rounded.Close,
//                            contentDescription = "Close"
//                        )
//                    }
//                },
//                actions = {
//                    IconButton(onClick = onDone) {
//                        Icon(
//                            imageVector = Icons.Rounded.Check,
//                            contentDescription = "Done"
//                        )
//                    }
//                }
//            )
//        }
//    ) { padding ->
//        FileExplorerContent(
//            currentPath = currentPath,
//            directoryChildren = directoryChildren,
//            smartViewEnabled = smartViewEnabled,
//            isLoading = isLoading,
//            isAtRoot = isAtRoot,
//            rootDirectory = rootDirectory,
//            onNavigateTo = onNavigateTo,
//            onNavigateUp = onNavigateUp,
//            onNavigateHome = onNavigateHome,
//            onToggleAllowed = onToggleAllowed,
//            onRefresh = onRefresh,
//            onSmartViewToggle = onSmartViewToggle,
//            onDone = onDone,
//            title = if (fromSetup) "Select folders for setup" else "Select music folders",
//            leadingContent = null,
//            modifier = Modifier.fillMaxSize().padding(padding)
//        )
//    }
//}
