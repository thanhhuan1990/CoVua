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

    /** Thời điểm app tự mở màn ngoài (chia sẻ, Google Play, trình duyệt, email, đánh giá). */
    @Volatile private var externalLaunchAt = 0L

    /**
     * Gọi ngay trước khi app tự mở một màn bên ngoài: lúc quay lại sẽ KHÔNG hiện App Open
     * (người dùng chỉ đang quay về màn đang dùng – chính sách AdMob "Site behavior: Navigation").
     */
    fun markExternalLaunch() {
        externalLaunchAt = SystemClock.elapsedRealtime()
    }

    /**
     * Hiện App Open nếu đủ điều kiện. [onReady] luôn được gọi đúng 1 lần: ngay khi quảng cáo đã phủ màn hình,
     * hoặc ngay lập tức nếu không hiện – để màn chờ (splash) chỉ tắt khi quảng cáo đã nằm trên cùng,
     * không có khung hình nội dung nào lọt ra trước quảng cáo.
     */
    fun showIfAvailable(activity: Activity, onReady: () -> Unit = {}) {
        var readyCalled = false
        val ready = { if (!readyCalled) { readyCalled = true; onReady() } }
        if (skipNext) {
            skipNext = false
            ready()
            return
        }
        val current = ad
        if (current == null || !isAdFresh()) {
            ad = null
            if (_state.value != LoadState.LOADING) _state.value = LoadState.IDLE
            load()
            ready()
            return
        }
        if (!eligible() || activity.isFinishing || activity.isDestroyed) {
            ready()
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adsManager.isShowingFullScreenAd = true
                adsManager.lastAppOpenShownAt = SystemClock.elapsedRealtime()
                ready()
            }

            override fun onAdDismissedFullScreenContent() {
                adsManager.isShowingFullScreenAd = false
                ad = null
                _state.value = LoadState.IDLE
                load()
                ready()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adsManager.isShowingFullScreenAd = false
                ad = null
                _state.value = LoadState.IDLE
                load()
                ready()
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
        val now = SystemClock.elapsedRealtime()
        // Quay về từ màn ngoài do chính app mở (chia sẻ, Play, trình duyệt…) trong vòng 30 phút → không quảng cáo.
        val ext = externalLaunchAt
        if (ext > 0 && ext >= bgAt - 5_000 && now - ext < 30 * 60_000L) {
            externalLaunchAt = 0L
            return
        }
        val inBackgroundMs = now - bgAt
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
