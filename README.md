# Cờ Vua Có Thầy (Chess Coach)

Chơi cờ vua với máy, có "thầy" chấm và giải thích từng nước. Package `com.huanhuynh.covua`. Dựng trên khung chung của Cờ Tướng Có Thầy / Cờ Vây Có Thầy.
Tên app: Cờ Vua Có Thầy · Chess Coach · 国际象棋老师 · チェス先生. Ngôn ngữ: vi / en / zh / ja.

- Kotlin · Compose M3 · Navigation · Room (KSP) + DataStore · WorkManager · AdMob + UMP · Firebase tuỳ chọn · XPromo · Play In-App Review · Gradle Play Publisher
- Engine: **Stockfish** (GPL v3) chạy như tiến trình riêng qua UCI cho máy cấp 4–8 và phân tích đầu lượt của thầy; engine **thuần Kotlin** (`:engine`) cho máy cấp 1–3, lời giải thích, bài tập, và làm dự phòng khi thiếu Stockfish.

## Cấu trúc

| Module / thư mục | Nội dung |
|---|---|
| `:engine` | Thuần Kotlin, có unit test. `engine/`: `Position` (nhập thành, bắt tốt qua đường, phong cấp, 50 nước), `Search` (PVS + ID + quiescence + TT), `Eval`, `Notation` (SAN, chữ quân theo ngôn ngữ), `OpeningBook` (33 khai cuộc có tên, 4 ngôn ngữ), `AiPlayer` (8 cấp), `Game` (+PGN), `Handicap` (cờ chấp), `MateProver`, **`Uci`** (`UciEngine`, `Engines`). `coach/`: `Coach`, `Puzzle`/`PuzzleSession` |
| `:training-core` | Logic huấn luyện thuần: Leitner, lịch tập, chọn bài, gợi ý cấp, Buổi tập, nhắc, danh hiệu |
| `:tools` | JVM app: `ChessMiner` (tự đánh + đào bài chiếu hết, chứng minh nước đầu duy nhất), `ContentPacker` → `app/src/main/assets/content/*.json.gz` |
| `engine-native/build_stockfish.sh` | Build Stockfish (mặc định sf_11) bằng NDK → `app/src/main/jniLibs/<abi>/libstockfish.so` |
| `app/stockfish/StockfishHub` | Khởi động Stockfish, gắn vào `Engines`; lỗi → dự phòng Kotlin |

## Build Stockfish (một lần, trên máy có Android NDK)

```bash
./engine-native/build_stockfish.sh            # arm64-v8a + armeabi-v7a + x86_64
```
Không có `libstockfish.so` thì app vẫn chạy (engine Kotlin, yếu hơn ở cấp 6–8). Màn Cài đặt → "Máy đánh cờ" cho biết đang dùng engine nào.
**GPL:** Stockfish là phần mềm GPL v3 – app ghi công ở Cài đặt → "Giấy phép phần mềm" (có link mã nguồn sf_11); script giữ bản mã nguồn `engine-native/stockfish-sf_11.tar.gz`.

## Kiểm tra

Perft chuẩn: thế đầu 20 / 400 / 8.902 / 197.281; Kiwipete 48 / 2.039 / 97.862.

```bash
./gradlew :engine:test :training-core:test testDebugUnitTest
```

## Sinh lại kho bài tập

```bash
./gradlew :tools:run --args="mine --games 20000 --minutes 60 --out build/mined.jsonl"
./gradlew :tools:run --args="pack --in build/mined.jsonl --out ../app/src/main/assets/content --csv content/puzzles.csv"
```

## Chạy

```bash
./gradlew installDebug
./gradlew :app:bundleRelease
```
