package app.fahrtenbuch.mobile.sync

import android.content.Context
import androidx.work.*
import app.fahrtenbuch.mobile.data.api.SessionStore
import java.util.concurrent.TimeUnit

object SyncScheduler {
    /**
     * Push immediately when Android currently has a usable network.
     * If there is no network, enqueue() keeps the data local and WorkManager
     * starts the same sync automatically as soon as connectivity returns.
     */
    fun pushIfOnline(context: Context) {
        if (!SessionStore(context).isServerMode) { cancel(context); return }
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val online = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        if (online) {
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "fahrtenbuch-sync",
                ExistingWorkPolicy.REPLACE,
                request
            )
        } else {
            enqueue(context)
        }
    }

    fun enqueue(context: Context) {
        if (!SessionStore(context).isServerMode) { cancel(context); return }
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("fahrtenbuch-sync", ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodic(context: Context) {
        if (!SessionStore(context).isServerMode) { cancel(context); return }
        val request = PeriodicWorkRequestBuilder<OfflineSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("fahrtenbuch-periodic-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("fahrtenbuch-sync")
        WorkManager.getInstance(context).cancelUniqueWork("fahrtenbuch-periodic-sync")
    }
}
