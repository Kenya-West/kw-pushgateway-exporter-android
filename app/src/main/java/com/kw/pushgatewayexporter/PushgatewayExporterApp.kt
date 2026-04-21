package com.kw.pushgatewayexporter

import android.app.Application
import com.kw.pushgatewayexporter.config.ConfigRepository
import com.kw.pushgatewayexporter.identity.InstanceIdentity
import com.kw.pushgatewayexporter.scheduler.SchedulerManager

class PushgatewayExporterApp : Application() {

    lateinit var configRepository: ConfigRepository
        private set

    lateinit var instanceIdentity: InstanceIdentity
        private set

    lateinit var schedulerManager: SchedulerManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        configRepository = ConfigRepository(this)
        instanceIdentity = InstanceIdentity(this)
        schedulerManager = SchedulerManager(this)

        // Re-schedule if previously enabled
        if (configRepository.getConfig().schedulingEnabled) {
            schedulerManager.schedulePeriodicJob(configRepository.getConfig())
        }
    }

    companion object {
        lateinit var instance: PushgatewayExporterApp
            private set
    }
}
