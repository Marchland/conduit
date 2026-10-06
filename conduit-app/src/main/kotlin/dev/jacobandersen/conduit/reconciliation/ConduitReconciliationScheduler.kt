package dev.jacobandersen.conduit.reconciliation

import dev.jacobandersen.conduit.config.ReconciliationProperties
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration

/** Runs [ConduitReconciliationService] recurrently when enabled. */
@Component
class ConduitReconciliationScheduler(
    private val jobScheduler: JobScheduler,
    private val reconciliationService: ConduitReconciliationService,
    private val properties: ReconciliationProperties,
) {
    @PostConstruct
    fun schedule() {
        if (!properties.enabled) return
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, Duration.ofMinutes(properties.intervalMinutes)) {
            reconciliationService.reconcile()
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "conduit-reconciliation"
    }
}
