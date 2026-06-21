package com.junnz.phone.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.Trigger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a [Trigger.GeofenceTrigger] label to coordinates (via [Geocoder])
 * and registers/removes circular geofences through the Play Services
 * [com.google.android.gms.location.GeofencingClient].
 *
 * One geofence per reminder; its requestId is the reminder id, so the
 * [GeofenceBroadcastReceiver] can map a transition straight back to a reminder.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val geofencingClient = LocationServices.getGeofencingClient(context)
    private val geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    /**
     * Geocode any unresolved (lat/lng == 0,0) geofence labels so the resolved
     * coordinates get persisted with the reminder. Returns the reminder
     * unchanged if there's nothing to resolve.
     */
    suspend fun resolveGeofences(reminder: Reminder): Reminder {
        if (reminder.triggers.none { it is Trigger.GeofenceTrigger }) return reminder
        val updated = reminder.triggers.map { trigger ->
            if (trigger is Trigger.GeofenceTrigger &&
                trigger.lat == 0.0 && trigger.lng == 0.0 &&
                trigger.label.isNotBlank()
            ) {
                val coords = geocode(trigger.label)
                if (coords != null) trigger.copy(lat = coords.first, lng = coords.second) else trigger
            } else {
                trigger
            }
        }
        return reminder.copy(triggers = updated)
    }

    @SuppressLint("MissingPermission")
    suspend fun register(reminder: Reminder) {
        if (!hasLocationPermission()) {
            Timber.d("GeofenceManager: location permission missing, skip ${reminder.id}")
            return
        }
        val trigger = reminder.triggers
            .filterIsInstance<Trigger.GeofenceTrigger>()
            .firstOrNull { it.lat != 0.0 || it.lng != 0.0 } ?: return

        var transitions = 0
        if (trigger.onEnter) transitions = transitions or Geofence.GEOFENCE_TRANSITION_ENTER
        if (trigger.onDwell) transitions = transitions or Geofence.GEOFENCE_TRANSITION_DWELL
        if (transitions == 0) transitions = Geofence.GEOFENCE_TRANSITION_ENTER

        val geofence = Geofence.Builder()
            .setRequestId(reminder.id)
            .setCircularRegion(trigger.lat, trigger.lng, trigger.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)
            .apply { if (trigger.onDwell) setLoiteringDelay(LOITERING_DELAY_MS) }
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent()).await()
            Timber.d("GeofenceManager: registered ${reminder.id} @ ${trigger.lat},${trigger.lng} r=${trigger.radiusMeters}")
        } catch (e: Exception) {
            Timber.e(e, "GeofenceManager: failed to register ${reminder.id}")
        }
    }

    suspend fun remove(reminderId: String) {
        try {
            geofencingClient.removeGeofences(listOf(reminderId)).await()
        } catch (e: Exception) {
            Timber.w(e, "GeofenceManager: failed to remove $reminderId")
        }
    }

    private suspend fun geocode(label: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(label, 1)
            results?.firstOrNull()?.let { it.latitude to it.longitude }
        } catch (e: IOException) {
            Timber.w(e, "GeofenceManager: geocoding failed for '$label'")
            null
        }
    }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // Geofencing requires a MUTABLE PendingIntent so Play Services can attach results.
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    companion object {
        private const val GEOFENCE_REQUEST_CODE = 9001
        private const val LOITERING_DELAY_MS = 60_000
    }
}
