package com.huanhuynh.covua.engine

import com.huanhuynh.covua.i18n.t
import kotlin.random.Random

/**
 * Sách khai cuộc nhỏ (SAN quốc tế) + tên thế khai cuộc để thầy gọi tên.
 * Khoá = chuỗi nước đã đi (bỏ dấu +/#), cách nhau dấu cách. Sách = mọi tiền tố của các đường khai cuộc có tên.
 */
object OpeningBook {

    data class Name(val title: String, val note: String)

    private class Entry(val line: String, val vi: String, val en: String, val zh: String, val ja: String,
                        val nvi: String, val nen: String, val nzh: String, val nja: String) {
        val name: Name get() = Name(t(vi, en, zh, ja), t(nvi, nen, nzh, nja))
    }

    private val ENTRIES = listOf(
        Entry("e4 e5 Nf3 Nc6 Bc4", "Khai cuộc Ý", "Italian Game", "意大利开局", "イタリアン・ゲーム",
            "Tượng nhắm ô f7 yếu – phát triển nhanh, nhập thành sớm.", "The Bishop eyes the weak f7 square – quick development, early castling.", "象瞄准薄弱的f7格——快速出子，尽早易位。", "ビショップが弱いf7を狙う – 素早い展開と早めのキャスリング。"),
        Entry("e4 e5 Nf3 Nc6 Bc4 Bc5", "Khai cuộc Ý – Giuoco Piano", "Giuoco Piano", "意大利开局·慢棋变例", "ジュオコ・ピアノ",
            "Cả hai Tượng nhắm f7/f2 – thế cờ êm, tranh trung tâm từ từ.", "Both Bishops aim at f7/f2 – a calm fight for the centre.", "双方的象都瞄准f7/f2——平稳地争夺中心。", "両者のビショップがf7/f2を狙う – 落ち着いた中央争い。"),
        Entry("e4 e5 Nf3 Nc6 Bc4 Nf6", "Phòng thủ Hai Mã", "Two Knights Defence", "双马防御", "ツーナイツ・ディフェンス",
            "Đen phản công Tốt e4 ngay thay vì giữ thế.", "Black counter-attacks e4 at once instead of playing safe.", "黑方立即反击e4兵，而不是求稳。", "黒は安全策より、すぐにe4へ反撃。"),
        Entry("e4 e5 Nf3 Nc6 Bb5", "Khai cuộc Tây Ban Nha (Ruy Lopez)", "Ruy Lopez", "西班牙开局", "ルイ・ロペス",
            "Tượng ép Mã c6 – Mã đang giữ Tốt e5. Một trong những khai cuộc lâu đời nhất.", "The Bishop pressures the c6 Knight that guards e5 – one of the oldest openings.", "象牵制守护e5的c6马——最古老的开局之一。", "e5を守るc6のナイトに圧力 – 最も古い定跡の一つ。"),
        Entry("e4 e5 Nf3 Nc6 Bb5 a6", "Ruy Lopez – Biến Morphy", "Ruy Lopez, Morphy Defence", "西班牙开局·莫菲防御", "ルイ・ロペス モーフィー・ディフェンス",
            "Đen hỏi ngay Tượng: lui hay đổi?", "Black asks the Bishop at once: retreat or trade?", "黑方立即逼问白象：退还是兑？", "黒はすぐにビショップに問う：引くか交換か？"),
        Entry("e4 e5 Nf3 Nc6 d4", "Khai cuộc Scotland", "Scotch Game", "苏格兰开局", "スコッチ・ゲーム",
            "Trắng mở trung tâm ngay nước thứ 3.", "White opens the centre right on move 3.", "白方第三步就打开中心。", "白は3手目で中央を開く。"),
        Entry("e4 e5 Nf3 Nf6", "Phòng thủ Petrov", "Petrov's Defence", "俄罗斯防御", "ペトロフ・ディフェンス",
            "Đen phản công đối xứng – thế cờ chắc chắn.", "Black mirrors with a counter-attack – very solid.", "黑方对称反击——非常稳固。", "黒は対称に反撃 – とても堅実。"),
        Entry("e4 e5 Nf3 d6", "Phòng thủ Philidor", "Philidor Defence", "菲利道尔防御", "フィリドール・ディフェンス",
            "Đen giữ Tốt e5 bằng Tốt d6 – chắc nhưng hơi thụ động.", "Black supports e5 with d6 – solid but a little passive.", "黑方用d6保护e5——稳固但略显被动。", "黒はd6でe5を支える – 堅いがやや受け身。"),
        Entry("e4 e5 f4", "Gambit Vua", "King's Gambit", "王翼弃兵", "キングズ・ギャンビット",
            "Trắng thí Tốt f để mở cột f tấn công – khai cuộc lãng mạn.", "White gives the f-pawn to open the f-file and attack – a romantic opening.", "白方弃f兵打开f线进攻——浪漫主义开局。", "fポーンを捨ててf列を開き攻撃 – ロマンティックな定跡。"),
        Entry("e4 e5 Nc3", "Khai cuộc Vienna", "Vienna Game", "维也纳开局", "ウィーン・ゲーム",
            "Ra Mã trước, giữ khả năng đẩy Tốt f4.", "Knight first, keeping the f4 push in reserve.", "先出马，保留f4挺兵的可能。", "先にナイトを出し、f4突きを残す。"),
        Entry("e4 c5", "Phòng thủ Sicilia", "Sicilian Defence", "西西里防御", "シシリアン・ディフェンス",
            "Đen tranh ô d4 từ cánh – thế cờ không cân bằng, nhiều cơ hội phản công.", "Black fights for d4 from the side – unbalanced, full of counter-play.", "黑方从侧翼争夺d4——不对称，反击机会多。", "黒は側面からd4を争う – アンバランスで反撃のチャンスが多い。"),
        Entry("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6", "Sicilia – Biến Najdorf", "Sicilian Najdorf", "西西里·纳道尔夫变例", "シシリアン ナイドルフ",
            "Nước a6 nhỏ mà linh hoạt – biến yêu thích của Fischer và Kasparov.", "The modest a6 is very flexible – a favourite of Fischer and Kasparov.", "a6看似平淡却很灵活——菲舍尔和卡斯帕罗夫的最爱。", "控えめなa6は柔軟 – フィッシャーとカスパロフが愛用。"),
        Entry("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6", "Sicilia – Biến Rồng", "Sicilian Dragon", "西西里·龙式变例", "シシリアン ドラゴン",
            "Tượng g7 nhắm đường chéo dài như hơi thở của rồng.", "The g7 Bishop breathes fire down the long diagonal.", "g7象沿长斜线喷火，如龙吐息。", "g7のビショップが長い対角線に火を吹く。"),
        Entry("e4 e6", "Phòng thủ Pháp", "French Defence", "法兰西防御", "フレンチ・ディフェンス",
            "Đen chuẩn bị d5 – chuỗi Tốt chắc, phản công ở cánh Hậu.", "Black prepares d5 – a solid pawn chain, counter-play on the queenside.", "黑方准备d5——兵链坚固，后翼反击。", "黒はd5を準備 – 堅いポーンチェーンとクイーンサイドの反撃。"),
        Entry("e4 c6", "Phòng thủ Caro-Kann", "Caro-Kann Defence", "卡罗-卡恩防御", "カロ・カン・ディフェンス",
            "Chuẩn bị d5 mà không nhốt Tượng trắng ô – rất chắc chắn.", "Prepares d5 without locking in the light-squared Bishop – very solid.", "准备d5而不堵住白格象——非常稳固。", "白マスのビショップを閉じ込めずにd5を準備 – とても堅実。"),
        Entry("e4 d5", "Phòng thủ Scandinavia", "Scandinavian Defence", "斯堪的纳维亚防御", "スカンジナビアン・ディフェンス",
            "Đen đánh vào trung tâm ngay nước 1.", "Black hits the centre on move one.", "黑方第一步就冲击中心。", "黒は1手目から中央を攻撃。"),
        Entry("e4 d6 d4 Nf6 Nc3 g6", "Phòng thủ Pirc", "Pirc Defence", "皮尔茨防御", "ピルツ・ディフェンス",
            "Đen để Trắng chiếm trung tâm rồi phá từ xa.", "Black lets White take the centre, then attacks it from afar.", "黑方让白方占据中心，再从远处攻击。", "黒は白に中央を取らせ、遠くから崩す。"),
        Entry("e4 Nf6", "Phòng thủ Alekhine", "Alekhine's Defence", "阿廖欣防御", "アレヒン・ディフェンス",
            "Mã khiêu khích Tốt trắng tiến lên rồi tấn công chúng.", "The Knight provokes White's pawns forward, then attacks them.", "马引诱白兵前进，然后攻击它们。", "ナイトで白のポーンを前に誘い、それを攻撃。"),
        Entry("d4 d5 c4", "Gambit Hậu", "Queen's Gambit", "后翼弃兵", "クイーンズ・ギャンビット",
            "Trắng mời đổi Tốt c lấy trung tâm – không thật sự thí quân.", "White offers the c-pawn for the centre – not a real sacrifice.", "白方以c兵换取中心——并非真正的弃子。", "cポーンを差し出して中央を得る – 本当の犠牲ではない。"),
        Entry("d4 d5 c4 e6", "Gambit Hậu từ chối", "Queen's Gambit Declined", "后翼弃兵拒绝变例", "クイーンズ・ギャンビット・ディクラインド",
            "Đen giữ chắc Tốt d5 – cổ điển và vững vàng.", "Black holds d5 firmly – classical and sturdy.", "黑方牢牢守住d5——古典而稳健。", "黒はd5をしっかり守る – 古典的で頑丈。"),
        Entry("d4 d5 c4 dxc4", "Gambit Hậu chấp nhận", "Queen's Gambit Accepted", "后翼弃兵接受变例", "クイーンズ・ギャンビット・アクセプテッド",
            "Đen ăn Tốt c4 rồi trả lại để phát triển nhanh.", "Black takes on c4, planning to give it back for quick development.", "黑方吃c4兵，准备之后还回去以换取快速出子。", "黒はc4を取り、返して素早く展開する。"),
        Entry("d4 d5 c4 c6", "Phòng thủ Slav", "Slav Defence", "斯拉夫防御", "スラブ・ディフェンス",
            "Giữ d5 bằng c6, Tượng c8 vẫn ra được.", "Supports d5 with c6 while the c8 Bishop stays free.", "用c6保护d5，c8象仍可出动。", "c6でd5を支え、c8のビショップも自由。"),
        Entry("d4 d5 Bf4", "Hệ thống London", "London System", "伦敦体系", "ロンドン・システム",
            "Một sơ đồ dễ nhớ: d4, Bf4, e3, Nf3, c3 – chơi được với mọi phòng thủ.", "An easy set-up: d4, Bf4, e3, Nf3, c3 – playable against anything.", "易记的布局：d4、Bf4、e3、Nf3、c3——几乎对付一切防御。", "覚えやすい形：d4、Bf4、e3、Nf3、c3 – どんな受けにも使える。"),
        Entry("d4 Nf6 c4 g6 Nc3 Bg7 e4 d6", "Phòng thủ Ấn Độ Vua", "King's Indian Defence", "古印度防御", "キングズ・インディアン・ディフェンス",
            "Đen nhường trung tâm rồi tấn công Vua – khai cuộc của Fischer, Kasparov.", "Black cedes the centre, then storms the king – Fischer's and Kasparov's weapon.", "黑方让出中心再进攻王翼——菲舍尔、卡斯帕罗夫的武器。", "中央を譲ってからキングを攻める – フィッシャーやカスパロフの武器。"),
        Entry("d4 Nf6 c4 e6 Nc3 Bb4", "Phòng thủ Nimzo-Ấn", "Nimzo-Indian Defence", "尼姆佐-印度防御", "ニムゾ・インディアン・ディフェンス",
            "Tượng ghim Mã c3 để kiểm soát ô e4.", "The Bishop pins the c3 Knight to control e4.", "象牵制c3马以控制e4。", "ビショップがc3のナイトをピンしてe4を支配。"),
        Entry("d4 Nf6 c4 e6 Nf3 b6", "Phòng thủ Ấn Độ Hậu", "Queen's Indian Defence", "新印度防御", "クイーンズ・インディアン・ディフェンス",
            "Tượng b7 kiểm soát ô e4 từ xa.", "The b7 Bishop controls e4 from afar.", "b7象从远处控制e4。", "b7のビショップが遠くからe4を支配。"),
        Entry("d4 Nf6 c4 g6 Nc3 d5", "Phòng thủ Grünfeld", "Grünfeld Defence", "格林菲尔德防御", "グリュンフェルト・ディフェンス",
            "Đen để Trắng dựng trung tâm lớn rồi phá nó.", "Black lets White build a big centre, then breaks it down.", "黑方让白方建立大中心，然后摧毁它。", "白に大きな中央を作らせ、それを崩す。"),
        Entry("d4 Nf6 c4 e6 g3", "Khai cuộc Catalan", "Catalan Opening", "卡塔兰开局", "カタラン・オープニング",
            "Tượng g2 ép đường chéo dài – lối chơi thế trận tinh tế.", "The g2 Bishop presses down the long diagonal – subtle positional play.", "g2象压制长斜线——细腻的局面型下法。", "g2のビショップが長い対角線を押さえる – 繊細な陣形戦。"),
        Entry("d4 Nf6 c4 c5 d5", "Phòng thủ Benoni", "Benoni Defence", "别诺尼防御", "ベノニ・ディフェンス",
            "Thế Tốt lệch: Trắng hơn trung tâm, Đen có cánh Hậu.", "Unbalanced pawns: White has the centre, Black the queenside.", "兵形不对称：白方占中心，黑方占后翼。", "非対称のポーン：白は中央、黒はクイーンサイド。"),
        Entry("d4 f5", "Phòng thủ Hà Lan", "Dutch Defence", "荷兰防御", "ダッチ・ディフェンス",
            "Đen giành ô e4 bằng Tốt f – đánh nhau sắc bén.", "Black grabs e4 with the f-pawn – a sharp fight.", "黑方用f兵争夺e4——激烈的对攻。", "fポーンでe4を押さえる – 鋭い戦い。"),
        Entry("c4", "Khai cuộc Anh", "English Opening", "英国式开局", "イングリッシュ・オープニング",
            "Kiểm soát ô d5 từ cánh – linh hoạt, dễ chuyển thế.", "Controls d5 from the flank – flexible, with many transpositions.", "从侧翼控制d5——灵活，易转换。", "側面からd5を支配 – 柔軟で他の定跡にも移行しやすい。"),
        Entry("Nf3 d5 c4", "Khai cuộc Réti", "Réti Opening", "列蒂开局", "レティ・オープニング",
            "Đánh trung tâm từ xa bằng Mã và Tốt c.", "Attacks the centre from afar with the Knight and c-pawn.", "用马和c兵从远处攻击中心。", "ナイトとcポーンで遠くから中央を攻める。"),
        Entry("e4", "Khai cuộc Tốt Vua", "King's Pawn Opening", "王兵开局", "キングズ・ポーン・オープニング",
            "Chiếm trung tâm, mở đường cho Hậu và Tượng – nước đi phổ biến nhất.", "Takes the centre and frees the Queen and Bishop – the most popular first move.", "占据中心，为后和象打开道路——最常见的第一步。", "中央を取りクイーンとビショップの道を開く – 最も人気の初手。"),
        Entry("d4", "Khai cuộc Tốt Hậu", "Queen's Pawn Opening", "后兵开局", "クイーンズ・ポーン・オープニング",
            "Chiếm trung tâm, Tốt d được Hậu bảo vệ – thế cờ chắc chắn.", "Takes the centre with a pawn already guarded by the Queen – solid.", "占据中心，d兵有后保护——稳健。", "クイーンに守られたポーンで中央を取る – 堅実。"),
        Entry("e4 e5", "Khai cuộc Mở", "Open Game", "开放性布局", "オープン・ゲーム",
            "Đen đáp đối xứng – thế cờ mở, quân hoạt động nhanh.", "Black answers symmetrically – open lines, active pieces.", "黑方对称应对——线路开放，子力活跃。", "黒は対称に応じる – 開放的で駒が活発。"),
        Entry("e4 e5 Nf3 Nc6", "Khai cuộc Mã Vua", "King's Knight Opening", "王翼马开局", "キングズ・ナイト・オープニング",
            "Cả hai ra Mã tranh và giữ Tốt e5.", "Both sides develop Knights to fight for e5.", "双方出马争夺e5。", "両者がナイトを出してe5を争う。"),
    )

    private fun key(moves: List<String>): String = moves.joinToString(" ") { it.trimEnd('+', '#') }

    /** Sách: tiền tố → các nước tiếp theo (có trọng số theo số đường đi qua). */
    private val BOOK: Map<String, List<String>> by lazy {
        val m = HashMap<String, MutableList<String>>()
        for (e in ENTRIES) {
            val mv = e.line.split(' ')
            for (i in mv.indices) m.getOrPut(mv.subList(0, i).joinToString(" ")) { ArrayList() } += mv[i]
        }
        // Đa dạng nước 1 của Trắng
        m.getOrPut("") { ArrayList() }.addAll(listOf("e4", "e4", "d4", "d4", "Nf3"))
        m
    }

    /** Danh mục khai cuộc (mỗi tên một lần, kèm chuỗi nước tiêu biểu) cho màn Khai cuộc. */
    fun catalog(): List<Pair<List<String>, Name>> =
        ENTRIES.filter { it.line.contains(' ') }.distinctBy { it.en }.map { it.line.split(' ') to it.name }

    /** Tên khai cuộc vừa hình thành đúng sau chuỗi [moves] (null nếu không có tên mới). */
    fun nameExact(moves: List<String>): Name? {
        val k = key(moves)
        return ENTRIES.firstOrNull { it.line == k }?.name
    }

    /** Tên khai cuộc dài nhất khớp phần đầu ván. */
    fun nameOf(moves: List<String>): Name? {
        val k = key(moves)
        return ENTRIES.filter { k == it.line || k.startsWith(it.line + " ") }.maxByOrNull { it.line.length }?.name
    }

    fun isBookLine(moves: List<String>): Boolean = BOOK.containsKey(key(moves))

    /** Các nước sách (SAN) cho chuỗi [moves]. */
    fun bookMoves(moves: List<String>): List<String> = BOOK[key(moves)]?.distinct() ?: emptyList()

    /** Chọn nước sách cho thế [pos] (dựng từ thế đầu). Cần [history] các nước đã đi (SAN). */
    fun pick(pos: Position, random: Random, history: List<String>? = null): Int? {
        val h = history ?: return null
        val options = BOOK[key(h)] ?: return null
        val code = options[random.nextInt(options.size)]
        return pos.legalMoves().firstOrNull { Notation.of(pos, it).trimEnd('+', '#') == code }
    }
}
