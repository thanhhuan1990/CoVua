package com.huanhuynh.covua.ads

import android.content.Context
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.huanhuynh.covua.BuildConfig
import com.huanhuynh.covua.i18n.t

/**
 * Quảng cáo native dạng thẻ, đặt GIỮA nội dung tĩnh (vd. màn Phân tích ván), không sát nút bấm.
 * Luôn có nhãn "Quảng cáo" + biểu tượng AdChoices (SDK tự thêm) để phân biệt rõ với nội dung app.
 * Không hiện gì nếu chưa consent / đã mua Xóa quảng cáo / tải lỗi (không để khoảng trống).
 */
@Composable
fun NativeAdCard(adsManager: AdsManager, modifier: Modifier = Modifier) {
    val enabled by adsManager.adsEnabled.collectAsStateWithLifecycle()
    if (!enabled) return
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(Unit) {
        var disposed = false
        val loader = AdLoader.Builder(context, BuildConfig.AD_NATIVE)
            .forNativeAd { ad ->
                if (disposed) ad.destroy() else { nativeAd?.destroy(); nativeAd = ad }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) { /* không hiện gì */ }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build(),
            )
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            nativeAd?.destroy()
            nativeAd = null
        }
    }
    val ad = nativeAd ?: return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx -> NativeAdLayout(ctx).also { it.view.tag = it }.view },
        update = { (it.tag as NativeAdLayout).bind(ad) },
    )
}

/**
 * Bố cục thẻ native dựng bằng code (không cần XML): nhãn, icon + tiêu đề, nội dung, ảnh/video, nút hành động.
 * NativeAdView là lớp final nên bọc lại (không kế thừa).
 */
private class NativeAdLayout(ctx: Context) {
    val view = NativeAdView(ctx)
    private fun dp(v: Int) = (v * view.resources.displayMetrics.density).toInt()

    private val label = TextView(ctx).apply {
        text = t("Quảng cáo", "Ad", "广告", "広告")
        textSize = 10f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(AColor.WHITE)
        setPadding(dp(6), dp(1), dp(6), dp(1))
        background = GradientDrawable().apply { setColor(AColor.parseColor("#B08A3E")); cornerRadius = dp(4).toFloat() }
    }
    private val icon = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val headline = TextView(ctx).apply {
        textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(AColor.parseColor("#222222"))
        maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    private val advertiser = TextView(ctx).apply { textSize = 11.5f; setTextColor(AColor.parseColor("#777777")); maxLines = 1 }
    private val body = TextView(ctx).apply {
        textSize = 12.5f; setTextColor(AColor.parseColor("#555555")); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
    }
    private val media = MediaView(ctx)
    private val cta = Button(ctx).apply {
        isAllCaps = false; textSize = 14f; setTextColor(AColor.WHITE)
        background = GradientDrawable().apply { setColor(AColor.parseColor("#3C5BA8")); cornerRadius = dp(20).toFloat() }
    }

    init {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(AColor.WHITE); cornerRadius = dp(16).toFloat(); setStroke(dp(1), AColor.parseColor("#E6DFD2"))
            }
        }
        root.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(10) })
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(headline); titles.addView(advertiser)
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        // MediaView ≥ 120×120dp theo yêu cầu của AdMob
        root.addView(media, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { topMargin = dp(8) })
        root.addView(cta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) })
        view.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.headlineView = headline
        view.bodyView = body
        view.iconView = icon
        view.mediaView = media
        view.callToActionView = cta
        view.advertiserView = advertiser
    }

    fun bind(ad: NativeAd) {
        headline.text = ad.headline
        body.text = ad.body.orEmpty()
        body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        advertiser.text = ad.advertiser.orEmpty()
        advertiser.visibility = if (ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
        val ic = ad.icon
        icon.setImageDrawable(ic?.drawable)
        icon.visibility = if (ic == null) View.GONE else View.VISIBLE
        cta.text = ad.callToAction.orEmpty()
        cta.visibility = if (ad.callToAction.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
        media.visibility = if (ad.mediaContent == null) View.GONE else View.VISIBLE
        ad.mediaContent?.let { media.mediaContent = it }
        view.setNativeAd(ad)
    }
}
