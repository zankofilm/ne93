package ir.madreseyar.student

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo=StudentRepository(applicationContext)
        if(!repo.hasSession()) return Result.success()
        return try {
            val sync=repo.syncAll(downloadContent = true)
            if(sync.pendingExamUploads>0 || sync.pendingHomeworkUploads>0 || repo.pendingIncidentCount()>0) Result.retry() else Result.success()
        } catch(e: ApiException) {
            if(e.status in 400..499 && e.status !in listOf(408,429)) Result.failure() else Result.retry()
        } catch(_:Exception){ Result.retry() }
    }

    companion object {
        private const val NAME="madreseyar-student-sync"
        private const val PERIODIC_NAME="madreseyar-student-periodic-sync"

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context:Context) {
            val req=OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(context:Context) {
            val req=PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(context:Context) {
            val wm=WorkManager.getInstance(context)
            wm.cancelUniqueWork(NAME)
            wm.cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
