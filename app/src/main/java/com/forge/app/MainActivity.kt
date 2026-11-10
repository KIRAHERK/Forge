package com.forge.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.forge.app.notify.Notifier
import com.forge.app.notify.Scheduler
import com.forge.app.ui.AppRoot
import com.forge.app.ui.theme.ForgeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val routeRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Graph.init(this)
        routeRequest.value = intent?.getStringExtra(Notifier.EXTRA_ROUTE)
        setContent {
            ForgeTheme {
                AppRoot(routeRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let { routeRequest.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Time passes while the app is closed. Settle misses and overdue penalties on every open.
        // The app shows them itself, so no notification here.
        lifecycleScope.launch {
            runCatching {
                Graph.repo.evaluate()
                Scheduler.rescheduleAll(this@MainActivity)
            }
        }
    }
}
