package com.kw.pushgatewayexporter

import android.app.Application
import com.kw.pushgatewayexporter.config.ConfigRepository
import com.kw.pushgatewayexporter.identity.InstanceIdentity
import com.kw.pushgatewayexporter.reliability.BootRecoveryCoordinator
import com.kw.pushgatewayexporter.reliability.ForegroundServiceController
import com.kw.pushgatewayexporter.reliability.ReliabilityLog
import com.kw.pushgatewayexporter.reliability.ReliabilityManager
import com.kw.pushgatewayexporter.reliability.ReliabilityPreferences
import com.kw.pushgatewayexporter.reliability.ReliabilityTestRunner
import com.kw.pushgatewayexporter.scheduler.SchedulerManager

class PushgatewayExporterApp : Application() {

    lateinit var configRepository: ConfigRepository
        private set

    lateinit var instanceIdentity: InstanceIdentity
        private set

    lateinit var schedulerManager: SchedulerManager
        private set

    lateinit var reliabilityManager: ReliabilityManager
        private set

    lateinit var bootRecoveryCoordinator: BootRecoveryCoordinator
        private set

    /**
     * Application-scoped so an ongoing self-test survives screen rotation and navigation
     * between reliability screens. The ViewModel observes its state flow.
     */
    lateinit var reliabilityTestRunner: ReliabilityTestRunner
        private set

    // Convenience delegates to reliability subsystem, already constructed inside ReliabilityManager.
    val reliabilityLog: ReliabilityLog get() = reliabilityManager.log
    val reliabilityPreferences: ReliabilityPreferences get() = reliabilityManager.preferences
    val foregroundServiceController: ForegroundServiceController get() = reliabilityManager.foregroundController

    override fun onCreate() {
        super.onCreate()
        instance = this
        configRepository = ConfigRepository(this)
        instanceIdentity = InstanceIdentity(this)
        schedulerManager = SchedulerManager(this)
        reliabilityManager = ReliabilityManager.create(this)
        bootRecoveryCoordinator = BootRecoveryCoordinator(this)
        reliabilityTestRunner = ReliabilityTestRunner(this)

        // Re-schedule if previously enabled
        if (configRepository.getConfig().schedulingEnabled) {
            schedulerManager.schedulePeriodicJob(configRepository.getConfig())
        }
        // Restart foreground service if the user had enabled reliability mode and the
        // process was killed / app was upgraded.
        if (reliabilityPreferences.isForegroundModeEnabled() && !foregroundServiceController.isRunning()) {
            foregroundServiceController.start()
        }
    }

    companion object {
        lateinit var instance: PushgatewayExporterApp
            private set
    }
}
