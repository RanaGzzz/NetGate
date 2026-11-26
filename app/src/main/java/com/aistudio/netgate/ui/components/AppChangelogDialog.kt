package com.aistudio.netgate.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.netgate.BuildConfig
import com.aistudio.netgate.data.model.AppUpdateUiState
import com.aistudio.netgate.data.model.BundledChangelog
import com.aistudio.netgate.data.model.ChangelogEntry
import com.aistudio.netgate.data.model.ChangelogSection
import com.aistudio.netgate.data.model.GitHubReleaseInfo
import com.aistudio.netgate.data.model.formatDataBytes
import com.aistudio.netgate.ui.theme.EmeraldPrimary
import com.aistudio.netgate.ui.theme.WifiAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChangelogSheet(
    updateState: AppUpdateUiState,
    onCheckUpdates: () -> Unit,
    onCopiedChangelog: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val remoteEntries = updateState.recentReleases.map { rel ->
        rel.toChangelogEntry()
    }
    val combinedEntries = if (remoteEntries.isNotEmpty()) {
        val remoteVersions = remoteEntries.map { it.versionLabel.lowercase() }.toSet()
        remoteEntries + BundledChangelog.entries.filter {
            val cleanBundled = it.versionLabel.lowercase()
            remoteVersions.none { rv -> rv.contains(cleanBundled.removePrefix("netgate ")) }
        }
    } else {
        BundledChangelog.entries
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
        modifier = Modifier.testTag("changelog_sheet")
    ) {
        FrostedSheetContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                FrostedSheetDragHandle()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Changelog & Releases",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Installed: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val plainText = formatChangelogForCopy(combinedEntries)
                        copyToClipboard(context, "NetGate Changelog", plainText)
                        onCopiedChangelog("Copied changelog to clipboard")
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("changelog_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy changelog",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCheckUpdates,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("changelog_refresh_releases_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync GitHub Releases", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                updateState.latestRelease?.let { latest ->
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(latest.preferredOpenUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("changelog_open_latest_release_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open GitHub release",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(latest.tagName, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                combinedEntries.forEach { entry ->
                    ChangelogVersionCard(
                        entry = entry,
                        onOpenReleaseUrl = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ChangelogVersionCard(
    entry: ChangelogEntry,
    onOpenReleaseUrl: (String) -> Unit
) {
    val accent = if (entry.isRemoteLive) EmeraldPrimary else WifiAccent
    FrostedSurface(
        shape = RoundedCornerShape(18.dp),
        borderAccent = if (entry.isRemoteLive) EmeraldPrimary.copy(alpha = 0.45f) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entry.versionLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = if (entry.isRemoteLive) "GitHub Live" else "Bundled",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (entry.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                entry.releaseUrl?.let { url ->
                    OutlinedButton(
                        onClick = { onOpenReleaseUrl(url) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = "Open release ${entry.versionLabel}",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (entry.sections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                entry.sections.forEachIndexed { idx, section ->
                    if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                    if (section.heading.isNotBlank()) {
                        Text(
                            text = section.heading,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    section.bullets.forEach { bullet ->
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun GitHubReleaseInfo.toChangelogEntry(): ChangelogEntry {
    val subParts = buildList {
        if (publishedAt.isNotBlank()) add(publishedAt)
        if (!apkName.isNullOrBlank()) {
            val sizeLabel = if (apkSizeBytes > 0L) " (${formatDataBytes(apkSizeBytes)})" else ""
            add("$apkName$sizeLabel")
        }
        if (isPrerelease) add("Pre-release")
    }
    val sections = parseMarkdownBodyToSections(body)
    return ChangelogEntry(
        versionLabel = title.ifBlank { tagName },
        subtitle = subParts.joinToString(" • "),
        sections = sections,
        isRemoteLive = true,
        releaseUrl = preferredOpenUrl
    )
}

internal fun parseMarkdownBodyToSections(body: String): List<ChangelogSection> {
    val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) {
        return listOf(ChangelogSection("Release Notes", listOf("Published on GitHub Releases.")))
    }
    val sections = mutableListOf<ChangelogSection>()
    var currentHeading = "Release Notes"
    val currentBullets = mutableListOf<String>()

    fun flushSection() {
        if (currentBullets.isNotEmpty()) {
            sections += ChangelogSection(currentHeading, currentBullets.toList())
            currentBullets.clear()
        }
    }

    lines.forEach { line ->
        when {
            line.startsWith("#") -> {
                flushSection()
                currentHeading = line.trimStart('#', ' ')
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                currentBullets += line.drop(2).replace("**", "").trim()
            }
            else -> {
                currentBullets += line.replace("**", "").trim()
            }
        }
    }
    flushSection()
    return sections
}

private fun formatChangelogForCopy(entries: List<ChangelogEntry>): String {
    return buildString {
        entries.forEach { entry ->
            appendLine("# ${entry.versionLabel}")
            if (entry.subtitle.isNotBlank()) appendLine(entry.subtitle)
            entry.sections.forEach { section ->
                if (section.heading.isNotBlank()) appendLine("## ${section.heading}")
                section.bullets.forEach { appendLine("- $it") }
            }
            appendLine()
        }
    }.trim()
}
