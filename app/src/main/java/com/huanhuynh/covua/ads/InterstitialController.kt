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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.huanhuynh.covua.BuildConfig
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.RcKeys

/**
 * Interstitial có frequency cap (Remote Config):
 * - tối thiểu N giây giữa 2 lần, tối thiểu M thao tác, tối đa K lần/ngày,
 * - cách App Open ≥ 60 giây, không trong 2 phút đầu của người dùng mới.
 * Chỉ dùng tại điểm chuyển tự nhiên, hiện TRƯỚC khi điều hướng (đóng xong mới chuyển màn) – KHÔNG gắn vào Back/Đóng.
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
        tryShow(activity, requireActions = true, onDone = {})
    }

    /**
     * Điểm chuyển màn hình tự nhiên. Quảng cáo hiện TRƯỚC khi chuyển màn; [onDone] (thường là lệnh
     * điều hướng) chỉ chạy SAU khi người dùng đóng quảng cáo, hoặc chạy ngay nếu không hiện quảng cáo.
     * Không bao giờ gọi từ nút Back / Đóng (chính sách AdMob "Site behavior: Navigation").
     */
    fun onTransition(activity: Activity, onDone: () -> Unit = {}) {
        if (!tryShow(activity, requireActions = false, onDone = onDone)) onDone()
    }

    /** Chỉ hiện khi màn hình đang ở trạng thái RESUMED (không hiện lúc đang chuyển màn / đang ở nền). */
    private fun isResumed(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val owner = activity as? LifecycleOwner ?: return true
        return owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    /** @return true nếu đã gọi show – khi đó [onDone] sẽ chạy lúc quảng cáo đóng / lỗi hiển thị. */
    private fun tryShow(activity: Activity, requireActions: Boolean, onDone: () -> Unit): Boolean {
        if (!adsManager.isAdsEnabledNow()) return false
        val current = ad
        if (current == null) {
            preload()
            return false
        }
        if (!isResumed(activity)) return false
        if (!canShow(requireActions)) return false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adsManager.isShowingFullScreenAd = true
                adsManager.lastInterstitialShownAt = SystemClock.elapsedRealtime()
            }

            override fun onAdDismissedFullScreenContent() {
                adsManager.isShowingFullScreenAd = false
                ad = null
                preload()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adsManager.isShowingFullScreenAd = false
                ad = null
                preload()
                onDone()
            }
        }
        actionCount = 0
        shownToday.increment()
        current.show(activity)
        FirebaseHelper.logEvent(app, "ad_interstitial_show")
        return true
    }

    /** Có quảng cáo sẵn để hiện ngay không (nếu chưa thì bắt đầu tải). */
    fun isReady(): Boolean = (ad != null).also { if (!it) preload() }

    /**
     * Hiện ngay (trước khi chuyển màn), bỏ qua frequency cap – dùng khi bấm "Ván mới" sau ván thua ở chế độ Chỉ đánh cờ
     * (điểm chuyển tự nhiên). [onDone] chạy khi đóng hoặc hiện lỗi.
     * @return false nếu tắt quảng cáo / chưa có quảng cáo (khi đó không gọi [onDone]).
     */
    fun showRequired(activity: Activity, onDone: () -> Unit): Boolean {
        if (!adsManager.isAdsEnabledNow() || adsManager.isShowingFullScreenAd) return false
        val current = ad ?: run { preload(); return false }
        if (!isResumed(activity)) return false
        // Không hiện liền sau một quảng cáo toàn màn hình khác (App Open, interstitial, có thưởng).
        val now = SystemClock.elapsedRealtime()
        val lastFull = maxOf(adsManager.lastInterstitialShownAt, adsManager.lastAppOpenShownAt)
        if (lastFull > 0 && now - lastFull < 60_000L) return false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adsManager.isShowingFullScreenAd = true
                adsManager.lastInterstitialShownAt = SystemClock.elapsedRealtime()
            }

            override fun onAdDismissedFullScreenContent() {
                adsManager.isShowingFullScreenAd = false
                ad = null
                preload()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adsManager.isShowingFullScreenAd = false
                ad = null
                preload()
                onDone()
            }
        }
        actionCount = 0
        shownToday.increment()
        current.show(activity)
        FirebaseHelper.logEvent(app, "ad_interstitial_loss_gate")
        return true
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
