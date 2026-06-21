package com.junnz.phone.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.junnz.phone.matching.ContextEventDispatcher
import com.junnz.shared.domain.ContextEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import javax.inject.Inject

/**
 * Feeds posted-notification text into the [ContextEventDispatcher] as an
 * [ContextEvent.AppNotification]. The shared MatchingEngine then matches it
 * against app-context triggers (by package) and semantic triggers (by embedding
 * similarity of the notification phrase). The dispatcher short-circuits cheaply
 * when no context reminders are active, so this stays light.
 */
@AndroidEntryPoint
class JunnzNotificationListener : NotificationListenerService() {

    @Inject lateinit var contextDispatcher: ContextEventDispatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // ignore our own notifications

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val phrase = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ")
        if (phrase.isBlank()) return

        Timber.v("Notification context: ${sbn.packageName}")
        scope.launch {
            contextDispatcher.dispatch(
                ContextEvent.AppNotification(
                    packageName = sbn.packageName,
                    contextPhrase = phrase,
                    occurredAt = Clock.System.now(),
                ),
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
