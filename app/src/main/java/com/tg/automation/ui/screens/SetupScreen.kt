package com.tg.automation.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tg.automation.data.MacroRepository
import com.tg.automation.data.Prefs
import com.tg.automation.engine.Permissions
import com.tg.automation.model.Requirement
import com.tg.automation.service.AutomationService

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    val macros by MacroRepository.macros.collectAsStateWithLifecycle()
    val master by Prefs.master.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val used = remember(macros) { Permissions.usedBy(macros) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        AutomationService.sync(ctx)
    }
    // Wichtige zuerst: die von Makros benötigten, dann der Rest.
    val ordered = remember(used) {
        Requirement.entries.sortedBy { if (it in used) 0 else if (it == Requirement.BATTERY_OPTIMIZATION) 1 else 2 }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automationen aktiv", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (master) "Makros reagieren auf Ereignisse." else "Alles pausiert – kein Hintergrund-Dienst, keine Wecker.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = master, onCheckedChange = { Prefs.setMaster(it) })
                }
            }
        }
        item { BatteryInfoCard() }
        item {
            Text(
                "Berechtigungen",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Du musst nur erlauben, was deine Makros wirklich brauchen (markiert mit „Benötigt“).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(ordered, key = { it.name }) { r ->
            val granted = remember(tick, r) { Permissions.isGranted(ctx, r) }
            RequirementCard(
                requirement = r,
                granted = granted,
                needed = r in used,
                onGrant = {
                    val runtime = Permissions.runtimePermissions(ctx, r)
                    if (runtime != null) launcher.launch(runtime) else open(ctx, Permissions.settingsIntent(ctx, r))
                },
            )
        }
        if (Build.VERSION.SDK_INT >= 33) {
            item { RestrictedSettingsCard() }
        }
    }
}

@Composable
private fun BatteryInfoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryChargingFull, null)
                Spacer(Modifier.width(8.dp))
                Text("So schont die App deinen Akku", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "• Keine Dauerabfragen: Die App wartet auf Signale von Android, statt ständig nachzusehen.\n" +
                    "• Uhrzeiten laufen über den System-Wecker – die App schläft bis dahin komplett.\n" +
                    "• Der Hintergrund-Dienst startet nur, wenn ein Makro Systemereignisse braucht (z. B. Bildschirm, WLAN), " +
                    "und hört dann nur auf genau diese.\n" +
                    "• Kein GPS, keine Sensoren im Dauerbetrieb.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RequirementCard(requirement: Requirement, granted: Boolean, needed: Boolean, onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (needed && !granted) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (granted) "Erlaubt" else "Nicht erlaubt",
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(requirement.title, style = MaterialTheme.typography.titleSmall)
                if (needed) {
                    Text("Benötigt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    requirement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onGrant) { Text("Erlauben") }
            }
        }
    }
}

@Composable
private fun RestrictedSettingsCard() {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null)
                Spacer(Modifier.width(8.dp))
                Text("Bedienungshilfe ausgegraut?", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Bei Apps, die nicht aus dem Play Store kommen, sperrt Android manche Einstellungen. So entsperrst du sie:\n" +
                    "1. „App-Info öffnen“ tippen\n" +
                    "2. Oben rechts auf ⋮ tippen\n" +
                    "3. „Eingeschränkte Einstellungen zulassen“ wählen\n" +
                    "4. Danach Bedienungshilfe bzw. Benachrichtigungszugriff erneut aktivieren",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { open(ctx, Permissions.appDetails(ctx)) }) { Text("App-Info öffnen") }
        }
    }
}

private fun open(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        ctx.startActivity(Permissions.appDetails(ctx))
    } catch (e: SecurityException) {
        ctx.startActivity(Permissions.appDetails(ctx))
    }
}
