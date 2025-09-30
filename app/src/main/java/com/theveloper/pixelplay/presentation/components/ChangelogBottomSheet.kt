package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@Composable
fun ChangelogBottomSheet(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 32.dp, top = 16.dp, start = 24.dp, end = 24.dp),
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VersionChanges(
                    versionNumber = "0.1.5-beta",
                    changesTitle = "Second beta Build",
                    changes = " ✸ Minor visual changes \n ✸ Fixed navigation bar padding \n ✸ Fixed favorite button not working \n ✸ Improved directory picker \n ✸ Minor UI Improvements \n ✸ Added haptic feedback and M3E animations for player \n" +
                            " ✸ Fixed critical database issues \n ✸ Improved different file types support"
                )
                HorizontalDivider(
                    modifier = Modifier
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                VersionChanges(
                    versionNumber = "0.1.0-beta",
                    changesTitle = "First Beta build.",
                    changes = ""
                )
            }
        }
    }
}

@Composable
fun VersionChanges(
    versionNumber: String,
    changesTitle: String,
    changes: String
){
    Column(
        modifier = Modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VersionBadge(versionNumber = versionNumber)
        Spacer(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth()
        )
        Text(
            modifier = Modifier.padding(start = 2.dp),
            text = changesTitle,
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (changes.isNotEmpty()){
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = changes,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
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
            text = versionNumber
        )
    }
}