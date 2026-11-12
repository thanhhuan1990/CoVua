#!/usr/bin/env bash
# Build Stockfish cho Android bằng NDK rồi chép vào app/src/main/jniLibs/<abi>/libstockfish.so.
#
# Stockfish chạy như một TIẾN TRÌNH riêng (giao thức UCI qua stdin/stdout) – app gọi file thực thi trong nativeLibraryDir
# (đặt tên lib*.so để Android cài vào thư mục được phép chạy; xem packaging.jniLibs.useLegacyPackaging trong app/build.gradle.kts).
#
# Mặc định Stockfish 11: đánh giá cổ điển, không cần mạng NNUE → file nhỏ (~1–2 MB/ABI), vẫn mạnh hơn mọi người chơi.
# Stockfish là phần mềm GPL v3: app phải ghi công và trỏ tới mã nguồn (đã có trong màn Giới thiệu → Giấy phép).
#
# Cần: Android NDK (Android Studio → SDK Manager → NDK), curl, tar.
# Dùng:  ./engine-native/build_stockfish.sh              # arm64-v8a + armeabi-v7a + x86_64 (máy ảo)
#        ABIS="arm64-v8a" ./engine-native/build_stockfish.sh
#        REBUILD=1 ./engine-native/build_stockfish.sh    # build lại dù đã có
set -euo pipefail

SF_TAG="${SF_TAG:-sf_11}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64}"
API="${API:-24}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/engine-native/build"
JNI="$ROOT/app/src/main/jniLibs"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  for base in "$HOME/Library/Android/sdk/ndk" "$HOME/Android/Sdk/ndk" "${ANDROID_HOME:-/nonexistent}/ndk"; do
    if [[ -d "$base" ]]; then ANDROID_NDK_HOME="$(ls -d "$base"/* | sort -V | tail -1)"; break; fi
  done
fi
[[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" ]] || { echo "Không tìm thấy Android NDK – đặt ANDROID_NDK_HOME"; exit 1; }
echo "NDK: $ANDROID_NDK_HOME"
BIN="$(ls -d "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin | head -1)"
STRIP="$BIN/llvm-strip"

mkdir -p "$WORK" "$JNI"
cd "$WORK"
SRC="$WORK/Stockfish-$SF_TAG/src"
if [[ ! -d "$SRC" ]]; then
  echo "Tải mã nguồn Stockfish ${SF_TAG}…"
  curl -fL --retry 3 -o "stockfish-$SF_TAG.tar.gz" "https://github.com/official-stockfish/Stockfish/archive/refs/tags/$SF_TAG.tar.gz"
  tar xzf "stockfish-$SF_TAG.tar.gz"
fi
# Giữ một bản mã nguồn đúng phiên bản trong repo (nghĩa vụ GPL: cung cấp mã nguồn khi phát hành bản nhị phân)
[[ -f "$ROOT/engine-native/stockfish-$SF_TAG.tar.gz" ]] || cp "stockfish-$SF_TAG.tar.gz" "$ROOT/engine-native/" 2>/dev/null || true

SOURCES=$(ls "$SRC"/*.cpp "$SRC"/syzygy/*.cpp)
JOBS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"

for ABI in $ABIS; do
  if [[ -s "$JNI/$ABI/libstockfish.so" && "${REBUILD:-0}" != "1" ]]; then echo "=== $ABI: đã có libstockfish.so (REBUILD=1 để build lại)"; continue; fi
  echo "=== $ABI ==="
  case "$ABI" in
    arm64-v8a)   CXX="$BIN/aarch64-linux-android$API-clang++";   FLAGS="-DIS_64BIT -DUSE_POPCNT" ;;
    armeabi-v7a) CXX="$BIN/armv7a-linux-androideabi$API-clang++"; FLAGS="-mthumb -march=armv7-a -mfloat-abi=softfp -mfpu=neon" ;;
    x86_64)      CXX="$BIN/x86_64-linux-android$API-clang++";    FLAGS="-DIS_64BIT -DUSE_POPCNT -msse4.2 -mpopcnt" ;;
    x86)         CXX="$BIN/i686-linux-android$API-clang++";      FLAGS="" ;;
    *) echo "ABI lạ: $ABI"; exit 1 ;;
  esac
  OUT="$WORK/out-$ABI"; rm -rf "$OUT"; mkdir -p "$OUT"   # xoá .o cũ để lần lỗi trước không bị tính là đã build
  # Biên dịch song song từng file rồi liên kết
  objs=()
  for f in $SOURCES; do
    o="$OUT/$(basename "${f%.cpp}").o"
    objs+=("$o")
    "$CXX" -std=c++11 -O3 -DNDEBUG -fPIE $FLAGS -Wno-deprecated-declarations -c "$f" -o "$o" &
    while (( $(jobs -r | wc -l) >= JOBS )); do sleep 0.2; done
  done
  wait
  for o in "${objs[@]}"; do [[ -s "$o" ]] || { echo "Lỗi biên dịch: $o"; exit 1; }; done
  "$CXX" -O3 -fPIE -pie -static-libstdc++ "${objs[@]}" -o "$OUT/stockfish" -lm
  mkdir -p "$JNI/$ABI"
  cp "$OUT/stockfish" "$JNI/$ABI/libstockfish.so"
  "$STRIP" "$JNI/$ABI/libstockfish.so"
  ls -lh "$JNI/$ABI/libstockfish.so"
done
echo "Xong. Build lại app để đóng gói Stockfish."
