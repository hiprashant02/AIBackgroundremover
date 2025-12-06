package com.remover.background.AI.utils

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

class InAppReviewManager(private val context: Context) {
    
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
    private val preferencesManager = PreferencesManager(context)
    
    companion object {
        private const val PREF_REVIEW_REQUESTED = "review_requested"
        private const val PREF_SAVE_COUNT = "save_count"
        private const val SAVES_BEFORE_REVIEW = 3 // Ask for review after 3 successful saves
    }
    
    /**
     * Check if we should request a review
     * Only request review if:
     * 1. User has saved at least SAVES_BEFORE_REVIEW images
     * 2. Review hasn't been requested before
     */
    suspend fun shouldRequestReview(): Boolean {
        // For now, we'll use a simple counter
        // In production, you might want to use DataStore for this
        val saveCount = getSaveCount()
        val reviewRequested = hasReviewBeenRequested()
        
        return saveCount >= SAVES_BEFORE_REVIEW && !reviewRequested
    }
    
    /**
     * Increment the save counter
     */
    suspend fun incrementSaveCount() {
        val currentCount = getSaveCount()
        saveSaveCount(currentCount + 1)
    }
    
    /**
     * Request the review flow
     * This will show the in-app review dialog if conditions are met
     */
    suspend fun requestReview(activity: Activity): Boolean {
        return try {
            // Request the ReviewInfo object
            val reviewInfo = reviewManager.requestReviewFlow().await()
            
            // Launch the review flow
            reviewManager.launchReviewFlow(activity, reviewInfo).await()
            
            // Mark that we've requested a review
            markReviewRequested()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Request review with automatic checking
     * This is the main method to call after a successful save
     */
    suspend fun requestReviewIfEligible(activity: Activity) {
        incrementSaveCount()
        
        if (shouldRequestReview()) {
            requestReview(activity)
        }
    }
    
    // Helper methods for preferences
    private fun getSaveCount(): Int {
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getInt(PREF_SAVE_COUNT, 0)
    }
    
    private fun saveSaveCount(count: Int) {
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt(PREF_SAVE_COUNT, count).apply()
    }
    
    private fun hasReviewBeenRequested(): Boolean {
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(PREF_REVIEW_REQUESTED, false)
    }
    
    private fun markReviewRequested() {
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(PREF_REVIEW_REQUESTED, true).apply()
    }
    
    /**
     * Reset the review state (useful for testing)
     */
    fun resetReviewState() {
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(PREF_REVIEW_REQUESTED, false)
            .putInt(PREF_SAVE_COUNT, 0)
            .apply()
    }
}
