#!/usr/bin/env bash
# Reproducible LGPL FFmpeg build for Rise Edit (arm64-v8a). No --enable-gpl, no nonfree.
# Output: core-transcode/src/main/cpp/ffmpeg/{include,lib/arm64-v8a}
set -euo pipefail
VER=${FFMPEG_VERSION:-7.1.1}
SHA256=${FFMPEG_SHA256:-733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1}
NDK=${ANDROID_NDK:-/home/box/android/ndk/27.2.12479018}
API=27
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
WORK=${WORK:-/tmp/ffmpeg-build}
OUT=$ROOT/core-transcode/src/main/cpp/ffmpeg
mkdir -p "$WORK"; cd "$WORK"
[ -f ffmpeg-$VER.tar.xz ] || curl -fsSLO https://ffmpeg.org/releases/ffmpeg-$VER.tar.xz
[ -z "$SHA256" ] || echo "$SHA256  ffmpeg-$VER.tar.xz" | sha256sum -c -
rm -rf src && mkdir src && tar xf ffmpeg-$VER.tar.xz -C src --strip-components=1
cd src
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
./configure \
  --prefix="$WORK/install" --target-os=android --arch=aarch64 --cpu=armv8-a \
  --enable-cross-compile --cc="$TC/bin/aarch64-linux-android$API-clang" \
  --cxx="$TC/bin/aarch64-linux-android$API-clang++" --nm="$TC/bin/llvm-nm" \
  --ar="$TC/bin/llvm-ar" --ranlib="$TC/bin/llvm-ranlib" --strip="$TC/bin/llvm-strip" \
  --sysroot="$TC/sysroot" --enable-pic --enable-shared --disable-static \
  --disable-everything --disable-programs --disable-doc --disable-avdevice \
  --disable-avfilter --disable-postproc --disable-network --disable-debug \
  --disable-autodetect --enable-small \
  --enable-avcodec --enable-avformat --enable-avutil --enable-swscale \
  --enable-decoder=h264,hevc --enable-parser=h264,hevc \
  --enable-demuxer=mov,matroska --enable-protocol=file,fd \
  --enable-bsf=h264_mp4toannexb,hevc_mp4toannexb
# Plain sonames (libavcodec.so, not .so.61) so Android's loader finds them in the APK.
sed -i -e "s/^SLIBNAME_WITH_MAJOR=.*/SLIBNAME_WITH_MAJOR=\$(SLIBNAME)/" \
  -e "s/^SLIB_INSTALL_NAME=.*/SLIB_INSTALL_NAME=\$(SLIBNAME)/" \
  -e "s/^SLIB_INSTALL_LINKS=.*/SLIB_INSTALL_LINKS=/" ffbuild/config.mak
MAKE=$(command -v make || echo "$NDK/prebuilt/linux-x86_64/bin/make")
"$MAKE" -j"$(nproc)" && "$MAKE" install
rm -rf "$OUT"; mkdir -p "$OUT/lib/arm64-v8a"
cp -r "$WORK/install/include" "$OUT/"
for l in avcodec avformat avutil swscale; do
  cp "$WORK/install/lib/lib$l.so" "$OUT/lib/arm64-v8a/"
done
cp COPYING.LGPLv2.1 "$OUT/"
echo "$VER" > "$OUT/VERSION"
"$TC/bin/llvm-strip" --strip-unneeded "$OUT"/lib/arm64-v8a/*.so
echo "FFmpeg $VER LGPL built:"; ls -l "$OUT/lib/arm64-v8a"
