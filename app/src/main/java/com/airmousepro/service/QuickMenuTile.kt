package com.airmousepro.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.airmousepro.app
import com.airmousepro.appSettings

/** Quick Settings tile that turns the floating dial on or off without opening the app. */
class QuickMenuTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val s = appSettings
        s.quickMenu = !s.quickMenu
        if (s.quickMenu && !app.quickMenu.canDraw()) {
            Toast.makeText(this, "Allow display over other apps in AirMouse Pro → Settings → Quick menu", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (appSettings.quickMenu) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Quick dial"
        tile.updateTile()
    }
}
