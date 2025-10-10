package com.theveloper.pixelplay.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

// Data class for a single changelog version
data class ChangelogVersion(
    val version: String,
    val date: String,
    val added: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val fixed: List<String> = emptyList()
)

// The changelog data
val changelog = listOf(
    ChangelogVersion(
        version = "0.2.0-beta",
        date = "2024-09-15",
        added = listOf(
            "Chromecast support for casting audio from your device.",
            "In-app changelog to keep you updated on the latest features.",
            "Support for .LRC files, both embedded and external.",
            "Offline lyrics support.",
            "Synchronized lyrics (synced with the song).",
            "New screen to view the full queue.",
            "Reorder and remove songs from the queue.",
            "Mini-player gestures (swipe down to close).",
            "Added more material animations.",
            "New settings to customize the look and feel.",
            "New settings to clear the cache."
        ),
        changed = listOf(
            "Complete redesign of the user interface.",
            "Complete redesign of the player.",
            "Performance improvements in the library.",
            "Improved application startup speed.",
            "The AI now provides better results."
        ),
        fixed = listOf(
            "Fixed various bugs in the tag editor.",
            "Fixed a bug where the playback notification was not clearing.",
            "Fixed several bugs that caused the app to crash."
        )
    )
)


@Composable
fun ChangelogBottomSheet(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val changelogUrl = "https://github.com/theovilardo/PixelPlay/blob/master/CHANGELOG.md"

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(bottom = 32.dp, top = 16.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Changelog",
                fontFamily = GoogleSansRounded,
                style = ExpTitleTypography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                items(changelog) { version ->
                    ChangelogVersionItem(version = version)
                }
            }
        }

        FloatingActionButton(
            onClick = { openUrl(context, changelogUrl) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.github),
                contentDescription = "View on GitHub"
            )
        }
    }
}

@Composable
fun ChangelogVersionItem(version: ChangelogVersion) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VersionBadge(versionNumber = version.version)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = version.date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (version.added.isNotEmpty()) {
            ChangelogCategory(title = "Added", items = version.added)
        }
        if (version.changed.isNotEmpty()) {
            ChangelogCategory(title = "Changed", items = version.changed)
        }
        if (version.fixed.isNotEmpty()) {
            ChangelogCategory(title = "Fixed", items = version.fixed)
        }
    }
}

@Composable
fun ChangelogCategory(title: String, items: List<String>) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun VersionBadge(
    versionNumber: String
){
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
    ) {
        Text(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            text = versionNumber,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = try { url.toUri() } catch (_: Throwable) { url.toUri() }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // As a last resort, do nothing; you could show a toast/snackbar from the caller if needed.
    }
}