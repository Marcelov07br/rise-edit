#!/usr/bin/env bash
# Host (x86_64 Linux) check of the JNI: same LGPL FFmpeg configure, static, linked into
# libhost_ingest.so, then the :core-transcode JVM test decodes real 4:2:2 10-bit samples.
# Samples: generated with the box's system ffmpeg (libx264 high422 / libx265 main422-10).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
WORK=${WORK:-/tmp/ffmpeg-host}; VER=7.1.1
mkdir -p "$WORK"; cd "$WORK"
cp -n /tmp/ffmpeg-build/ffmpeg-$VER.tar.xz . 2>/dev/null || [ -f ffmpeg-$VER.tar.xz ] || curl -fsSLO https://ffmpeg.org/releases/ffmpeg-$VER.tar.xz
STAMP=prores-aac-v1
if [ "$(cat install/.stamp 2>/dev/null)" != "$STAMP" ]; then
  rm -rf src && mkdir src && tar xf ffmpeg-$VER.tar.xz -C src --strip-components=1 && cd src
  ./configure --prefix="$WORK/install" --enable-pic --disable-everything --disable-programs --disable-doc \
    --disable-avdevice --disable-avfilter --disable-network --disable-autodetect --disable-asm \
    --enable-decoder=h264,hevc,aac --enable-parser=h264,hevc,aac --enable-demuxer=mov,matroska --enable-protocol=file,fd \
    --enable-encoder=prores_ks,pcm_s16le --enable-muxer=mov
  MAKE=$(command -v make || echo /home/box/android/ndk/27.2.12479018/prebuilt/linux-x86_64/bin/make)
  "$MAKE" -j"$(nproc)" && "$MAKE" install && echo "$STAMP" > "$WORK/install/.stamp" && cd ..
fi
J=${JAVA_HOME:-/home/box/jdk}
SAMPLES=${SAMPLES:-/workspace/samples}
mkdir -p "$SAMPLES"
# VFR (frames 40-49 dropped), rotation display matrix, LPCM audio — like a camera .mov.
[ -f "$SAMPLES/h264_422_10bit.mov" ] || { ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1080x1920:rate=30000/1001:duration=3" -f lavfi -i "sine=duration=3:sample_rate=48000" \
  -vf "select='not(between(n,40,49))'" -fps_mode passthrough \
  -c:v libx264 -profile:v high422 -pix_fmt yuv422p10le -c:a pcm_s16le "$SAMPLES/tmp.mov" &&
  ffmpeg -hide_banner -loglevel error -y -display_rotation 90 -i "$SAMPLES/tmp.mov" -c copy "$SAMPLES/h264_422_10bit.mov" &&
  rm "$SAMPLES/tmp.mov"; }
[ -f "$SAMPLES/hevc_422_10bit_hlg.mp4" ] || ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1920x1080:rate=25:duration=2" -c:v libx265 \
  -x265-params "log-level=error:colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc" \
  -profile:v main422-10 -pix_fmt yuv422p10le "$SAMPLES/hevc_422_10bit_hlg.mp4"
# 0.21: the export intermediate (HEVC Main10 4:2:0 HLG + AAC) for the ProRes encode test.
[ -f "$SAMPLES/hevc10_aac_intermediate.mp4" ] || ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=30:duration=2" -f lavfi -i "sine=duration=2:sample_rate=48000" -c:v libx265 \
  -x265-params "log-level=error:colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc" \
  -profile:v main10 -pix_fmt yuv420p10le -c:a aac -ac 2 "$SAMPLES/hevc10_aac_intermediate.mp4"
# Android log shim so rise_ingest.c compiles unchanged on the host.
mkdir -p shim/android && printf '#include <stdio.h>\n#define ANDROID_LOG_WARN 5\n#define __android_log_print(p,t,...) fprintf(stderr,__VA_ARGS__)\n' > shim/android/log.h
cc -shared -fPIC -O2 -o libhost_ingest.so "$ROOT/core-transcode/src/main/cpp/rise_ingest.c" "$ROOT/core-transcode/src/main/cpp/rise_prores.c" \
  -Ishim -I"$WORK/install/include" -I"$J/include" -I"$J/include/linux" \
  -L"$WORK/install/lib" -Wl,--whole-archive -lavformat -lavcodec -lswscale -lavutil -Wl,--no-whole-archive -lm -lpthread
cd "$ROOT" && RISE_HOST_INGEST="$WORK/libhost_ingest.so" RISE_SAMPLES="$SAMPLES" \
  ./gradlew :core-transcode:testDebugUnitTest --tests '*HostIngestTest*' --tests '*HostProresTest*' --rerun-tasks
