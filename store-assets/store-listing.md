# Cờ Vua Có Thầy – Store listing (Google Play)

Package: `com.huanhuynh.covua` · Danh mục: **Game → Board** · Ngôn ngữ mặc định: **vi-VN** (+ en-US, zh-CN, ja-JP) · Mọi lứa tuổi, có quảng cáo.

| | vi-VN | en-US | zh-CN | ja-JP |
|---|---|---|---|---|
| Tên (≤30) | Cờ Vua Có Thầy – Học & chơi | Chess Coach – Learn & Play | 国际象棋老师 – 学棋与对弈 | チェス先生 – 学んで指せるチェス |
| Mô tả ngắn (≤80) | Chơi cờ vua với máy, thầy chấm và giải thích từng nước. 600 bài chiếu hết | Play chess vs the computer with a coach who explains every move. 600 puzzles | 与电脑下国际象棋，老师逐步点评讲解。600道杀棋题，每日一题。 | コンピュータとチェス。一手ごとに先生が解説。メイト問題600問、毎日の新問題 |

## Tên ứng dụng (≤ 30 ký tự)
- vi-VN: `Cờ Vua Có Thầy – Học & chơi` (27 ký tự)
- en-US: `Chess Coach – Learn & Play` (26 ký tự)
- zh-CN: `国际象棋老师 – 学棋与对弈` (14 ký tự)
- ja-JP: `チェス先生 – 学んで指せるチェス` (17 ký tự)

## Mô tả ngắn (≤ 80 ký tự)
- vi-VN:
```
Chơi cờ vua với máy, thầy chấm và giải thích từng nước. 600 bài chiếu hết
```
(73 ký tự)
- en-US:
```
Play chess vs the computer with a coach who explains every move. 600 puzzles
```
(76 ký tự)
- zh-CN:
```
与电脑下国际象棋，老师逐步点评讲解。600道杀棋题，每日一题。
```
(31 ký tự)
- ja-JP:
```
コンピュータとチェス。一手ごとに先生が解説。メイト問題600問、毎日の新問題
```
(38 ký tự)

## Tags gợi ý (tối đa 5)
Board (Cờ bàn) · Chess · Strategy · Puzzle · Offline. Dự phòng: Educational (Giáo dục – hợp với thầy kèm, Sổ tay & Buổi tập), Brain games.
Chọn ở Play Console → Store presence → Store settings → App tags (chọn từ danh sách của Google, không gõ tự do).

## Mô tả đầy đủ (vi-VN)
```
Cờ Vua Có Thầy – học cờ vua với máy, có "thầy" kèm từng nước.

Máy không chỉ đánh với bạn: sau mỗi nước, thầy chấm điểm và giải thích bằng lời dễ hiểu vì sao nước đó hay hay dở, và chỉ ra nước tốt hơn.

♟ CHƠI VỚI MÁY – ENGINE STOCKFISH
• 8 cấp độ: Mới học, Tập sự, Phong trào, Kỳ thủ (~1400), Khá (~1700), Giỏi (~2000), Kiện tướng (~2400), Đại kiện tướng
• Cầm Trắng hoặc Đen; cờ chấp Tốt, Mã, Xe, Hậu – hoặc bạn chấp máy
• Không cần đăng nhập, chơi không cần mạng

👨‍🏫 THẦY KÈM TỪNG NƯỚC
• Chấm nước: Nước hay · Đúng bài · Tốt · Chưa chính xác · Sai lầm · Sai lầm nghiêm trọng
• Giải thích lý do: quân bị treo, bị bắt đôi, bỏ lỡ chiếu hết, để bị chiếu hết…
• Cảnh báo trước khi đi: ô đi vào sẽ mất quân hiện dấu đỏ
• Gợi ý 2 bước, đi lại nước sai sau khi đọc lời thầy
• Gọi tên hơn 30 khai cuộc: Ý, Tây Ban Nha, Sicilia, Pháp, Caro-Kann, Gambit Hậu, Ấn Độ Vua…

📈 PHÂN TÍCH VÁN – độ chính xác, biểu đồ lợi thế, khoảnh khắc quan trọng, xem lại từng nước
📒 SỔ TAY SAI LẦM & BUỔI TẬP HẰNG NGÀY – thế bạn đi sai được ôn lại đúng lúc
🧩 600 BÀI CHIẾU HẾT 1–4 NƯỚC – chia 5 bậc độ khó, "Bài vừa sức", bài của thầy mỗi ngày

Tiếng Việt, English, 中文, 日本語. Engine: Stockfish (GPL v3). Miễn phí, có quảng cáo.
```

## Ảnh store
- `icon-512.png` – icon 512×512 (Vua vàng kim, nền xanh hoàng gia).
- `covua-feature-graphic-<lang>-1024x500.png` – feature graphic cho từng ngôn ngữ (vi, en, zh, ja).
- `screenshots/<lang>/covua-<lang>-01..06-*.png` – 6 ảnh điện thoại 1080×1920 mỗi ngôn ngữ, theo thứ tự:
  1. Thầy kèm từng nước · 2. Cảnh báo trước khi đi · 3. Mỗi ngày thầy giao bài · 4. 600 bài chiếu hết · 5. Sổ tay sai lầm · 6. Phân tích sau mỗi ván
- Dựng lại: `python3 store-assets/source/build_assets.py` (cần Playwright + Chromium, hoặc đặt `CHROME=`). Quân cờ lấy từ `tools/pieces/gen.py`.
- Thế cờ trong ảnh là thế thật: bẫy Blackburne–Shilling (khai cuộc Ý, 4.Nxe5? Qg5!), 4.c3 Nf6 rồi Bxf7+?, bài 209 (Nc6+ bxc6 Qc7#).
