package com.tg.automation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tg.automation.data.LogRepository

@Composable
fun LogScreen() {
    val entries by LogRepository.entries.collectAsStateWithLifecycle()
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Noch nichts passiert.\nHier siehst du, wann welches Makro gelaufen ist – und ob etwas schiefging.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries) { e ->
            ListItem(
                overlineContent = { Text(formatTime(e.time)) },
                headlineContent = { Text(e.macro) },
                supportingContent = { Text(e.message) },
                leadingContent = {
                    Icon(
                        if (e.error) Icons.Default.Error else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (e.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }
}
