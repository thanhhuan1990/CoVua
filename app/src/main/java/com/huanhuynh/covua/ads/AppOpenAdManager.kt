package com.huanhuynh.covua.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.huanhuynh.covua.BuildConfig
import com.huanhuynh.covua.MainActivity
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.RcKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App Open:
 * - không hiển thị ở lần mở đầu tiên sau cài đặt,
 * - chỉ khi đã tải sẵn và còn hạn (< 4 giờ),
 * - warm start chỉ khi ở nền > 30 giây, cap 1 lần / 4 phút, tối đa 6/ngày,
 * - bỏ qua khi đang có interstitial/rewarded, hoặc khi mở từ thông báo/widget.
 */
class AppOpenAdManager(
    private val app: Application,
    private val adsManager: AdsManager,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    enum class LoadState { IDLE, LOADING, LOADED, FAILED }

    private var ad: AppOpenAd? = null
    private var loadTime = 0L
    private var currentActivity: Activity? = null
    private var backgroundAt = 0L
    private val shownToday = DailyCounter(app, "app_open")

    /** Bỏ qua lượt hiển thị kế tiếp (mở app từ thông báo nhắc giỗ hoặc widget). */
    @Volatile var skipNext = false

    private val _state = MutableStateFlow(LoadState.IDLE)
    val state: StateFlow<LoadState> = _state.asStateFlow()

    fun register() {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun rc(key: String): Long = FirebaseHelper.getLong(app, key)

    private fun isAdFresh(): Boolean =
        ad != null && SystemClock.elapsedRealtime() - loadTime < FOUR_HOURS_MS

    fun load() {
        if (!adsManager.isAdsEnabledNow()) {
            _state.value = LoadState.FAILED
            return
        }
        if (_state.value == LoadState.LOADING || isAdFresh()) return
        _state.value = LoadState.LOADING
        AppOpenAd.load(
            app,
            BuildConfig.AD_APP_OPEN,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    ad = appOpenAd
                    loadTime = SystemClock.elapsedRealtime()
                    _state.value = LoadState.LOADED
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "App open load failed: ${error.message}")
                    ad = null
                    _state.value = LoadState.FAILED
                }
            },
        )
    }

    /** Không tải được / không đủ điều kiện – để splash không phải chờ. */
    fun markUnavailable() {
        if (_state.value != LoadState.LOADED) _state.value = LoadState.FAILED
    }

    private fun eligible(): Boolean {
        if (!adsManager.isAdsEnabledNow()) return false
        if (adsManager.launchCount.value <= 1) return false // lần mở đầu tiên sau cài
        if (adsManager.isShowingFullScreenAd) return false
        if (adsManager.fullScreenSuppressed()) return false
        val now = SystemClock.elapsedRealtime()
        if (shownToday.get() >= rc(RcKeys.APP_OPEN_MAX_PER_DAY)) return false
        val last = adsManager.lastAppOpenShownAt
        if (last > 0 && now - last < rc(RcKeys.APP_OPEN_MIN_INTERVAL_SEC) * 1000) return false
        return true
    }

    fun showIfAvailable(activity: Activity) {
        if (skipNext) {
            skipNext = false
            return
        }
        val current = ad
        if (current == null || !isAdFresh()) {
            ad = null
            if (_state.value != LoadState.LOADING) _state.value = LoadState.IDLE
            load()
            return
        }
        if (!eligible()) return
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adsManager.isShowingFullScreenAd = true
                adsManager.lastAppOpenShownAt = SystemClock.elapsedRealtime()
            }

            override fun onAdDismissedFullScreenContent() {
                adsManager.isShowingFullScreenAd = false
                ad = null
                _state.value = LoadState.IDLE
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adsManager.isShowingFullScreenAd = false
                ad = null
                _state.value = LoadState.IDLE
                load()
            }
        }
        shownToday.increment()
        current.show(activity)
        FirebaseHelper.logEvent(app, "ad_app_open_show")
    }

    // ---- Process lifecycle (warm start) ----
    override fun onStart(owner: LifecycleOwner) {
        val bgAt = backgroundAt
        if (bgAt == 0L) return // cold start do MainActivity/splash xử lý
        val inBackgroundMs = SystemClock.elapsedRealtime() - bgAt
        if (inBackgroundMs < rc(RcKeys.APP_OPEN_MIN_BACKGROUND_SEC) * 1000) return
        currentActivity?.let { showIfAvailable(it) }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    // ---- Activity tracking ----
    override fun onActivityStarted(activity: Activity) {
        if (activity is MainActivity) currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    companion object {
        private const val TAG = "AppOpen"
        private const val FOUR_HOURS_MS = 4L * 60 * 60 * 1000
    }
}
