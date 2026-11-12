package com.huanhuynh.covua.training

import com.huanhuynh.covua.i18n.t

/** Danh hiệu trong app theo cấp cao nhất đã thắng khi KHÔNG chấp. Chỉ lên, không tụt. Không dùng tên phong cấp chính thức. */
object Titles {
    private val NAMES_VI = listOf("Tân thủ", "Học trò", "Kỳ hữu", "Tay cờ câu lạc bộ", "Tay cờ phường", "Cao thủ phường", "Cao thủ quận", "Kỳ tài", "Đại kỳ tài")
    private val NAMES_EN = listOf("Newcomer", "Student", "Chess friend", "Club player", "Ward player", "Ward champion", "District champion", "Prodigy", "Grand prodigy")
    private val NAMES_ZH = listOf("新手", "学生", "棋友", "俱乐部棋手", "街坊棋手", "街坊高手", "区级高手", "棋才", "大棋才")
    private val NAMES_JA = listOf("新人", "弟子", "チェス仲間", "クラブの常連", "町内の指し手", "町内の強豪", "地区の強豪", "天才", "大天才")
    private val NAMES: List<String> get() = when (com.huanhuynh.covua.i18n.I18n.lang) {
        com.huanhuynh.covua.i18n.Lang.EN -> NAMES_EN
        com.huanhuynh.covua.i18n.Lang.ZH -> NAMES_ZH
        com.huanhuynh.covua.i18n.Lang.JA -> NAMES_JA
        else -> NAMES_VI
    }

    /** [highestLevelWon] = 0 nếu chưa thắng ván nào. */
    fun of(highestLevelWon: Int): String = NAMES[highestLevelWon.coerceIn(0, 8)]

    val SUBTITLE: String get() = t("danh hiệu trong app", "in-app title", "应用内称号", "アプリ内の称号")
}

/**
 * Xin hoà: máy nhận khi đã qua nước [minMove] (nước = cặp nửa nước) VÀ máy không hơn quá [maxAiAdvantageCp].
 * Bị từ chối → phải chờ [cooldownMoves] nước mới xin lại.
 */
class DrawPolicy(private val minMove: Int = 30, private val maxAiAdvantageCp: Int = 50, private val cooldownMoves: Int = 10) {
    sealed interface Decision {
        data object Accept : Decision
        data class Decline(val reason: String) : Decision
        data class TooEarly(val reason: String) : Decision
    }

    fun canOffer(plies: Int, lastDeclinedAtPly: Int?): Boolean =
        lastDeclinedAtPly == null || plies - lastDeclinedAtPly >= cooldownMoves * 2

    fun decide(plies: Int, aiScoreCp: Int, aiMateWin: Boolean): Decision {
        val move = plies / 2 + 1
        if (move <= minMove) return Decision.TooEarly(t(
            "Mới nước $move – còn sớm, máy muốn đánh tiếp đã. Qua nước $minMove hãy xin hoà nhé.",
            "Only move $move – too early, the computer wants to play on. Offer a draw after move $minMove.",
            "才第${move}步——还早，电脑想继续下。第${minMove}步之后再提和吧。",
        ))
        if (aiMateWin || aiScoreCp > maxAiAdvantageCp) {
            val a = advantage(aiScoreCp, aiMateWin)
            return Decision.Decline(t("Máy đang hơn $a nên chưa chịu hoà.", "The computer is ahead by $a, so it declines.", "电脑领先${a}，所以不同意和棋。", "コンピュータが${a}リードしているので、引き分けは断ります。"))
        }
        return Decision.Accept
    }

    private fun advantage(cp: Int, mate: Boolean): String = when {
        mate -> t("rất nhiều (thấy đòn chiếu hết)", "a lot (it sees a mate)", "很多（看到了杀棋）", "かなり（メイトが見えている）")
        cp >= 800 -> t("khoảng một Hậu", "about a Queen", "约一个后", "クイーン1枚ほど")
        cp >= 450 -> t("khoảng một Xe", "about a Rook", "约一个车", "ルーク1枚ほど")
        cp >= 270 -> t("khoảng một Mã", "about a Knight", "约一个马", "ナイト1枚ほど")
        cp >= 150 -> t("khoảng hai Tốt", "about two Pawns", "约两兵", "ポーン2枚ほど")
        else -> t("một chút", "a little", "一点", "少し")
    }
}

/** Luật "ngày được tính" cho Lịch tập. */
object StreakRules {
    fun gameCounts(plies: Int, minMoves: Int = 10) = plies / 2 >= minMoves
}
