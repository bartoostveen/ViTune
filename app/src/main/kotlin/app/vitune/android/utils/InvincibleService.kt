package app.vitune.android.utils

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import app.vitune.core.ui.utils.isAtLeastAndroid12

// https://stackoverflow.com/q/53502244/16885569
// I found four ways to make the system not kill the stopped foreground service: e.g. when
// the player is paused:
// 1 - Use the solution below - hacky;
// 2 - Do not call stopForeground but provide a button to dismiss the notification - bad UX;
// 3 - Lower the targetSdk (e.g. to 23) - security concerns;
// 4 - Host the service in a separate process - overkill and pathetic.
abstract class InvincibleService : Service() {
    protected val handler = Handler(Looper.getMainLooper())

    protected abstract val isInvincibilityEnabled: Boolean
    protected abstract val notificationId: Int

    private var invincibility: Invincibility? = null

    private val isAllowedToStartForegroundServices: Boolean
        get() = !isAtLeastAndroid12 || isIgnoringBatteryOptimizations

    override fun onBind(intent: Intent?): Binder? {
        invincibility?.stop()
        invincibility = null
        return null
    }

    override fun onRebind(intent: Intent?) {
        invincibility?.stop()
        invincibility = null
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (isInvincibilityEnabled && isAllowedToStartForegroundServices)
            invincibility = Invincibility()
        return true
    }

    override fun onDestroy() {
        invincibility?.stop()
        invincibility = null
        super.onDestroy()
    }

    protected fun makeInvincible(isInvincible: Boolean = true) {
        if (isInvincible) invincibility?.start() else invincibility?.stop()
    }

    protected abstract fun shouldBeInvincible(): Boolean

    /**
     * Should strictly be called on the main thread!
     */
    protected abstract fun startForeground()

    private inner class Invincibility : BroadcastReceiver(), Runnable {
        private var isStarted = false
        private val intervalMs = 30_000L

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> handler.post(this)

                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(this)
                    startForeground()
                }
            }
        }

        @Synchronized
        fun start() {
            if (isStarted) return

            isStarted = true
            handler.postDelayed(this, intervalMs)

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                /* context  = */ this@InvincibleService,
                /* receiver = */ this,
                /* filter   = */ filter,
                /* flags    = */ ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        @Synchronized
        fun stop() {
            if (!isStarted) return

            handler.removeCallbacks(this)
            unregisterReceiver(this)
            isStarted = false
        }

        override fun run() {
            if (!shouldBeInvincible() || !isAllowedToStartForegroundServices) return

            startForeground()
            @Suppress("DEPRECATION")
            stopForeground(false)

            handler.postDelayed(this, intervalMs)
        }
    }
}
