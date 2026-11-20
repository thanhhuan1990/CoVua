package com.huanhuynh.covua.ads

import android.app.Activity
import android.app.Application
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.huanhuynh.covua.BuildConfig

/**
 * Rewarded – chỉ hiển thị khi người dùng CHỦ ĐỘNG bấm nút "Xem 1 quảng cáo để…".
 * MVP chưa có điểm dùng (theme widget / xem ngày theo tuổi là v1.1/v2); để sẵn cho các bản sau.
 */
class RewardedController(
    private val app: Application,
    private val adsManager: AdsManager,
) {
    private var ad: RewardedAd? = null
    private var loading = false

    val isReady: Boolean get() = ad != null

    fun preload() {
        if (!adsManager.isAdsEnabledNow() || loading || ad != null) return
        loading = true
        RewardedAd.load(
            app,
            BuildConfig.AD_REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    ad = rewardedAd
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("Rewarded", "load failed: ${error.message}")
                    ad = null
                    loading = false
                }
            },
        )
    }

    /** @return false nếu chưa có quảng cáo sẵn sàng. */
    fun show(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit = {}): Boolean {
        val current = ad ?: run { preload(); return false }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adsManager.isShowingFullScreenAd = true
                // Giãn cách: interstitial không hiện ngay sau quảng cáo có thưởng.
                adsManager.lastInterstitialShownAt = android.os.SystemClock.elapsedRealtime()
            }

            override fun onAdDismissedFullScreenContent() {
                adsManager.isShowingFullScreenAd = false
                ad = null
                onClosed()
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adsManager.isShowingFullScreenAd = false
                ad = null
                onClosed()
                preload()
            }
        }
        current.show(activity) { _ -> onReward() }
        return true
    }
}
