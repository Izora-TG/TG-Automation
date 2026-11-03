package com.tg.automation.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tg.automation.data.Prefs

/** Schnelleinstellungs-Kachel: alle Automationen pausieren / fortsetzen. */
class MasterTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        Prefs.setMaster(!Prefs.masterEnabled)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val on = Prefs.masterEnabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Automationen"
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "Aktiv" else "Pausiert"
        tile.updateTile()
    }
}
