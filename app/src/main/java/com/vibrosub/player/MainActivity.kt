package com.vibrosub.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vibrosub.player.player.MusicPlayerController
import com.vibrosub.player.ui.MainScreen
import com.vibrosub.player.ui.theme.VibroSubTheme

class MainActivity : ComponentActivity() {

    private lateinit var controller: MusicPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        controller = MusicPlayerController(this)

        handleIntent(intent)

        setContent {
            VibroSubTheme {
                MainScreen(controller = controller)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data: Uri? = intent.data
            if (data != null) {
                controller.loadUri(data)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            controller.release()
        }
    }
}
