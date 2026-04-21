package com.kw.pushgatewayexporter.collector

import com.kw.pushgatewayexporter.model.MetricFamily

/**
 * Interface for all metric collectors.
 * Each collector is independently testable and must handle failures gracefully —
 * returning an empty list rather than throwing.
 */
interface Collector {
    /** Human-readable name for logging / UI. */
    val name: String

    /**
     * Collect metric families. Must not throw.
     * Returns empty list if the subsystem is unavailable or collection fails.
     */
    fun collect(): List<MetricFamily>
}
