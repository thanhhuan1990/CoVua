package com.huanhuynh.covua.ads

import android.app.Activity
import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.huanhuynh.covua.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UMP consent → MobileAds.initialize (một lần, chỉ khi canRequestAds) → trạng thái dùng chung cho mọi định dạng.
 */
class AdsManager(
    private val app: Application,
    settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(app)
    private val initStarted = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    val adsRemoved: StateFlow<Boolean> = settings.settings.map { it.adsRemoved }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val launchCount: StateFlow<Int> = settings.settings.map { it.launchCount }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** true khi được phép hiển thị quảng cáo (đã consent & chưa mua gói Xóa quảng cáo). */
    val adsEnabled: StateFlow<Boolean> = combine(_canRequestAds, adsRemoved) { can, removed -> can && !removed }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Đang có quảng cáo toàn màn hình (interstitial / rewarded / app open). */
    @Volatile var isShowingFullScreenAd: Boolean = false

    @Volatile var lastAppOpenShownAt: Long = 0L
    @Volatile var lastInterstitialShownAt: Long = 0L
    /** Mở app từ thông báo "Thầy nhắc" → không App Open / interstitial đến mốc này (elapsedRealtime). */
    @Volatile var suppressFullScreenUntil: Long = 0L
    fun fullScreenSuppressed(): Boolean = android.os.SystemClock.elapsedRealtime() < suppressFullScreenUntil

    private val readyCallbacks = mutableListOf<() -> Unit>()

    /** Kết quả consent hiện tại (đồng bộ), độc lập với việc SDK đã khởi tạo xong hay chưa. */
    fun consentAllowsAds(): Boolean = consentInformation.canRequestAds()

    fun isAdsEnabledNow(): Boolean = _canRequestAds.value && !adsRemoved.value

    /** Gọi mỗi lần mở app (từ Activity) TRƯỚC khi tải quảng cáo. */
    fun gatherConsent(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    updatePrivacyStatus()
                    initializeIfAllowed()
                    onComplete()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent update lỗi: ${requestError.message}")
                updatePrivacyStatus()
                initializeIfAllowed()
                onComplete()
            },
        )
        // Nếu đã có consent từ phiên trước, có thể khởi tạo ngay song song.
        initializeIfAllowed()
    }

    private fun updatePrivacyStatus() {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initializeIfAllowed() {
        if (!consentInformation.canRequestAds()) return
        if (initStarted.getAndSet(true)) return
        scope.launch(Dispatchers.IO) {
            runCatching { MobileAds.initialize(app) {} }
            scope.launch(Dispatchers.Main) {
                _canRequestAds.value = true
                val callbacks = synchronized(readyCallbacks) { readyCallbacks.toList().also { readyCallbacks.clear() } }
                callbacks.forEach { it() }
            }
        }
    }

    /** Chạy [block] khi SDK đã được khởi tạo (ngay lập tức nếu đã sẵn sàng). */
    fun whenReady(block: () -> Unit) {
        if (_canRequestAds.value) {
            block()
        } else {
            synchronized(readyCallbacks) { readyCallbacks.add(block) }
        }
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) Log.w(TAG, "Privacy options: ${formError.message}")
            updatePrivacyStatus()
        }
    }

    companion object {
        private const val TAG = "AdsManager"
    }
}
