"""Ảnh Google Play cho Cờ Vua Có Thầy (com.huanhuynh.covua) – 4 ngôn ngữ vi / en / zh / ja.
Chạy: python3 store-assets/source/build_assets.py   (cần: pip install playwright && playwright install chromium,
hoặc đặt CHROME=/đường/dẫn/chrome; font Inter + Noto Sans CJK). Màn hình dựng lại bằng HTML/SVG theo màu và bố cục
của app (Theme.kt, BoardView.kt, PieceArt.kt, HomeScreen.kt, GameScreen.kt…); quân cờ lấy từ tools/pieces/gen.py
(cùng hình với app). Thế cờ thật: bẫy Blackburne–Shilling (khai cuộc Ý), bài 209 trong kho bài. Không có quảng cáo.
Ra: screenshots/<lang>/covua-<lang>-NN-*.png (1080x1920) và covua-feature-graphic-<lang>-1024x500.png.
"""
import base64, importlib.util, math, os
from playwright.sync_api import sync_playwright

SRC = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(SRC, ".."))
ROOT = os.path.abspath(os.path.join(OUT, ".."))
ICON = "data:image/png;base64," + base64.b64encode(open(os.path.join(OUT, "icon-512.png"), "rb").read()).decode()
LANGS = ("vi", "en", "zh", "ja")

_spec = importlib.util.spec_from_file_location("pieces", os.path.join(ROOT, "tools", "pieces", "gen.py"))
PG = importlib.util.module_from_spec(_spec); _spec.loader.exec_module(PG)

# ---------------------------------------------------------------- thế cờ (FEN, phần xếp quân)
# Ván với máy cấp 4, người chơi cầm Trắng: 1.e4 e5 2.Nf3 Nc6 3.Bc4 Nd4 4.Nxe5? (…Qg5! đánh Mã e5 và Tốt g2). Nên: 4.Nxd4.
COACH = "r1bqkbnr/pppp1ppp/8/4N3/2BnP3/8/PPPP1PPP/RNBQK2R"
BEFORE = "r1bqkbnr/pppp1ppp/8/4p3/2BnP3/5N2/PPPP1PPP/RNBQK2R"
# Cảnh báo: 1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 4.c3 Nf6 – cầm Tượng c4, định ăn f7.
WARN = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R"
# Bài 209: Trắng đi, chiếu hết 2 nước (Nc6+ bxc6 Qc7#).
PUZZLE = "rk5q/1p2NQ2/3pN2P/p7/4P3/8/1PP1KP1P/1R5R"
FINAL = "rk5q/2Q5/2ppN2P/p7/4P3/8/1PP1KP1P/1R5R"

PIECE = {'k': 1, 'q': 2, 'r': 3, 'b': 4, 'n': 5, 'p': 6}
def placement(fen):
    out = {}
    for r, row in enumerate(fen.split()[0].split("/")):
        c = 0
        for ch in row:
            if ch.isdigit(): c += int(ch); continue
            out[f"{'abcdefgh'[c]}{8 - r}"] = (PIECE[ch.lower()], ch.isupper())
            c += 1
    return out

def piece_svg(t, white, x, y, size, uid=""):
    fill, line, det = ('#FBF7EE', '#2A2420', '#2A2420') if white else ('#2A2A2D', '#0B0B0B', '#E3DCCD')
    s = size / 100
    o = [f"<g transform='translate({x - 50 * s:.2f},{y - 47 * s:.2f}) scale({s:.4f})'>",
         "<g transform='translate(1.5,3)' fill='#000' fill-opacity='.25'>" + "".join(PG.svg_shape(q) for q in PG.P[t]) + "</g>"]
    for q in PG.P[t]:
        o.append(PG.svg_shape(q).replace("/>", f" fill='{fill}' stroke='{line}' stroke-width='3.4' stroke-linejoin='round'/>"))
    for d in PG.D[t]:
        o.append(f"<path d='{d}' fill='none' stroke='{det}' stroke-width='3' stroke-linecap='round'/>")
    for (cx, cy, r) in PG.DOTS.get(t, []):
        o.append(f"<circle cx='{cx}' cy='{cy}' r='{r}' fill='{det}'/>")
    o.append("</g>")
    return "".join(o)

def board_svg(fen, w, coords=True, last=None, selected=None, targets=(), danger=(), arrows=(), hint=None, frame=True):
    """Bàn gỗ như BoardView: ô kem F0D9B5 / nâu B58863, khung 6B3E1E, nước vừa đi tô vàng."""
    fr = w * 0.028 if frame else 0
    cell = (w - 2 * fr) / 8
    def tl(sq):
        c = "abcdefgh".index(sq[0]); r = 8 - int(sq[1])
        return fr + c * cell, fr + r * cell
    ctr = lambda sq: (tl(sq)[0] + cell / 2, tl(sq)[1] + cell / 2)
    o = [f"<svg width='{w}' height='{w}' viewBox='0 0 {w} {w}' xmlns='http://www.w3.org/2000/svg'>"]
    if frame: o.append(f"<rect width='{w}' height='{w}' rx='{w * .02}' fill='#6B3E1E'/>")
    for r in range(8):
        for c in range(8):
            o.append(f"<rect x='{fr + c * cell:.2f}' y='{fr + r * cell:.2f}' width='{cell + .3:.2f}' height='{cell + .3:.2f}' fill='{'#F0D9B5' if (r + c) % 2 == 0 else '#B58863'}'/>")
    for sq in (last or ()):
        x, y = tl(sq); o.append(f"<rect x='{x:.2f}' y='{y:.2f}' width='{cell:.2f}' height='{cell:.2f}' fill='#F2D33B' fill-opacity='.4'/>")
    if selected:
        x, y = tl(selected); o.append(f"<rect x='{x:.2f}' y='{y:.2f}' width='{cell:.2f}' height='{cell:.2f}' fill='#2E7D4F' fill-opacity='.35'/>")
    if hint:
        x, y = tl(hint); o.append(f"<rect x='{x + 2:.2f}' y='{y + 2:.2f}' width='{cell - 4:.2f}' height='{cell - 4:.2f}' rx='{cell * .12}' fill='rgba(21,101,192,.22)' stroke='#1565C0' stroke-width='{cell * .07}'/>")
    for sq in danger:
        x, y = tl(sq); o.append(f"<rect x='{x:.2f}' y='{y:.2f}' width='{cell:.2f}' height='{cell:.2f}' fill='#C62828' fill-opacity='.45'/>")
    if coords:
        fs = cell * .2
        for i in range(8):
            light_l = (i % 2 == 0)
            o.append(f"<text x='{fr + cell * .07:.2f}' y='{fr + i * cell + fs * 1.05:.2f}' font-size='{fs:.2f}' font-weight='700' font-family='Inter' fill='{'#B58863' if light_l else '#F0D9B5'}'>{8 - i}</text>")
            o.append(f"<text x='{fr + (i + 1) * cell - fs * .7:.2f}' y='{fr + 8 * cell - fs * .3:.2f}' font-size='{fs:.2f}' font-weight='700' font-family='Inter' fill='{'#F0D9B5' if i % 2 == 0 else '#B58863'}'>{'abcdefgh'[i]}</text>")
    pos = placement(fen)
    for sq, (t, white) in pos.items():
        x, y = ctr(sq); o.append(piece_svg(t, white, x, y, cell))
    for sq in targets:
        x, y = ctr(sq)
        if sq in pos: o.append(f"<circle cx='{x:.2f}' cy='{y:.2f}' r='{cell * .44:.2f}' fill='none' stroke='#2E7D4F' stroke-opacity='.75' stroke-width='{cell * .07:.2f}'/>")
        else: o.append(f"<circle cx='{x:.2f}' cy='{y:.2f}' r='{cell * .15:.2f}' fill='#2E7D4F' fill-opacity='.6'/>")
    for a, b, col in arrows:
        (ax, ay), (bx, by) = ctr(a), ctr(b)
        ang = math.atan2(by - ay, bx - ax); wd = cell * .16; head = wd * 2.6
        ex, ey = bx - math.cos(ang) * head * .8, by - math.sin(ang) * head * .8
        p1 = (bx - head * math.cos(ang) + head * .62 * math.sin(ang), by - head * math.sin(ang) - head * .62 * math.cos(ang))
        p2 = (bx - head * math.cos(ang) - head * .62 * math.sin(ang), by - head * math.sin(ang) + head * .62 * math.cos(ang))
        o.append(f"<line x1='{ax:.2f}' y1='{ay:.2f}' x2='{ex:.2f}' y2='{ey:.2f}' stroke='{col}' stroke-opacity='.8' stroke-width='{wd:.2f}' stroke-linecap='round'/>"
                 f"<polygon points='{bx:.2f},{by:.2f} {p1[0]:.2f},{p1[1]:.2f} {p2[0]:.2f},{p2[1]:.2f}' fill='{col}' fill-opacity='.9'/>")
    o.append("</svg>")
    return "".join(o)

def mini_piece(t, white, size, tint=None):
    """Quân rời (icon người chơi, hình chìm)."""
    if tint:
        body = "".join(PG.svg_shape(q).replace("/>", f" fill='{tint}' stroke='{tint}' stroke-width='3.4' stroke-linejoin='round'/>") for q in PG.P[t])
        return f"<svg width='{size}' height='{size}' viewBox='0 -3 100 100'>{body}</svg>"
    return f"<svg width='{size}' height='{size}' viewBox='0 0 100 100'>{piece_svg(t, white, 50, 50, 100)}</svg>"

# ---------------------------------------------------------------- chữ
CAP = {
 'vi': [("Thầy kèm từng nước", "Chấm điểm, giải thích, chỉ nước tốt hơn"),
        ("Cảnh báo trước khi đi", "Sắp mất quân? Thầy nhắc ngay"),
        ("Mỗi ngày thầy giao bài", "Ôn sổ · bài trong ngày · ~8 phút"),
        ("600 bài chiếu hết", "Chiếu hết 1–4 nước · 5 bậc độ khó"),
        ("Sổ tay sai lầm", "Ôn lại đúng lúc để không lặp lỗi"),
        ("Phân tích sau mỗi ván", "Độ chính xác · biểu đồ lợi thế · khoảnh khắc quan trọng")],
 'en': [("A coach for every move", "Grades, explains and shows better moves"),
        ("Warned before you blunder", "About to hang a piece? You'll know"),
        ("A daily lesson", "Review · puzzle of the day · ~8 min"),
        ("600 mate puzzles", "Mate in 1–4 · five difficulty levels"),
        ("Mistake notebook", "Review at the right time – never repeat a mistake"),
        ("Post-game analysis", "Accuracy · advantage chart · key moments")],
 'zh': [("老师陪你下每一步", "打分、讲解，给出更好的着法"),
        ("走棋之前先提醒", "快要丢子？老师马上告诉你"),
        ("老师每天布置练习", "复习 · 今日题 · 约8分钟"),
        ("600道杀棋题", "一至四步杀 · 五级难度"),
        ("错题本", "按时复习，不再犯同样的错"),
        ("每盘棋都有分析", "准确率 · 优势曲线 · 关键时刻")],
 'ja': [("一手ごとに先生が解説", "評価・解説・より良い手を提示"),
        ("悪手の前に警告", "駒を取られそう？すぐにお知らせ"),
        ("毎日の練習メニュー", "復習 · 今日の問題 · 約8分"),
        ("メイト問題600問", "1〜4手メイト · 難易度5段階"),
        ("ミスノート", "ちょうど良いタイミングで復習、同じミスを防ぐ"),
        ("対局後の分析", "正確さ · 形勢グラフ · 重要な場面")],
}
SLUG = ["thay-kem", "canh-bao", "buoi-tap", "bai-tap", "so-tay", "phan-tich"]

T = {
 'vi': dict(app="Cờ Vua Có Thầy", level="Cấp 4 · Kỳ thủ", ai="Máy · Kỳ thủ", you="Bạn", white="Trắng", black="Đen",
   grade="Sai lầm", ctitle="Có nước tốt hơn!",
   ctext="Ăn Tốt e5 trông được lợi, nhưng Hậu đen lên g5 sẽ đánh cùng lúc Mã e5 và Tốt g2. Nên đổi Mã trước: Mxd4 exd4 rồi nhập thành – an toàn và vẫn phát triển nhanh hơn.",
   you_pl="● Bạn đi: Mxe5", better="● Nên đi: Mxd4", retry="↶ Đi lại theo Mxd4", keep="Giữ nước của tôi",
   warn_t="Cẩn thận!", warn_x="Txf7+ là nước chiếu, nhưng Vua đen ăn lại ngay – bạn đổi Tượng lấy 1 Tốt, thiệt khoảng 2 Tốt.",
   other="Chọn nước khác", anyway="Vẫn đi",
   greet="Chào buổi tối", wk=["T2", "T3", "T4", "T5", "T6", "T7", "CN"], streak="5/7 · 12 ngày liền",
   unf="VÁN ĐANG DỞ", unf_s="Nước 4 · bạn cầm Trắng", cont="▶ Chơi tiếp", newg="Ván mới ›",
   dk="THẦY GIAO BÀI · ~8 PHÚT", dt="Buổi tập hôm nay", steps=["✓ Ôn sổ", "Bài hôm nay", "Vừa sức"], go="Tiếp",
   tiles=[(1, "♟", "Chơi với máy", "Đang ở cấp 4 · Kỳ thủ", None), (5, "🧩", "Bài tập", "142/600 · Bài vừa sức", "Bậc 3/5"),
          (3, "📖", "Khai cuộc", "Ý, Tây Ban Nha, Sicilia…", None), (2, "📈", "Tiến bộ", "Kỳ hữu · Sổ tay", "3 thế cần ôn")],
   lessons="36 thế có tên ›",
   pz_title="Bài tập 209", pz_theme="Chủ đề: Thí quân · ★★★★☆", pz_h="Trắng đi – chiếu hết trong 2 nước",
   pz_ct="Gợi ý", pz_cx="Hãy dùng Mã e7. Thí quân để kéo Tốt b7 ra khỏi chỗ.", pz_btn="Xem nước đi",
   rv_title="Sổ tay · Ôn lại", rv_sub="Thế bạn từng đi sai · 3 ngày trước", rv_ok="Đúng rồi!",
   rv_x="Mxd4 – đổi Mã trước để Hậu đen không lên g5 được. Lần trước bạn đi Mxe5 và mất quân.", rv_next="Thế tiếp ▶",
   an_title="Phân tích ván", an_sub="Cấp 4 · 38 nước", acc="Độ chính xác", result="Bạn thắng – chiếu hết!", score="Mc6+ bxc6 Hc7#",
   chart="Lợi thế qua từng nước", cnt=[("2", "Nước hay"), ("24", "Tốt"), ("4", "Chưa chính xác"), ("1", "Sai lầm")], km="Khoảnh khắc quan trọng",
   kms=[("Nước 4", "Mxe5", "Sai lầm"), ("Nước 17", "h4", "Chưa chính xác"), ("Nước 37", "Mc6+", "Nước hay")],
   fg_s="Chơi cờ vua với máy – thầy chấm và giải thích từng nước", fg_k="Stockfish · 8 cấp độ · 600 bài chiếu hết", fg_b="Thầy gợi ý", fg_m="Mxd4 – đổi Mã trước"),
 'en': dict(app="Chess Coach", level="Level 4 · Club player", ai="Computer · Club player", you="You", white="White", black="Black",
   grade="Mistake", ctitle="There was a better move!",
   ctext="Taking on e5 looks free, but …Qg5 hits the knight on e5 and the g2 pawn at once. Trade first with Nxd4 exd4, then castle – safe, and you stay ahead in development.",
   you_pl="● You played: Nxe5", better="● Better: Nxd4", retry="↶ Take back, play Nxd4", keep="Keep my move",
   warn_t="Careful!", warn_x="Bxf7+ is check, but the king simply takes back – a bishop for a pawn leaves you about 2 pawns down.",
   other="Choose another move", anyway="Play anyway",
   greet="Good evening", wk=["M", "T", "W", "T", "F", "S", "S"], streak="5/7 · 12 days in a row",
   unf="UNFINISHED GAME", unf_s="Move 4 · you play White", cont="▶ Continue", newg="New game ›",
   dk="TODAY'S LESSON · ~8 MIN", dt="Today's practice", steps=["✓ Review", "Puzzle", "For you"], go="Next",
   tiles=[(1, "♟", "Play vs computer", "At level 4 · Club player", None), (5, "🧩", "Puzzles", "142/600 · For you", "Level 3/5"),
          (3, "📖", "Openings", "Italian, Ruy Lopez, Sicilian…", None), (2, "📈", "Progress", "Chess friend · Notebook", "3 to review")],
   lessons="36 named lines ›",
   pz_title="Puzzle 209", pz_theme="Theme: Sacrifice · ★★★★☆", pz_h="White to move – mate in 2",
   pz_ct="Hint", pz_cx="Use the knight on e7. Sacrifice it to drag the b7 pawn away.", pz_btn="Show the move",
   rv_title="Notebook · Review", rv_sub="A position you got wrong · 3 days ago", rv_ok="Correct!",
   rv_x="Nxd4 – trade knights first so the black queen can't come to g5. Last time you played Nxe5 and lost a piece.", rv_next="Next ▶",
   an_title="Game analysis", an_sub="Level 4 · 38 moves", acc="Accuracy", result="You win – checkmate!", score="Nc6+ bxc6 Qc7#",
   chart="Advantage move by move", cnt=[("2", "Brilliant"), ("24", "Good"), ("4", "Inaccuracy"), ("1", "Mistake")], km="Key moments",
   kms=[("Move 4", "Nxe5", "Mistake"), ("Move 17", "h4", "Inaccuracy"), ("Move 37", "Nc6+", "Brilliant")],
   fg_s="Play chess vs the computer – a coach grades and explains every move", fg_k="Stockfish · 8 levels · 600 mate puzzles", fg_b="Coach's tip", fg_m="Nxd4 – trade knights first"),
 'zh': dict(app="国际象棋老师", level="4级 · 棋手", ai="电脑 · 棋手", you="你", white="白方", black="黑方",
   grade="失误", ctitle="有更好的着法！",
   ctext="吃e5兵看似白赚，但黑后走g5会同时攻击e5马和g2兵。应先用Nxd4兑马，exd4后再王车易位——安全，出子也更快。",
   you_pl="● 你走：Nxe5", better="● 应走：Nxd4", retry="↶ 悔棋改走Nxd4", keep="保留我的着法",
   warn_t="小心！", warn_x="Bxf7+是将军，但黑王直接吃回——用象换一个兵，大约亏2个兵。",
   other="换一步", anyway="仍然走",
   greet="晚上好", wk=["一", "二", "三", "四", "五", "六", "日"], streak="5/7 · 连续12天",
   unf="未完成的棋局", unf_s="第4步 · 你执白", cont="▶ 继续", newg="新棋局 ›",
   dk="老师布置 · 约8分钟", dt="今日练习", steps=["✓ 复习", "今日题", "适合你"], go="继续",
   tiles=[(1, "♟", "人机对弈", "当前4级 · 棋手", None), (5, "🧩", "习题", "142/600 · 适合你", "等级3/5"),
          (3, "📖", "开局", "意大利、西班牙、西西里…", None), (2, "📈", "进步", "棋友 · 错题本", "3个待复习")],
   lessons="36种布局 ›",
   pz_title="习题209", pz_theme="主题：弃子 · ★★★★☆", pz_h="白先——两步杀",
   pz_ct="提示", pz_cx="用e7的马。弃掉它，把b7兵引开。", pz_btn="显示着法",
   rv_title="错题本 · 复习", rv_sub="你曾走错的局面 · 3天前", rv_ok="正确！",
   rv_x="Nxd4——先兑马，黑后就上不了g5。上次你走了Nxe5，丢了一子。", rv_next="下一个 ▶",
   an_title="棋局分析", an_sub="4级 · 38步", acc="准确率", result="你赢了——将杀！", score="Nc6+ bxc6 Qc7#",
   chart="每步形势", cnt=[("2", "妙着"), ("24", "好棋"), ("4", "欠准确"), ("1", "失误")], km="关键时刻",
   kms=[("第4步", "Nxe5", "失误"), ("第17步", "h4", "欠准确"), ("第37步", "Nc6+", "妙着")],
   fg_s="和电脑下国际象棋——老师逐步打分讲解", fg_k="Stockfish · 8个等级 · 600道杀棋题", fg_b="老师提示", fg_m="Nxd4——先兑马"),
 'ja': dict(app="チェス先生", level="レベル4 · アマ中級", ai="コンピュータ · アマ中級", you="あなた", white="白", black="黒",
   grade="悪手", ctitle="より良い手があります！",
   ctext="e5のポーン取りは得に見えますが、…Qg5でe5のナイトとg2のポーンを同時に狙われます。先にNxd4で交換し、exd4の後にキャスリング – 安全で展開も先行できます。",
   you_pl="● あなたの手：Nxe5", better="● 推奨：Nxd4", retry="↶ 待ったしてNxd4", keep="自分の手のまま",
   warn_t="注意！", warn_x="Bxf7+はチェックですが、キングにそのまま取り返されます – ビショップとポーン1つの交換で、約2ポーン分の損です。",
   other="別の手を選ぶ", anyway="それでも指す",
   greet="こんばんは", wk=["月", "火", "水", "木", "金", "土", "日"], streak="5/7 · 12日連続",
   unf="指しかけの対局", unf_s="4手目 · あなたは白", cont="▶ 続ける", newg="新しい対局 ›",
   dk="今日のメニュー · 約8分", dt="今日の練習", steps=["✓ 復習", "今日の問題", "あなた向け"], go="次へ",
   tiles=[(1, "♟", "コンピュータと対局", "レベル4 · アマ中級", None), (5, "🧩", "問題集", "142/600 · あなた向け", "段階3/5"),
          (3, "📖", "定跡", "イタリアン、ルイロペス、シシリアン…", None), (2, "📈", "上達", "チェス仲間 · ミスノート", "復習3件")],
   lessons="名前のある定跡36種 ›",
   pz_title="問題209", pz_theme="テーマ：サクリファイス · ★★★★☆", pz_h="白番 – 2手メイト",
   pz_ct="ヒント", pz_cx="e7のナイトを使いましょう。捨ててb7のポーンをどかします。", pz_btn="手を表示",
   rv_title="ミスノート · 復習", rv_sub="以前間違えた局面 · 3日前", rv_ok="正解！",
   rv_x="Nxd4 – 先にナイトを交換すれば黒のクイーンはg5に来られません。前回はNxe5で駒を損しました。", rv_next="次へ ▶",
   an_title="対局の分析", an_sub="レベル4 · 38手", acc="正確さ", result="あなたの勝ち – チェックメイト！", score="Nc6+ bxc6 Qc7#",
   chart="一手ごとの形勢", cnt=[("2", "好手"), ("24", "良い手"), ("4", "不正確"), ("1", "悪手")], km="重要な場面",
   kms=[("4手目", "Nxe5", "悪手"), ("17手目", "h4", "不正確"), ("37手目", "Nc6+", "好手")],
   fg_s="コンピュータとチェス – 先生が一手ごとに評価・解説", fg_k="Stockfish · 8レベル · メイト問題600問", fg_b="先生のヒント", fg_m="Nxd4 – 先にナイト交換"),
}

FONT = {'vi': "Inter,'Noto Sans CJK SC'", 'en': "Inter,'Noto Sans CJK SC'", 'zh': "Inter,'Noto Sans CJK SC'", 'ja': "Inter,'Noto Sans CJK JP'"}

# Màu app (Theme.kt): Red = xanh mực 2C4A7A, RedDark 15253F, Gold F6D58E
CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{color:#2B2420}
.shot{width:1080px;height:1920px;position:relative;overflow:hidden;background:linear-gradient(170deg,#3B6AA8 0%,#2C4A7A 50%,#15253F 100%)}
.shot .wm{position:absolute;right:-110px;top:-30px;opacity:.07}
.cap{position:absolute;top:78px;left:0;right:0;text-align:center;color:#fff;padding:0 60px}
.cap h1{font-size:76px;font-weight:800;letter-spacing:-1px;line-height:1.12}
.cap p{font-size:38px;margin-top:22px;color:#F6D58E;font-weight:600;line-height:1.25}
.phone{position:absolute;left:100px;top:390px;width:880px;height:1600px;border-radius:84px;background:#1C1A18;padding:22px;box-shadow:0 30px 80px rgba(0,0,0,.45)}
.screen{width:412px;height:800px;transform:scale(2.029);transform-origin:0 0;background:#FAF5EC;border-radius:31px;overflow:hidden;position:relative}
.sb{height:30px;display:flex;justify-content:space-between;padding:9px 22px 0;font-size:12px;font-weight:700}
.tb{display:flex;align-items:center;gap:10px;padding:6px 14px 8px}
.tb .bk{font-size:20px}.tb .t{font-weight:800;font-size:16px}.tb .s{font-size:11.5px;color:#7A6E62}
.pr{display:flex;align-items:center;gap:9px;padding:5px 16px}
.pr .av{width:30px;height:30px;border-radius:50%;flex:none;background:#EDE4D3;border:1px solid #E7DCC8;display:flex;align-items:center;justify-content:center}
.pr .n{font-weight:700;font-size:13px}.pr .x{font-size:11px;color:#7A6E62}
.bd{display:flex;justify-content:center;padding:2px 0}
.mv{display:flex;gap:6px;padding:4px 16px;font-size:11.5px;color:#7A6E62;white-space:nowrap;overflow:hidden}
.mv b{color:#2B2420}
.card{margin:6px 12px;background:#fff;border:1px solid #E7DCC8;border-radius:18px;padding:12px 14px;box-shadow:0 4px 14px rgba(21,37,63,.08)}
.badge{display:inline-block;font-size:11px;font-weight:800;color:#fff;padding:3px 9px;border-radius:9px}
.card .h{font-weight:800;font-size:15px;margin-top:6px}.card .x{font-size:12.5px;line-height:1.45;margin-top:4px;color:#3d342e}
.row{display:flex;gap:8px;margin-top:10px}
.pill{flex:1;text-align:center;padding:9px 8px;border-radius:20px;font-weight:700;font-size:12px;background:#F1EADF;white-space:nowrap}
.pill.p{background:#2C4A7A;color:#fff}
.code{font-size:12.5px;font-weight:700;margin-top:4px}
"""

def page(lang, body, w=1080, h=1920):
    return f"""<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>{CSS}
body{{font-family:{FONT[lang]},sans-serif}}</style></head>
<body style="width:{w}px;height:{h}px">{body}</body></html>"""

def bd(svg): return f"<div class='bd'>{svg}</div>"
def sb(dark=False):
    return f"<div class='sb' style='color:{'#fff' if dark else '#2B2420'}'><span>21:30</span><span>▾ ◢ ▮</span></div>"
def topbar(t, s="", right="⋮"):
    return f"<div class='tb'><span class='bk'>←</span><div style='flex:1'><div class='t'>{t}</div><div class='s'>{s}</div></div><span style='font-size:18px'>{right}</span></div>"

def players(L, ai_think=False):
    ai = f"<div class='pr'><div class='av'>{mini_piece(1, False, 24)}</div><div style='flex:1'><div class='n'>{L['ai']}</div><div class='x'>{L['black']}</div></div></div>"
    me = f"<div class='pr'><div class='av'>{mini_piece(1, True, 24)}</div><div style='flex:1'><div class='n' style='color:#2C4A7A'>{L['you']}</div><div class='x'>{L['white']}</div></div></div>"
    return ai, me

MOVES = "<div class='mv'><span>1. <b>e4</b> e5</span><span>2. <b>{n}f3</b> {n2}c6</span><span>3. <b>{b}c4</b> {n2}d4</span><span>4. <b>{n}xe5</b></span></div>"
def moves(lang):
    n = "M" if lang == "vi" else "N"; b = "T" if lang == "vi" else "B"
    return MOVES.format(n=n, n2=n, b=b)

def s_coach(L, lang):
    ai, me = players(L)
    return (sb() + topbar(L['level'], "", "⋮") + ai +
        bd(board_svg(COACH, 332, last=("f3", "e5"), arrows=[("d8", "g5", "#C62828"), ("f3", "d4", "#2E7D4F")])) + me + moves(lang) +
        f"<div class='card'><span class='badge' style='background:#C62828'>{L['grade']}</span><div class='h'>{L['ctitle']}</div>"
        f"<div class='x'>{L['ctext']}</div><div class='code' style='color:#C62828'>{L['you_pl']}</div><div class='code' style='color:#2E7D4F'>{L['better']}</div>"
        f"<div class='row'><div class='pill p'>{L['retry']}</div><div class='pill'>{L['keep']}</div></div></div>")

def s_warn(L, lang):
    ai, me = players(L)
    return (sb() + topbar(L['level'], "", "⋮") + ai +
        bd(board_svg(WARN, 360, last=("g8", "f6"), selected="c4", targets=["b5", "d5", "b3", "d3", "e2", "f1"], danger=["a6", "e6", "f7"])) + me +
        f"<div class='card' style='border-color:#F2B8B5;background:#FFF6F5;margin-top:10px'><span class='badge' style='background:#C62828'>⚠</span><div class='h'>{L['warn_t']}</div>"
        f"<div class='x'>{L['warn_x']}</div><div class='row'><div class='pill p'>{L['other']}</div><div class='pill'>{L['anyway']}</div></div></div>")

def s_home(L, lang):
    dots = ["#F6D58E"] * 4 + ["linear-gradient(90deg,rgba(255,255,255,.22) 50%,#F6D58E 50%)", "none;border:2px dashed #F6D58E", "rgba(255,255,255,.22)"]
    wk = "".join(f"<div style='flex:1;text-align:center;font-size:11px'><b style='display:block;width:18px;height:18px;border-radius:50%;margin:0 auto 3px;background:{bg}'></b>{d}</div>"
                 for d, bg in zip(L['wk'], dots))
    grads = [("#D2382B", "#8E1D15"), ("#E9A23B", "#B86A12"), ("#2F8F68", "#1C5E43"), ("#3C5BA8", "#253B78")]
    foots = ["<div style='display:flex;gap:3px'>" + "".join(f"<i style='flex:1;height:5px;border-radius:3px;background:{'#F6D58E' if i < 4 else 'rgba(255,255,255,.25)'}'></i>" for i in range(8)) + "</div>",
             "<div style='height:5px;border-radius:3px;background:rgba(255,255,255,.25)'><div style='height:5px;border-radius:3px;width:24%;background:#fff'></div></div>",
             f"<div style='font-size:12px;font-weight:700;color:#fff'>{L['lessons']}</div>",
             "<svg width='130' height='22'><polyline points='2,18 20,14 38,16 56,10 74,12 92,6 110,8 128,3' fill='none' stroke='#fff' stroke-width='2.5' stroke-linejoin='round'/></svg>"]
    tiles = "".join(f"""<div style='position:relative;height:140px;border-radius:22px;overflow:hidden;background:linear-gradient(135deg,{a},{b});box-shadow:0 6px 14px rgba(0,0,0,.18);color:#fff;padding:12px 14px;display:flex;flex-direction:column'>
      <div style='position:absolute;right:-8px;bottom:-12px'>{mini_piece(pc, True, 100, 'rgba(255,255,255,.16)')}</div>
      {f"<div style='position:absolute;right:10px;top:10px;font-size:10px;font-weight:800;background:#fff;color:#2C4A7A;padding:3px 8px;border-radius:10px'>{badge}</div>" if badge else ''}
      <div style='width:34px;height:34px;border-radius:11px;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;font-size:17px'>{ic}</div>
      <div style='font-size:15.5px;font-weight:800;margin-top:6px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>{t}</div>
      <div style='font-size:11px;opacity:.9;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>{s}</div><div style='flex:1'></div>{f}</div>"""
        for (pc, ic, t, s, badge), (a, b), f in zip(L['tiles'], grads, foots))
    steps = "".join(f"<span style='white-space:nowrap;font-size:10.5px;padding:3px 7px;border-radius:9px;background:{'#E3F1E8;color:#2E7D4F' if i == 0 else '#F1EADF'}'>{s}</span>" for i, s in enumerate(L['steps']))
    return f"""<div style='background:linear-gradient(135deg,#3B6AA8,#15253F);color:#fff;padding:0 18px 22px;border-radius:0 0 30px 30px;position:relative;overflow:hidden'>
      {sb(True)}<div style='position:absolute;right:-36px;top:30px'>{mini_piece(1, True, 200, 'rgba(255,255,255,.07)')}</div>
      <div style='display:flex;justify-content:space-between;align-items:center;margin-top:6px;position:relative'><div><div style='font-size:13px;opacity:.85'>{L['greet']}</div>
      <div style='font-size:25px;font-weight:800'>{L['app']}</div></div><div style='width:40px;height:40px;border-radius:50%;background:rgba(255,255,255,.15);display:flex;align-items:center;justify-content:center'>⚙</div></div>
      <div style='display:flex;gap:2px;margin-top:12px;align-items:center;position:relative'>{wk}<div style='margin-left:6px;background:rgba(0,0,0,.2);padding:5px 10px;border-radius:12px;font-weight:700;font-size:12px;white-space:nowrap'>{L['streak']}</div></div>
      <div style='display:flex;gap:14px;margin-top:16px;align-items:center;position:relative'><div style='flex:none;border-radius:10px;overflow:hidden;box-shadow:0 8px 20px rgba(0,0,0,.35);line-height:0'>{board_svg(COACH, 128, coords=False, last=('f3', 'e5'))}</div>
      <div><div style='font-size:11px;letter-spacing:.8px;font-weight:800;color:#F6D58E'>{L['unf']}</div><div style='font-size:17px;font-weight:800;margin-top:3px'>{L['level']}</div>
      <div style='font-size:12px;opacity:.85;margin-top:2px'>{L['unf_s']}</div><div style='display:inline-block;background:#F6D58E;color:#15253F;font-weight:800;padding:10px 18px;border-radius:24px;margin-top:10px;font-size:14px'>{L['cont']}</div>
      <div style='font-size:12.5px;margin:8px 0 0 12px;opacity:.9;font-weight:600'>{L['newg']}</div></div></div></div>
      <div style='margin:16px 16px 0;background:#fff;border-radius:20px;padding:14px;display:flex;gap:12px;align-items:center;border:1px solid #F2D9A8;box-shadow:0 6px 18px rgba(21,37,63,.12)'>
      <svg width='54' height='54' viewBox='0 0 54 54'><circle cx='27' cy='27' r='22' fill='none' stroke='#F1EADF' stroke-width='7'/><circle cx='27' cy='27' r='22' fill='none' stroke='#2C4A7A' stroke-width='7' stroke-dasharray='46 138' transform='rotate(-90 27 27)' stroke-linecap='round'/><text x='27' y='32' text-anchor='middle' font-size='14' font-weight='800' fill='#2B2420' font-family='Inter'>1/3</text></svg>
      <div style='flex:1;min-width:0'><div style='font-size:10.5px;color:#2C4A7A;font-weight:800;letter-spacing:.5px'>{L['dk']}</div><div style='font-size:14px;font-weight:800;margin-top:2px'>{L['dt']}</div>
      <div style='display:flex;gap:5px;margin-top:6px'>{steps}</div></div><div style='background:#2C4A7A;color:#fff;font-weight:800;border-radius:16px;padding:10px 12px;font-size:13px'>{L['go']}</div></div>
      <div style='display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:12px 16px'>{tiles}</div>"""

def s_puzzle(L, lang):
    return (sb() + topbar(L['pz_title'], L['pz_theme'], "📤") +
        f"<div style='padding:2px 16px 10px;font-weight:800;font-size:16px;display:flex;align-items:center;gap:8px'>{mini_piece(1, True, 22)}{L['pz_h']}</div>" +
        bd(board_svg(PUZZLE, 384, hint="e7")) +
        f"<div class='card' style='border-color:#BBD3F0;background:#F4F8FD;margin-top:14px'><span class='badge' style='background:#1565C0'>💡 {L['pz_ct']}</span>"
        f"<div class='x' style='margin-top:8px'>{L['pz_cx']}</div><div class='row'><div class='pill p'>{L['pz_btn']}</div></div></div>")

def s_review(L, lang):
    return (sb() + topbar(L['rv_title'], L['rv_sub'], "") +
        bd(board_svg(BEFORE, 384, last=("c6", "d4"), arrows=[("f3", "d4", "#2E7D4F")])) +
        f"<div class='card' style='border-color:#BFE0CB;background:#F3FAF5;margin-top:14px'><span class='badge' style='background:#2E7D4F'>✓ {L['rv_ok']}</span>"
        f"<div class='x' style='margin-top:8px'>{L['rv_x']}</div><div class='row'><div class='pill p'>{L['rv_next']}</div></div></div>")

def s_analysis(L, lang):
    vals = [0.0]
    for i in range(1, 38):
        v = 0.6 * math.sin(i / 2.3) + 0.25 * i * (i > 12) * 0.5
        if 4 <= i <= 8: v -= 2.6 - 0.35 * (i - 4)          # nước 4 Mxe5 sai lầm → bị Đen ép
        if i >= 30: v += 1.2 * (i - 29)                     # thí Mã, chiếu hết
        vals.append(v)
    vals = [max(-8.5, min(8.5, v)) for v in vals]
    xs = lambda i: 6 + i * 10.0
    ys = lambda v: 70 - v * 6.5
    path = " ".join(f"{'M' if i == 0 else 'L'}{xs(i):.1f},{ys(v):.1f}" for i, v in enumerate(vals))
    cnt = "".join(f"<div style='flex:1;background:#fff;border:1px solid #E7DCC8;border-radius:12px;padding:8px 2px;text-align:center'><div style='font-size:19px;font-weight:800;color:{c}'>{n}</div><div style='font-size:9.5px;color:#7A6E62;white-space:nowrap'>{t}</div></div>"
                  for (n, t), c in zip(L['cnt'], ["#1565C0", "#558B2F", "#E08A00", "#C62828"]))
    kms = "".join(f"<div style='display:flex;align-items:center;gap:10px;padding:8px 0;border-top:1px solid #F1EADF'><span style='font-size:12px;color:#7A6E62;width:70px'>{a}</span><b style='font-size:13px;flex:1'>{b}</b><span class='badge' style='background:{c}'>{g}</span></div>"
                  for (a, b, g), c in zip(L['kms'], ["#C62828", "#E08A00", "#1565C0"]))
    return (sb() + topbar(L['an_title'], L['an_sub'], "📤") +
        f"<div class='card' style='display:flex;align-items:center;gap:14px'><div style='flex:1;text-align:center'><div style='font-size:12px;color:#7A6E62'>{L['acc']}</div><div style='font-size:44px;font-weight:800;color:#2E7D4F;line-height:1.1'>86%</div>"
        f"<div style='font-size:13px;font-weight:800'>{L['result']}</div><div style='font-size:11px;color:#7A6E62;margin-top:2px'>{L['score']}</div></div>"
        f"<div style='border-radius:8px;overflow:hidden;line-height:0'>{board_svg(FINAL, 130, coords=False, frame=False, last=('f7', 'c7'))}</div></div>"
        f"<div class='card'><div style='font-size:12px;color:#7A6E62;margin-bottom:6px'>{L['chart']}</div><svg width='360' height='140' viewBox='0 0 380 140'>"
        f"<rect x='0' y='0' width='380' height='70' fill='#FBFAF6'/><rect x='0' y='70' width='380' height='70' fill='#E6E2DA'/><line x1='0' y1='70' x2='380' y2='70' stroke='#CDBFAE'/>"
        f"<path d='{path} L{xs(len(vals) - 1):.1f},70 L6,70 Z' fill='#FBFAF6' fill-opacity='.0'/>"
        f"<path d='{path}' fill='none' stroke='#2C4A7A' stroke-width='3' stroke-linejoin='round'/>"
        f"<circle cx='{xs(4)}' cy='{ys(vals[4])}' r='5' fill='#C62828' stroke='#fff' stroke-width='1.5'/><circle cx='{xs(17)}' cy='{ys(vals[17])}' r='5' fill='#E08A00' stroke='#fff' stroke-width='1.5'/><circle cx='{xs(37)}' cy='{ys(vals[37])}' r='5' fill='#1565C0' stroke='#fff' stroke-width='1.5'/></svg></div>"
        f"<div style='display:flex;gap:6px;margin:6px 12px'>{cnt}</div>"
        f"<div class='card'><div style='font-weight:800;font-size:14px;margin-bottom:4px'>{L['km']}</div>{kms}</div>")

SCREENS = [s_coach, s_warn, s_home, s_puzzle, s_review, s_analysis]

def shot(lang, i):
    L = T[lang]; h1, sub = CAP[lang][i]
    body = f"""<div class='shot'><div class='wm'>{mini_piece(5, True, 560, '#fff')}</div><div class='cap'><h1 style='font-size:{76 if len(h1) <= 22 else 64}px'>{h1}</h1><p>{sub}</p></div>
      <div class='phone'><div class='screen'>{SCREENS[i](L, lang)}</div></div></div>"""
    return page(lang, body)

def feature(lang):
    L = T[lang]
    big = 58 if len(L['app']) <= 16 else 50
    body = f"""<div style='width:1024px;height:500px;position:relative;overflow:hidden;background:linear-gradient(120deg,#3B6AA8 0%,#2C4A7A 55%,#15253F 100%);color:#fff'>
      <div style='position:absolute;left:-90px;top:-60px;opacity:.07'>{mini_piece(1, True, 560, '#fff')}</div>
      <div style='position:absolute;left:60px;top:0;bottom:0;width:470px;display:flex;flex-direction:column;justify-content:center'>
        <img src='{ICON}' style='width:104px;height:104px;border-radius:24px;box-shadow:0 10px 30px rgba(0,0,0,.35)'>
        <div style='font-size:{big}px;font-weight:800;margin-top:22px;letter-spacing:-.5px;line-height:1.08'>{L['app']}</div>
        <div style='font-size:24px;color:#F6D58E;font-weight:600;margin-top:12px;line-height:1.35'>{L['fg_s']}</div>
        <div style='font-size:16px;opacity:.75;margin-top:12px;font-weight:600'>{L['fg_k']}</div></div>
      <div style='position:absolute;right:52px;top:40px;transform:rotate(4deg);border-radius:12px;overflow:hidden;box-shadow:0 20px 50px rgba(0,0,0,.45);line-height:0'>{board_svg(BEFORE, 400, coords=False, last=('c6', 'd4'), arrows=[('f3', 'd4', '#2E7D4F')])}</div>
      <div style='position:absolute;right:330px;bottom:34px;background:#fff;color:#2B2420;border-radius:18px;padding:12px 18px;box-shadow:0 10px 30px rgba(0,0,0,.35);transform:rotate(-3deg)'>
        <span class='badge' style='background:#2E7D4F;font-size:14px'>{L['fg_b']}</span><div style='font-weight:800;font-size:18px;margin-top:6px;white-space:nowrap'>{L['fg_m']}</div></div></div>"""
    return page(lang, body, 1024, 500)

def main():
    with sync_playwright() as p:
        exe = os.environ.get("CHROME")
        b = p.chromium.launch(executable_path=exe) if exe else p.chromium.launch()
        for lang in LANGS:
            d = os.path.join(OUT, "screenshots", lang); os.makedirs(d, exist_ok=True)
            for i in range(6):
                pg = b.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
                pg.set_content(shot(lang, i)); pg.wait_for_timeout(300)
                pg.screenshot(path=os.path.join(d, f"covua-{lang}-{i + 1:02d}-{SLUG[i]}.png")); pg.close()
            pg = b.new_page(viewport={"width": 1024, "height": 500}, device_scale_factor=1)
            pg.set_content(feature(lang)); pg.wait_for_timeout(300)
            pg.screenshot(path=os.path.join(OUT, f"covua-feature-graphic-{lang}-1024x500.png")); pg.close()
        b.close()

if __name__ == "__main__":
    main()
