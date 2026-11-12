package com.huanhuynh.covua.ads

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.huanhuynh.covua.BuildConfig
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.RcKeys

/**
 * Interstitial có frequency cap (Remote Config):
 * - tối thiểu N giây giữa 2 lần, tối thiểu M thao tác, tối đa K lần/ngày,
 * - cách App Open ≥ 60 giây, không trong 2 phút đầu của người dùng mới.
 * Chỉ dùng tại điểm chuyển "tự nhiên" (Đổi ngày) – KHÔNG dùng ở lịch tháng / chi tiết ngày.
 */
class InterstitialController(
    private val app: Application,
    private val adsManager: AdsManager,
) {
    private var ad: InterstitialAd? = null
    private var loading = false
    private var actionCount = 0
    private val shownToday = DailyCounter(app, "interstitial")

    private val firstInstallTime: Long by lazy {
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime }
            .getOrDefault(0L)
    }

    private fun rc(key: String): Long = FirebaseHelper.getLong(app, key)

    fun preload() {
        if (!adsManager.isAdsEnabledNow() || loading || ad != null) return
        loading = true
        InterstitialAd.load(
            app,
            BuildConfig.AD_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    ad = interstitialAd
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Interstitial load failed: ${error.message}")
                    ad = null
                    loading = false
                }
            },
        )
    }

    /** Ghi nhận 1 thao tác của người dùng; hiển thị nếu thỏa mọi điều kiện cap. */
    fun onUserAction(activity: Activity) {
        actionCount++
        tryShow(activity, requireActions = true)
    }

    /**
     * Điểm chuyển màn hình tự nhiên (rời bài văn khấn, sau khi lưu ngày giỗ):
     * không cần đủ số thao tác, nhưng vẫn áp dụng mọi cap còn lại.
     */
    fun onTransition(activity: Activity) {
        tryShow(activity, requireActions = false)
    }

    private fun tryShow(activity: Activity, requireActions: Boolean) {
        if (!adsManager.isAdsEnabledNow()) return
        val current = ad
        if (current == null) {
            preload()
            return
        }
        if (!canShow(requireActions)) return
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adsManager.isShowingFullScreenAd = true
                adsManager.lastInterstitialShownAt = SystemClock.elapsedRealtime()
            }

            override fun onAdDismissedFullScreenContent() {
                adsManager.isShowingFullScreenAd = false
                ad = null
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adsManager.isShowingFullScreenAd = false
                ad = null
                preload()
            }
        }
        actionCount = 0
        shownToday.increment()
        current.show(activity)
        FirebaseHelper.logEvent(app, "ad_interstitial_show")
    }

    private fun canShow(requireActions: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (adsManager.isShowingFullScreenAd) return false
        if (adsManager.fullScreenSuppressed()) return false
        if (requireActions && actionCount < rc(RcKeys.INTERSTITIAL_MIN_ACTIONS)) return false
        if (shownToday.get() >= rc(RcKeys.INTERSTITIAL_MAX_PER_DAY)) return false
        val last = adsManager.lastInterstitialShownAt
        if (last > 0 && now - last < rc(RcKeys.INTERSTITIAL_MIN_INTERVAL_SEC) * 1000) return false
        val appOpen = adsManager.lastAppOpenShownAt
        if (appOpen > 0 && now - appOpen < rc(RcKeys.INTERSTITIAL_AFTER_APP_OPEN_SEC) * 1000) return false
        if (System.currentTimeMillis() - firstInstallTime < rc(RcKeys.NEW_USER_GRACE_SEC) * 1000) return false
        return true
    }

    companion object {
        private const val TAG = "Interstitial"
    }
}
