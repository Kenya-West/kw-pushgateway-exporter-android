package com.kw.pushgatewayexporter.config

import java.util.UUID

/**
 * A named Pushgateway endpoint profile. The user can define several profiles
 * and mark one as active. Only the active one is used by the push pipeline;
 * its connection-related fields are mirrored into [AppConfig] whenever the
 * active selection or the profile itself changes.
 */
data class EndpointProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val insecureTls: Boolean = false,
    val jobName: String = "android_device_exporter",
    val customHeaders: Map<String, String> = emptyMap()
)
