package org.example.project

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.example.project.AppContextHolder
import org.example.project.worker.SessionSyncWorker

actual fun scheduleOfflineSync() {
    val context = AppContextHolder.context ?: return
    // Kikötjük, hogy csak akkor induljon el a Worker, ha van élő netkapcsolat
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val syncWorkRequest = OneTimeWorkRequestBuilder<SessionSyncWorker>()
        .setConstraints(constraints)
        .build()

    // Beütemezzük a szinkronizációt
    WorkManager.getInstance(context)
        .enqueueUniqueWork("OfflineSessionSync", ExistingWorkPolicy.REPLACE, syncWorkRequest)
}
