package com.huanhuynh.covua.feature.iar

import android.app.Activity
import android.os.SystemClock
import com.google.android.play.core.review.ReviewManagerFactory
import com.huanhuynh.covua.AppContainer
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.domain.Ev

/**
 * Hỏi đánh giá trong app (Play In-App Review): chỉ sau một ván thắng đáng nhớ, đã chơi ≥ 3 ván, ≥ 2 ngày hoạt động,
 * không ngay sau quảng cáo, tối đa 1 lần / 60 ngày. Google tự quyết có hiện hay không.
 */
object InAppReview {
    private const val KEY = "iar_last_at"
    private const val GAP_MS = 60L * 86_400_000

    suspend fun maybeRequest(activity: Activity, c: AppContainer) {
        if (!c.flag(RcKeys.IAR_ENABLED)) return
        val s = c.settings.current()
        if (s.games < c.rcInt(RcKeys.REVIEW_PROMPT_MIN_GAMES)) return
        if (c.activity.current().days.count { it.counts } < 2) return
        val now = System.currentTimeMillis()
        if (now - c.settings.getLongKey(KEY, 0) < GAP_MS) return
        val lastAd = maxOf(c.adsManager.lastInterstitialShownAt, c.adsManager.lastAppOpenShownAt)
        if (c.adsManager.isShowingFullScreenAd || (lastAd > 0 && SystemClock.elapsedRealtime() - lastAd < 60_000)) return
        c.settings.setLongKey(KEY, now)
        c.events.log(Ev.IAR_REQUEST)
        runCatching {
            val m = ReviewManagerFactory.create(activity)
            m.requestReviewFlow().addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    c.appOpenAdManager.markExternalLaunch()
                    m.launchReviewFlow(activity, t.result)
                }
            }
        }
    }
}
