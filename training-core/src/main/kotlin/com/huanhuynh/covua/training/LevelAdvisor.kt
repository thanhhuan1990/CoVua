package com.huanhuynh.covua.training

import com.huanhuynh.covua.i18n.t

import com.huanhuynh.covua.engine.Handicap

/** Một ván đã xong, để thầy gợi ý cấp. */
data class FinishedGame(
    val id: Long,
    val result: Int, // 1 thắng, 0 hoà, −1 thua
    val level: Int,
    val handicap: Handicap,
    val fromPosition: Boolean = false,
    val legacy: Boolean = false,
    /** Xin thua trước nước 10 (không tính). */
    val earlyResign: Boolean = false,
)

data class LevelSuggestion(
    val type: Type,
    val text: String,
    val options: List<Option>,
) {
    enum class Type { EASIER, HARDER, ENCOURAGE }
    data class Option(val label: String, val level: Int, val handicap: Handicap, val enableHints: Boolean = false)
}

/**
 * Thầy gợi ý cấp (minh bạch, người chơi luôn tự chọn): thua [lossStreak] ván liền cùng cấp + chấp → gợi ý dễ hơn;
 * thắng [winStreak] ván liền → gợi ý khó hơn. Từ chối → [cooldownGames] ván sau mới hỏi lại.
 */
class LevelAdvisor(private val lossStreak: Int = 3, private val winStreak: Int = 3, private val cooldownGames: Int = 5) {

    /**
     * [recent] = các ván gần nhất, MỚI NHẤT TRƯỚC. [gamesSinceDecline] = số ván (được tính) kể từ lần từ chối gần nhất
     * (null = chưa từ chối).
     */
    fun advise(recent: List<FinishedGame>, gamesSinceDecline: Int?): LevelSuggestion? {
        if (gamesSinceDecline != null && gamesSinceDecline < cooldownGames) return null
        val counted = recent.filter { !it.fromPosition && !it.legacy && !it.earlyResign }
        val last = counted.firstOrNull() ?: return null
        val same = counted.takeWhile { it.level == last.level && it.handicap == last.handicap }
        val lv = last.level
        val h = last.handicap
        if (same.size >= lossStreak && same.take(lossStreak).all { it.result == -1 }) {
            if (lv <= 1 && h == Handicap.AI_QUEEN) {
                return LevelSuggestion(
                    LevelSuggestion.Type.ENCOURAGE,
                    t("Đừng nản – ai mới tập cũng thua nhiều. Bật gợi ý và đi chậm lại, thầy chỉ từng nước cho.",
                        "Don't give up – everyone loses a lot at first. Turn on hints and slow down; I'll guide you move by move.",
                        "别灰心——初学者都会输很多。打开提示、慢慢走，老师一步步教你。", "あきらめないで – 最初は誰でもたくさん負けます。ヒントをオンにしてゆっくり指しましょう。先生が一手ずつ導きます。"),
                    listOf(LevelSuggestion.Option(t("Bật gợi ý", "Turn on hints", "打开提示", "ヒントをオン"), lv, h, enableHints = true)),
                )
            }
            val opts = ArrayList<LevelSuggestion.Option>()
            val easier = easierHandicap(h)
            if (easier != null) opts += LevelSuggestion.Option(easier.label, lv, easier)
            val down = lv - 1
            if (lv > 1) opts += LevelSuggestion.Option(t("Cấp $down", "Level $down", "${down}级", "レベル${down}"), down, h)
            val text = if (h == Handicap.NONE) {
                t("Thầy thấy máy cấp $lv đang hơi khó. Mình thử cho máy chấp Mã, hoặc xuống cấp $down vài ván nhé?",
                    "Level $lv looks a bit hard right now. Shall we give you knight odds, or drop to level $down for a few games?",
                    "${lv}级电脑现在有点难。让电脑让一马，或者降到${down}级下几盘吧？", "レベル${lv}は今少し難しいようです。コンピュータにナイト落ちにしてもらうか、何局かレベル${down}に下げてみませんか？")
            } else {
                val hl = h.label
                if (lv > 1) t("Thầy thấy ván ${hl.lowercase()} ở cấp $lv vẫn còn khó. Thử chấp thêm một bậc, hoặc xuống cấp $down nhé?",
                    "\"$hl\" at level $lv is still hard. Try one more handicap step, or drop to level $down?",
                    "${lv}级的「${hl}」仍然偏难。再多让一级，或者降到${down}级吧？", "レベル${lv}の「${hl}」はまだ難しいようです。ハンデをもう1段増やすか、レベル${down}に下げてみませんか？")
                else t("Thầy thấy ván ${hl.lowercase()} ở cấp $lv vẫn còn khó. Thử chấp thêm một bậc nhé?",
                    "\"$hl\" at level $lv is still hard. Try one more handicap step?", "${lv}级的「${hl}」仍然偏难。再多让一级吧？", "レベル${lv}の「${hl}」はまだ難しいようです。ハンデをもう1段増やしてみませんか？")
            }
            return LevelSuggestion(LevelSuggestion.Type.EASIER, text, opts)
        }
        if (same.size >= winStreak && same.take(winStreak).all { it.result == 1 }) {
            if (h != Handicap.NONE && h.byAi) {
                val harder = harderHandicap(h) ?: Handicap.NONE
                return LevelSuggestion(
                    LevelSuggestion.Type.HARDER, t("Ba ván thắng liền rồi! Bớt chấp một bậc nhé?", "Three wins in a row! Reduce the handicap one step?", "三连胜了！少让一级吧？", "3連勝！ハンデを1段減らしますか？"),
                    listOf(LevelSuggestion.Option(harder.label, lv, harder)),
                )
            }
            if (lv >= 8) {
                val harder = harderHandicap(h) ?: return null
                return LevelSuggestion(
                    LevelSuggestion.Type.HARDER, t("Ba ván thắng liền máy Đại kiện tướng! Thử chấp máy một quân xem?", "Three wins in a row against the Grandmaster! Try giving it piece odds?", "三连胜特级大师！试试让电脑一子？", "グランドマスターに3連勝！コンピュータに駒落ちしてみませんか？"),
                    listOf(LevelSuggestion.Option(harder.label, lv, harder)),
                )
            }
            return LevelSuggestion(
                LevelSuggestion.Type.HARDER, (lv + 1).let { t("Ba ván thắng liền rồi! Thử lên cấp $it?", "Three wins in a row! Try level $it?", "三连胜了！试试${it}级？", "3連勝！レベル${it}に挑戦しますか？") },
                listOf(LevelSuggestion.Option((lv + 1).let { t("Lên cấp $it", "Level $it", "升到${it}级", "レベル${it}へ") }, lv + 1, h)),
            )
        }
        return null
    }

    companion object {
        /** Dễ hơn một bậc (với người chơi). Không chấp → máy chấp Mã (chấp Tốt quá nhẹ để giúp được). */
        fun easierHandicap(h: Handicap): Handicap? {
            if (h == Handicap.NONE) return Handicap.AI_KNIGHT
            val r = Handicap.rung(h)
            return if (r <= 0) null else Handicap.LADDER[r - 1].first()
        }

        fun harderHandicap(h: Handicap): Handicap? {
            val r = Handicap.rung(h)
            return if (r >= Handicap.LADDER.lastIndex) null else Handicap.LADDER[r + 1].first()
        }
    }
}
