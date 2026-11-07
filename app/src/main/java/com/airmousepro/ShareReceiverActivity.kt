package com.airmousepro

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.airmousepro.hid.HidController

/** Share any text to AirMouse Pro and it is typed on the connected computer. */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val hid = app.hid
        when {
            text.isNullOrEmpty() -> Toast.makeText(this, "Nothing to type", Toast.LENGTH_SHORT).show()
            hid.state != HidController.State.Connected ->
                Toast.makeText(this, "Not connected. Open AirMouse Pro and connect first.", Toast.LENGTH_LONG).show()
            else -> {
                // Typing continues in the app's own scope after this invisible screen closes.
                app.macros.typeText(text.take(2000))
                Toast.makeText(this, "Typing on ${hid.hostName}", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
