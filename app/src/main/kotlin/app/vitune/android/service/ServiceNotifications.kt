package app.vitune.android.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.media3.common.util.NotificationUtil.Importance
import androidx.media3.common.util.UnstableApi
import app.vitune.android.R
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.random.Random
import kotlin.reflect.KProperty

abstract class NotificationChannels {
    private val handler = Handler(Looper.getMainLooper())

    @OptIn(UnstableApi::class)
    inner class Channel internal constructor(
        val id: String,
        @param:StringRes
        val description: Int,
        val notificationId: Int? = null,
        val importance: @Importance Int,
        val options: NotificationChannelCompat.Builder.() -> NotificationChannelCompat.Builder
    ) {
        private val Context.notificationManager
            get() = getSystemService<NotificationManager>()
                ?: error("No NotificationManager available")

        private fun createNotification(
            context: Context,
            notification: NotificationCompat.Builder.() -> NotificationCompat.Builder
        ): Pair<Int, Notification> =
            (notificationId ?: randomNotificationId()) to NotificationCompat.Builder(context, id)
                .let {
                    if (notificationId == null) it else it.setOnlyAlertOnce(false)
                }
                .run(notification)
                .build()

        fun upsertChannel(context: Context) = NotificationManagerCompat
            .from(context)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(id, importance)
                    .setName(context.getString(description))
                    .run(options)
                    .build()
            )

        fun sendNotification(
            context: Context,
            notification: NotificationCompat.Builder.() -> NotificationCompat.Builder
        ) = runCatching {
            handler.post {
                val manager = context.notificationManager
                upsertChannel(context)
                val (id, notif) = createNotification(context, notification)
                manager.notify(id, notif)
            }
        }

        context(s: Service)
        fun startForeground(
            context: Context,
            notification: NotificationCompat.Builder.() -> NotificationCompat.Builder
        ) = runCatching {
            // Synchronous: the FGS start timeout runs from startForegroundService()
            upsertChannel(context)
            val (id, notif) = createNotification(context, notification)
            s.startForeground(id, notif)
        }

        fun cancel(
            context: Context,
            notificationId: Int? = null
        ) = runCatching {
            handler.post {
                context.notificationManager.cancel((this.notificationId ?: notificationId)!!)
            }
        }
    }

    private val mutableChannels = mutableListOf<Channel>()
    private val index = AtomicInteger(1001)

    private fun randomNotificationId(): Int {
        var random = Random.nextInt().absoluteValue
        while (random in 1001..2001) {
            random = Random.nextInt().absoluteValue
        }
        return random
    }

    context(application: Application)
    fun createAll() = handler.post {
        mutableChannels.forEach { it.upsertChannel(application) }
    }

    @OptIn(UnstableApi::class)
    fun channel(
        name: String? = null,
        @StringRes
        description: Int,
        importance: @Importance Int,
        singleNotification: Boolean,
        options: NotificationChannelCompat.Builder.() -> NotificationChannelCompat.Builder = { this }
    ) = readOnlyProvider<NotificationChannels, Channel> { _, property ->
        val channel = Channel(
            id = "${name?.lowercase() ?: property.name.lowercase()}_channel_id",
            description = description,
            notificationId = if (singleNotification) index.getAndIncrement().also {
                if (it > 2001) error("More than 1000 unique notifications created!")
            } else null,
            importance = importance,
            options = options
        )
        mutableChannels += channel
        { _, _ -> channel }
    }
}

inline fun <ThisRef, Return> readOnlyProvider(
    crossinline provide: (
        thisRef: ThisRef,
        property: KProperty<*>
    ) -> (thisRef: ThisRef, property: KProperty<*>) -> Return
) = PropertyDelegateProvider<ThisRef, ReadOnlyProperty<ThisRef, Return>> { thisRef, property ->
    val provider = provide(thisRef, property)
    ReadOnlyProperty { innerThisRef, innerProperty -> provider(innerThisRef, innerProperty) }
}

object ServiceNotifications : NotificationChannels() {
    val default by channel(
        description = R.string.now_playing,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        singleNotification = true
    )

    val sleepTimer by channel(
        name = "sleep_timer",
        description = R.string.sleep_timer,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        singleNotification = true
    )

    val download by channel(
        description = R.string.pre_cache,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        singleNotification = true
    )

    val version by channel(
        description = R.string.version_check,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        singleNotification = true
    ) {
        setLightsEnabled(true).setVibrationEnabled(true)
    }

    val autoSkip by channel(
        name = "autoskip",
        description = R.string.skip_on_error,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        singleNotification = true
    ) {
        setLightsEnabled(true).setVibrationEnabled(true)
    }
}
