package org.wikitide.wikiportal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.LogEntry
import org.wikitide.wikiportal.util.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val entries by AppLog.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { AppLog.clear() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear logs")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
                Text(
                    "Nothing logged yet this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
            reverseLayout = true,
        ) {
            items(entries.asReversed()) { entry ->
                LogRow(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${formatLogTime(entry.timestampEpochMillis)}  ${entry.level.name}  ${entry.tag}",
            style = MaterialTheme.typography.labelSmall,
            color = levelColor(entry.level),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun levelColor(level: LogLevel) = when (level) {
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatLogTime(epochMillis: Long): String {
    val totalSeconds = epochMillis / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return "${hours.pad()}:${minutes.pad()}:${seconds.pad()}"
}

private fun Long.pad(): String = toString().padStart(2, '0')
