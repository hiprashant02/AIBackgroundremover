package com.remover.background.AI.utils

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manager for interstitial ads
 * Handles loading and showing full-screen interstitial ads
 */
class InterstitialAdManager(private val context: Context) {
    
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    
    companion object {
        // Test interstitial ad unit ID - Replace with your actual ad unit ID for production
        private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
    
    /**
     * Load an interstitial ad
     * Call this early so the ad is ready when needed
     */
    fun loadAd(adUnitId: String = TEST_AD_UNIT_ID) {
        if (isLoading || interstitialAd != null) {
            return // Already loading or loaded
        }
        
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }
    
    /**
     * Show the interstitial ad if it's loaded
     * @param activity The activity to show the ad in
     * @param onAdDismissed Callback when ad is dismissed or not shown (ALWAYS called)
     */
    fun showAd(
        activity: Activity,
        onAdDismissed: () -> Unit
    ) {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    // Ad was dismissed, load a new one for next time
                    interstitialAd = null
                    loadAd()
                    onAdDismissed()
                }
                
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    // Ad failed to show - still call callback to ensure review happens
                    interstitialAd = null
                    loadAd()
                    onAdDismissed()
                }
                
                override fun onAdShowedFullScreenContent() {
                    // Ad showed successfully
                    interstitialAd = null
                }
            }
            
            try {
                interstitialAd?.show(activity)
            } catch (e: Exception) {
                // Exception during show - ensure callback is still called
                interstitialAd = null
                loadAd()
                onAdDismissed()
            }
        } else {
            // Ad not loaded - still call callback to ensure review happens
            onAdDismissed()
            // Try to load for next time
            loadAd()
        }
    }
    
    /**
     * Check if an ad is ready to show
     */
    fun isAdReady(): Boolean {
        return interstitialAd != null
    }
    
    /**
     * Preload ad for better user experience
     * Call this when you anticipate the user might trigger an ad soon
     */
    fun preloadAd() {
        if (!isAdReady() && !isLoading) {
            loadAd()
        }
    }
}
