package com.huanhuynh.covua.notify

import com.huanhuynh.covua.i18n.t

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.huanhuynh.covua.CoVuaApp
import com.huanhuynh.covua.MainActivity
import com.huanhuynh.covua.R
import com.huanhuynh.covua.data.FirebaseHelper
import com.huanhuynh.covua.data.RcKeys
import com.huanhuynh.covua.domain.Ev
import com.huanhuynh.covua.training.ReminderPolicy
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * "Thầy nhắc": mỗi ngày tối đa 1 thông báo cục bộ. Dùng OneTimeWorkRequest đặt lại mỗi ngày (không cần quyền báo thức chính xác).
 * Worker chỉ ĐỌC dữ liệu (không chạy engine).
 */
class ReminderScheduler(private val context: Context) {

    fun policy(): ReminderPolicy = ReminderPolicy(
        quietStartMinute = FirebaseHelper.getLong(context, RcKeys.NOTIF_QUIET_START).toInt() * 60,
        quietEndMinute = ReminderPolicy.parseQuietEnd(FirebaseHelper.getString(context, RcKeys.NOTIF_QUIET_END).toDoubleOrNull() ?: 7.5),
        reduceAfter = FirebaseHelper.getLong(context, RcKeys.NOTIF_IGNORE_REDUCE_AFTER).toInt(),
        stopAfter = FirebaseHelper.getLong(context, RcKeys.NOTIF_IGNORE_STOP_AFTER).toInt(),
    )

    fun enabledByConfig() = FirebaseHelper.v11(context, RcKeys.NOTIF_ENABLED)

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Đặt lần nhắc kế tiếp vào phút [targetMinute] (hôm nay nếu còn kịp, không thì ngày mai). */
    fun schedule(targetMinute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabledByConfig()) { wm.cancelUniqueWork(WORK); return }
        val minute = policy().effectiveMinute(targetMinute)
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var at = now.toLocalDate().atTime(minute / 60, minute % 60)
        if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
        val delay = java.time.Duration.between(now, at).toMillis()
        wm.enqueueUniqueWork(
            WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    fun cancel() = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        // Hai kênh tách riêng để người dùng tắt từng loại
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, t("Ôn bài & buổi tập", "Review & daily practice", "复习与每日练习", "復習と毎日の練習"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t("Thầy nhắc ôn Sổ tay và Buổi tập hôm nay – tối đa 1 lần/ngày", "Reminders for your notebook and today's practice – at most once a day", "提醒复习错题本和今日练习——每天最多一次", "ミスノートと今日の練習のお知らせ – 1日最大1回")
        })
        nm.createNotificationChannel(NotificationChannel(CH_GAME, t("Ván dở", "Unfinished games", "未完成棋局", "指しかけの対局"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t("Nhắc ván cờ với máy còn dang dở", "Reminds you of an unfinished game", "提醒未下完的人机棋局", "指しかけの対局をお知らせします")
        })
    }

    fun post(n: ReminderPolicy.Notice) {
        if (!hasPermission()) return
        ensureChannels()
        val target = when (n.type) {
            ReminderPolicy.Type.REVIEW -> DEST_REVIEW
            ReminderPolicy.Type.GAME -> DEST_GAME
            else -> DEST_DAILY
        }
        // Deep link mở được cả khi app đã bị tắt hẳn
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEST, target)
            putExtra(EXTRA_FROM_NOTIF, n.type.name)
        }
        val pi = PendingIntent.getActivity(context, target.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif = NotificationCompat.Builder(context, if (n.type == ReminderPolicy.Type.GAME) CH_GAME else CH_DAILY)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notif) }
    }

    companion object {
        const val WORK = "thay_nhac"
        const val CH_DAILY = "daily"
        const val CH_GAME = "game"
        const val NOTIF_ID = 1901
        const val EXTRA_DEST = "dest"
        const val EXTRA_FROM_NOTIF = "from_notif"
        const val DEST_DAILY = "daily"
        const val DEST_REVIEW = "review"
        const val DEST_GAME = "game"
        // Khoá trạng thái (DataStore)
        const val K_LAST_SENT = "notif_last_sent_day"
        const val K_IGNORED = "notif_ignored"
        const val K_STOP_ASKED = "notif_stop_asked"
        const val K_PROMPTED_AT = "notif_prompted_day"
        const val K_PROMPT_COUNT = "notif_prompt_count"
        /** 7 lần bị bỏ qua → dừng và hỏi lại 1 lần trong app. */
        const val K_STOP_PENDING = "notif_stop_pending"
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CoVuaApp).container
        val sch = c.reminders
        val s = c.settings.current()
        val today = c.clock.today()
        val policy = sch.policy()
        runCatching {
            val lastSent = c.settings.getLongKey(ReminderScheduler.K_LAST_SENT, -1).takeIf { it >= 0 }
            var ignored = c.settings.getInt(ReminderScheduler.K_IGNORED)
            val snap = c.activity.current()
            // Lần nhắc trước có được đáp lại (mở app từ hôm đó trở đi) không
            if (lastSent != null && snap.days.any { it.epochDay >= lastSent && it.firstOpenMinute != null }) ignored = 0
            val freqBefore = policy.frequency(ignored)
            val send = policy.shouldSend(today, c.clock.minuteOfDay(), s.notifOn && sch.enabledByConfig() && sch.hasPermission(), snap.activeToday, lastSent, ignored)
            if (send) {
                val due = c.reviews.due().size
                val inProgress = c.archive.inProgress()?.let { g ->
                    // Đánh giá đã lưu ở nước gần nhất của người chơi
                    val last = c.archive.evals(g.id).lastOrNull()
                    t("Ván với máy cấp ${g.level} còn dang dở", "Your level ${g.level} game is unfinished", "${g.level}级人机棋局还没下完", "レベル${g.level}との対局が指しかけです") + (last?.let { " – " + advantageText(it.scoreAfter) } ?: t(" – thầy vẫn nhớ từng nước", " – your coach remembers every move", "——老师记得每一步", " – 先生は全手を覚えています"))
                }
                val content = runCatching { c.content.get() }.getOrNull()
                val dailyText = c.daily.dailyPuzzleId()?.let { (id, n) ->
                    run { val wd = com.huanhuynh.covua.training.DailyPlanner.weekdayName(today); t("Bài $wd: chiếu hết $n nước", "$wd puzzle: mate in $n", "${wd}题：${n}步杀", "${wd}の問題：${n}手メイト") } +
                        (content?.puzzle(id)?.themeName?.let { t(" – chủ đề $it", " – theme: $it", "——主题：$it", " – テーマ：${it}") } ?: "")
                }
                val week = snap.weekDone.takeIf { it > 0 && !s.hideStreak && FirebaseHelper.v11(applicationContext, RcKeys.STREAK_ENABLED) }
                val notice = policy.pickContent(due, inProgress, dailyText, week, today.toInt())
                if (notice != null) {
                    sch.post(notice)
                    c.settings.setLongKey(ReminderScheduler.K_LAST_SENT, today)
                    ignored++
                    c.events.log(Ev.NOTIF_SHOWN, mapOf("type" to notice.type.name.lowercase()))
                }
            }
            c.settings.setInt(ReminderScheduler.K_IGNORED, ignored)
            val freqAfter = policy.frequency(ignored)
            if (freqAfter != freqBefore) when (freqAfter) {
                ReminderPolicy.Frequency.TWICE_WEEKLY -> c.events.log(Ev.NOTIF_REDUCED)
                ReminderPolicy.Frequency.STOPPED -> {
                    c.events.log(Ev.NOTIF_STOPPED)
                    if (c.settings.getInt(ReminderScheduler.K_STOP_PENDING) == 0) c.settings.setInt(ReminderScheduler.K_STOP_PENDING, 1)
                }
                else -> Unit
            }
        }
        // Đặt lại cho ngày mai
        sch.schedule(targetMinute(c))
        return Result.success()
    }

    companion object {
        fun advantageText(cp: Int): String = when {
            cp >= 800 -> t("bạn đang hơn khoảng một Hậu", "you're about a Queen up", "你大约多一个后", "クイーン1枚ほど優勢")
            cp >= 450 -> t("bạn đang hơn khoảng một Xe", "you're about a Rook up", "你大约多一个车", "ルーク1枚ほど優勢")
            cp >= 270 -> t("bạn đang hơn một Mã", "you're a Knight up", "你多一个马", "ナイト1枚優勢")
            cp >= 150 -> t("bạn đang hơn chút ít", "you're slightly ahead", "你稍占上风", "あなたが少しリード")
            cp > -150 -> t("thế cờ đang cân bằng", "the position is equal", "局面均势", "形勢は互角")
            cp > -350 -> t("bạn đang kém chút ít, còn gỡ được", "you're slightly behind, still saveable", "你稍落后，还能挽回", "少し遅れていますが、まだ挽回できます")
            else -> t("đang khó, thử tìm cách gỡ nhé", "it's tough – try to find a way back", "局面困难，试着扳回吧", "苦しい形勢 – 逆転の道を探しましょう")
        }

        suspend fun targetMinute(c: com.huanhuynh.covua.AppContainer): Int {
            val s = c.settings.current()
            val def = FirebaseHelper.getLong(c.appContext, RcKeys.NOTIF_DEFAULT_HOUR).toInt() * 60
            return when {
                s.notifAuto -> c.reminders.policy().medianMinute(runCatching { c.activity.openMinutes(14) }.getOrDefault(emptyList()), def)
                s.notifMinute >= 0 -> s.notifMinute
                else -> def
            }
        }
    }
}
