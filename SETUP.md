# Việc cần làm trước khi phát hành

- [ ] `./engine-native/build_stockfish.sh` (cần NDK) – đóng gói Stockfish vào app.
- [ ] Mở `CoVua` bằng Android Studio → Sync → Run debug (quảng cáo test).
- [ ] AdMob: tạo app `com.huanhuynh.covua` + ad unit (banner, interstitial, rewarded, app open), tạo `ads.properties` (xem `ads.properties` của app khác).
- [ ] Firebase (tuỳ chọn): thêm app `com.huanhuynh.covua`, tải `google-services.json` vào `app/`.
- [ ] `key.properties` + `keystore` (upload key dùng chung) – không commit.
- [ ] Play Console: tạo app "Cờ Vua Có Thầy", danh mục **Game → Board**, mọi lứa tuổi, có quảng cáo; upload tay bản đầu.
- [x] Privacy policy: https://thanhhuan1990.github.io/privacy-policy/covua/ (gắn trong Cài đặt).
- [ ] Ảnh store (feature graphic + ảnh màn hình). Icon 512: `store-assets/icon-512.png`.
- [ ] Thêm app vào `thanhhuan1990.github.io/xpromo/apps.json` khi đã lên Play.
