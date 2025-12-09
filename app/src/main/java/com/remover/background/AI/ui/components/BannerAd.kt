package com.remover.background.AI.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.remover.background.AI.BuildConfig

/**
 * Composable banner ad component
 * Displays a Google AdMob banner ad at the bottom of the screen
 * 
 * @param adUnitId The AdMob ad unit ID. Uses BuildConfig value by default.
 * @param modifier Optional modifier for the ad container
 */
@Composable
fun BannerAd(
    adUnitId: String = BuildConfig.ADMOB_BANNER_ID,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    setAdUnitId(adUnitId);
                    loadAd(AdRequest.Builder().build())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )
    }
}

/**
 * Adaptive banner ad that adjusts to screen width
 * Better for different screen sizes
 */
@Composable
fun AdaptiveBannerAd(
    adUnitId: String = BuildConfig.ADMOB_BANNER_ID,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    // Get the screen width in dp
                    val display = ctx.resources.displayMetrics
                    val adWidthPixels = display.widthPixels.toFloat()
                    val density = display.density
                    val adWidth = (adWidthPixels / density).toInt()
                    
                    // Use adaptive banner size
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth))
                    this.adUnitId = adUnitId
                    loadAd(AdRequest.Builder().build())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
}
