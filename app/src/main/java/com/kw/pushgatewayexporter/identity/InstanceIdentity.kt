package com.kw.pushgatewayexporter.identity

import android.content.Context
import java.util.UUID

/**
 * Manages a stable app-installation UUID for use as the primary instance identity
 * in Pushgateway grouping keys.
 *
 * Does NOT use IMEI, IMSI, hardware serial, or other privileged identifiers.
 * Generates a random UUID on first launch and persists it in private SharedPreferences.
 */
class InstanceIdentity(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the installation UUID, creating one if it doesn't exist yet.
     */
    val installationId: String
        get() {
            val existing = prefs.getString(KEY_INSTALLATION_ID, null)
            if (existing != null) return existing
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, newId).apply()
            return newId
        }

    /**
     * Resets the installation identity. Use this for explicit cleanup / re-provisioning.
     * The old Pushgateway group should be DELETEd before resetting.
     */
    fun resetIdentity(): String {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALLATION_ID, newId).apply()
        return newId
    }

    /**
     * Returns the previous installation ID, if any, for stale group cleanup.
     */
    fun getPreviousId(): String? = prefs.getString(KEY_PREVIOUS_ID, null)

    /**
     * Saves the current ID as previous before resetting.
     */
    fun rotateIdentity(): String {
        val currentId = installationId
        prefs.edit().putString(KEY_PREVIOUS_ID, currentId).apply()
        return resetIdentity()
    }

    companion object {
        private const val PREFS_NAME = "instance_identity_prefs"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_PREVIOUS_ID = "previous_installation_id"
    }
}
