package com.sanelink.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sanelink.app.R

class SaneLinkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (isLocked) {
            unlockAndRun {
                launchCleanActivity()
            }
        } else {
            launchCleanActivity()
        }
    }

    /**
     * Opens the invisible clipboard reader (it needs focus to read on Android 10+), which
     * cleans the clipboard and closes again. The main UI is never opened or closed.
     */
    private fun launchCleanActivity() {
        val intent = ClipboardReaderActivity.createIntent(this, manual = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapseCompat(intent)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // The Intent overload is the only one before API 34
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}
