package com.hamman.consoclaude

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Toutes les 15 min (minimum imposé par Android). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "refresh", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "refresh_now", ExistingWorkPolicy.REPLACE, request
            )
        }

        suspend fun refresh(context: Context) {
            val store = UsageStore(context)
            val cookie = store.cookie
            if (cookie == null) {
                store.error = "Non connecté — ouvrez l'app"
            } else {
                try {
                    val (orgId, usage) = UsageApi.fetch(context, cookie, store.orgId)
                    store.orgId = orgId
                    store.saveUsage(usage)
                } catch (e: AuthException) {
                    store.error = "${e.message} — reconnectez-vous dans l'app"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    store.error = "Erreur : ${e.message ?: e.javaClass.simpleName}"
                }
            }
            UsageWidget.updateAll(context)
        }
    }
}
