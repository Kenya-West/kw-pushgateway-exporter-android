package com.kw.pushgatewayexporter.config

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Serialization of [EndpointProfile] lists for import/export. Uses
 * built-in [org.json] so no extra dependency is required.
 *
 * Wire format (version 1):
 * ```
 * {
 *   "version": 1,
 *   "endpoints": [
 *     { "id": "...", "name": "Prod", "url": "https://...",
 *       "username": "", "password": "",
 *       "insecureTls": false, "jobName": "android_device_exporter",
 *       "customHeaders": { "X-Token": "..." } }
 *   ]
 * }
 * ```
 */
object EndpointsJson {
    const val CURRENT_VERSION = 1

    fun serialize(endpoints: List<EndpointProfile>): String {
        val arr = JSONArray()
        endpoints.forEach { ep ->
            val o = JSONObject()
            o.put("id", ep.id)
            o.put("name", ep.name)
            o.put("url", ep.url)
            o.put("username", ep.username)
            o.put("password", ep.password)
            o.put("insecureTls", ep.insecureTls)
            o.put("jobName", ep.jobName)
            val headers = JSONObject()
            ep.customHeaders.forEach { (k, v) -> headers.put(k, v) }
            o.put("customHeaders", headers)
            arr.put(o)
        }
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("endpoints", arr)
        return root.toString(2)
    }

    fun parse(json: String): List<EndpointProfile> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("endpoints") ?: return emptyList()
        val out = ArrayList<EndpointProfile>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val headers = mutableMapOf<String, String>()
            o.optJSONObject("customHeaders")?.let { h ->
                val keys = h.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headers[k] = h.optString(k, "")
                }
            }
            val idRaw = o.optString("id", "")
            out += EndpointProfile(
                id = if (idRaw.isBlank()) UUID.randomUUID().toString() else idRaw,
                name = o.optString("name", ""),
                url = o.optString("url", ""),
                username = o.optString("username", ""),
                password = o.optString("password", ""),
                insecureTls = o.optBoolean("insecureTls", false),
                jobName = o.optString("jobName", "android_device_exporter")
                    .ifBlank { "android_device_exporter" },
                customHeaders = headers
            )
        }
        return out
    }
}
