# Media compatibility fixtures

The files in `app/src/androidTest/assets/compatibility/` are synthetic test assets generated locally on 2026-10-11. They contain FFmpeg's moving `testsrc2` pattern or a 440 Hz sine wave. No camera footage, downloaded media, or third-party creative work was used. The entire corpus is 237,230 bytes (about 232 KiB).

The generator was `ffmpeg version 2025-10-09-git-469aad3897-essentials_build-www.gyan.dev`, using libavcodec 62.16.100 and libavformat 62.6.101. The installed encoder list included `libx264`, `libx265`, `libvpx-vp9`, `libaom-av1`, `aac`, `libmp3lame`, `libopus`, and `pcm_s16le`. It did **not** include an APV encoder. There is no APV fixture, and this corpus does not validate APV decoding or conversion.

## Contents and expected source semantics

All video fixtures have 12 frames at 160 × 90, 12 fps, and approximately one second of duration. Audio is mono at 48 kHz and approximately one second long; codec delay and container timestamps may differ slightly. Bit depth and chroma below describe the encoded source, not a decoder's output format.

| File | Container | Video profile and source pixels | Audio | Bytes |
| --- | --- | --- | --- | ---: |
| `h264_8bit_aac.mp4` | MP4 | H.264 Constrained Baseline, 8-bit 4:2:0 | AAC LC | 24,825 |
| `h264_10bit.mp4` | MP4 | H.264 High 10, 10-bit 4:2:0 | None | 19,920 |
| `h264_422.mkv` | Matroska | H.264 High 4:2:2, 8-bit 4:2:2 | None | 23,907 |
| `hevc_8bit.mkv` | Matroska | HEVC Main, 8-bit 4:2:0 | None | 14,355 |
| `hevc_10bit.mkv` | Matroska | HEVC Main 10, 10-bit 4:2:0 | None | 14,317 |
| `hevc_hdr10.mkv` | Matroska | HEVC Main 10, 10-bit 4:2:0, BT.2020/PQ | None | 16,997 |
| `vp9_8bit.webm` | WebM | VP9 Profile 0, 8-bit 4:2:0 | None | 12,898 |
| `av1_8bit.mkv` | Matroska | AV1 Main, 8-bit 4:2:0 | None | 5,477 |
| `mp3.mp3` | MP3 | None | MPEG audio layer III | 4,364 |
| `opus.ogg` | Ogg | None | Opus | 4,092 |
| `pcm_s16le.wav` | WAV | None | Signed 16-bit little-endian PCM | 96,078 |

The HDR fixture carries PQ/BT.2020 signalling, mastering display metadata (1,000-nit maximum, 0.0001-nit minimum), MaxCLL 1,000 and MaxFALL 400. Its synthetic pattern is a metadata test; it is not a reference for perceptual HDR rendering or tone-map accuracy.

## Reproduction

Run from the repository root using the FFmpeg version above. Matroska/WebM muxing may assign new identifiers, so regenerated container hashes need not be identical even when stream semantics match.

```powershell
$ffmpeg = 'C:\yt-dlp\ffmpeg.exe'
$out = 'app\src\androidTest\assets\compatibility'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$video = @('-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i', 'testsrc2=size=160x90:rate=12:duration=1')
$tone = @('-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000:duration=1')
$hevc = 'log-level=error:pools=1:frame-threads=1'
$hdr = "$hevc`:hdr10=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"

& $ffmpeg @video @tone -c:v libx264 -preset ultrafast -crf 28 -pix_fmt yuv420p -profile:v baseline -c:a aac -b:a 32k -shortest -movflags +faststart "$out\h264_8bit_aac.mp4"
& $ffmpeg @video -c:v libx264 -preset ultrafast -crf 28 -pix_fmt yuv420p10le -profile:v high10 -an -movflags +faststart "$out\h264_10bit.mp4"
& $ffmpeg @video -c:v libx264 -preset ultrafast -crf 28 -pix_fmt yuv422p -profile:v high422 -an "$out\h264_422.mkv"
& $ffmpeg @video -c:v libx265 -preset ultrafast -crf 30 -pix_fmt yuv420p -x265-params $hevc -an "$out\hevc_8bit.mkv"
& $ffmpeg @video -c:v libx265 -preset ultrafast -crf 30 -pix_fmt yuv420p10le -x265-params $hevc -an "$out\hevc_10bit.mkv"
& $ffmpeg @video -c:v libx265 -preset ultrafast -crf 30 -pix_fmt yuv420p10le -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -x265-params $hdr -an "$out\hevc_hdr10.mkv"
& $ffmpeg @video -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -crf 40 -b:v 0 -pix_fmt yuv420p -an "$out\vp9_8bit.webm"
& $ffmpeg @video -c:v libaom-av1 -cpu-used 8 -crf 42 -b:v 0 -pix_fmt yuv420p -an "$out\av1_8bit.mkv"
& $ffmpeg -hide_banner -loglevel error -y @tone -c:a libmp3lame -b:a 32k "$out\mp3.mp3"
& $ffmpeg -hide_banner -loglevel error -y @tone -c:a libopus -b:a 24k "$out\opus.ogg"
& $ffmpeg -hide_banner -loglevel error -y @tone -c:a pcm_s16le "$out\pcm_s16le.wav"
```

Every checked-in fixture was decoded with `ffmpeg -hide_banner -loglevel error -xerror -i <file> -f null NUL`; all returned exit code zero and no error output. This confirms that the fixture streams are readable by this FFmpeg build. It does not establish Android decoder availability.

## Android test scope

`MediaCompatibilityTest` exercises the new compatibility APIs independently of the existing `HomeFlowTest` and `VideoPipelineTest`. Source bit depth or chroma may remain explicitly `UNKNOWN` when the platform cannot establish them; a known value must agree with the fixture. Decoder support is discovered at runtime and is not inferred from the filename or fixture's existence. Proxy conversion uses the actual `ProxyTranscoder` and playback uses ExoPlayer. Input formats without a suitable native decoder must receive a typed, actionable failure rather than a claim that Media3 can decode them. A conversion or rejection test whose required capability branch cannot run is reported as skipped, so its absence is not reported as successful validation.

Run on the project's API 35 Google APIs x86_64 emulator with:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=br.com.riseedit.MediaCompatibilityTest'
```

## SHA-256 of the checked-in corpus

| File | SHA-256 |
| --- | --- |
| `av1_8bit.mkv` | `4ce08f1a1099e2ed9337a320feecd55c8585f6cb80e7792b2ff852ea926b878b` |
| `h264_10bit.mp4` | `48d3c0c144cbf205b0361b1937bef877f8cae50498006db6a3977bf571595dfe` |
| `h264_422.mkv` | `f09b99df41abba686847a929c1880f9f21338dc615d4bf55cb3514514db74f62` |
| `h264_8bit_aac.mp4` | `bf5203b5a540c70bb90295c878e6f1b882995312ac440b3781315347bd953e11` |
| `hevc_10bit.mkv` | `7d75b6f9fb988405086090a08f493959979f6968e8d7c31f17b2a13e57175c0e` |
| `hevc_8bit.mkv` | `ae131a72e1a183f4d163ff51e62755f318f3ba97c58b27d228c3f6f45aee3d9b` |
| `hevc_hdr10.mkv` | `f749633d8b163f075a12d88369ffe18721d4a7155ce49c77afd2feea47b76509` |
| `mp3.mp3` | `c65df6668cb112be8be8ae9f3466816483915fff230cd4396c1e464cba88fbde` |
| `opus.ogg` | `f0f17893301edb7e1b1e1ba5dc915e9b33b3a36626ae69510b2e94b299404226` |
| `pcm_s16le.wav` | `58df84e90097a8d07d4d62e9e8fd9a69c8289bb45367ec6f3516ee704b9459b0` |
| `vp9_8bit.webm` | `a00555331a01aea015da70731a8b0638e53c0a58c8d8952cf5d3accd80ee966d` |
