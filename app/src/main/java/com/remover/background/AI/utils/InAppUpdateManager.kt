package com.remover.background.AI.utils

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.tasks.await

class InAppUpdateManager(private val activity: Activity) {
    
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    
    /**
     * Check if an update is available
     * @return AppUpdateInfo if update is available, null otherwise
     */
    suspend fun checkForUpdate(): AppUpdateInfo? {
        return try {
            val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
            
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                appUpdateInfo
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Start an immediate update flow
     * This will show a full-screen update dialog
     */
    fun startImmediateUpdate(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        try {
            if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    REQUEST_CODE_UPDATE
                )
            }
        } catch (e: IntentSender.SendIntentException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Start a flexible update flow
     * This allows the user to continue using the app while downloading
     */
    fun startFlexibleUpdate(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        try {
            if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    REQUEST_CODE_UPDATE
                )
            }
        } catch (e: IntentSender.SendIntentException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Complete a flexible update by installing the downloaded update
     */
    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
    }
    
    /**
     * Check if an update is already in progress (for flexible updates)
     */
    suspend fun isUpdateInProgress(): Boolean {
        return try {
            val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
            appUpdateInfo.installStatus() == InstallStatus.DOWNLOADING ||
            appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Register a listener for flexible update progress
     */
    fun registerUpdateListener(onStateUpdate: (InstallStatus) -> Unit) {
//        appUpdateManager.registerListener { state ->
//            onStateUpdate(state.installStatus())
//        }
    }
    
    /**
     * Unregister the update listener
     */
    fun unregisterUpdateListener() {
        // Note: You need to keep a reference to the listener to unregister it
        // This is a simplified version
    }
    
    companion object {
        const val REQUEST_CODE_UPDATE = 1001
    }
}
