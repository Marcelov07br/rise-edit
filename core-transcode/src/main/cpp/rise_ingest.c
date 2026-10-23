// Minimal JNI over FFmpeg (LGPL build, tools/ffmpeg/build.sh): open a file descriptor, decode the
// first video stream and convert each frame with swscale straight into a MediaCodec input Image.
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <android/log.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/display.h>
#include <libavutil/pixdesc.h>
#include <libswscale/swscale.h>

#define TAG "RiseIngest"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

typedef struct {
    AVFormatContext *fmt;
    AVCodecContext *dec;
    AVPacket *pkt;
    AVFrame *frame;
    struct SwsContext *sws;
    int stream, eof_sent, src_fmt, dst_fmt;
} Ingest;

enum { I_W, I_H, I_ROT, I_DUR, I_FPS_N, I_FPS_D, I_PRIM, I_TRC, I_MTX, I_RANGE, I_BITS, I_CHROMA, I_FRAMES, I_COUNT };

static void close_ingest(Ingest *s) {
    if (!s) return;
    sws_freeContext(s->sws);
    av_frame_free(&s->frame);
    av_packet_free(&s->pkt);
    avcodec_free_context(&s->dec);
    avformat_close_input(&s->fmt);
    free(s);
}

JNIEXPORT jlong JNICALL
Java_br_com_riseedit_transcode_FfmpegIngest_nativeOpen(JNIEnv *env, jclass cls, jint fd, jlongArray out) {
    char url[64];
    snprintf(url, sizeof url, "/proc/self/fd/%d", fd);
    Ingest *s = calloc(1, sizeof *s);
    if (avformat_open_input(&s->fmt, url, NULL, NULL) < 0 || avformat_find_stream_info(s->fmt, NULL) < 0) {
        LOGW("open failed"); close_ingest(s); return 0;
    }
    const AVCodec *codec = NULL;
    s->stream = av_find_best_stream(s->fmt, AVMEDIA_TYPE_VIDEO, -1, -1, &codec, 0);
    if (s->stream < 0 || !codec) { LOGW("no decodable video"); close_ingest(s); return 0; }
    AVStream *st = s->fmt->streams[s->stream];
    s->dec = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(s->dec, st->codecpar);
    s->dec->thread_count = 0;
    if (avcodec_open2(s->dec, codec, NULL) < 0) { LOGW("decoder open failed"); close_ingest(s); return 0; }
    s->pkt = av_packet_alloc();
    s->frame = av_frame_alloc();
    s->src_fmt = s->dst_fmt = -1;

    jlong v[I_COUNT] = {0};
    v[I_W] = st->codecpar->width;
    v[I_H] = st->codecpar->height;
    const AVPacketSideData *sd = av_packet_side_data_get(st->codecpar->coded_side_data,
        st->codecpar->nb_coded_side_data, AV_PKT_DATA_DISPLAYMATRIX);
    if (sd) {
        double r = -av_display_rotation_get((const int32_t *)sd->data);
        int deg = ((int)(r + (r < 0 ? -0.5 : 0.5)) % 360 + 360) % 360;
        v[I_ROT] = deg;
    }
    v[I_DUR] = st->duration != AV_NOPTS_VALUE ? av_rescale_q(st->duration, st->time_base, AV_TIME_BASE_Q)
                                              : (s->fmt->duration > 0 ? s->fmt->duration : 0);
    AVRational fr = st->avg_frame_rate.num ? st->avg_frame_rate : st->r_frame_rate;
    v[I_FPS_N] = fr.num; v[I_FPS_D] = fr.den;
    v[I_PRIM] = st->codecpar->color_primaries;
    v[I_TRC] = st->codecpar->color_trc;
    v[I_MTX] = st->codecpar->color_space;
    v[I_RANGE] = st->codecpar->color_range;
    const AVPixFmtDescriptor *d = av_pix_fmt_desc_get(st->codecpar->format);
    v[I_BITS] = d ? d->comp[0].depth : 0;
    v[I_CHROMA] = d ? (d->log2_chroma_w == 1 && d->log2_chroma_h == 0 ? 422 : d->log2_chroma_w == 0 ? 444 : 420) : 0;
    v[I_FRAMES] = st->nb_frames;
    (*env)->SetLongArrayRegion(env, out, 0, I_COUNT, v);
    return (jlong)(intptr_t)s;
}

// Returns the next frame's pts (µs), -1 at end of stream, -2 on error.
static int64_t next_frame(Ingest *s) {
    for (;;) {
        int r = avcodec_receive_frame(s->dec, s->frame);
        if (r == 0) {
            int64_t ts = s->frame->best_effort_timestamp;
            if (ts == AV_NOPTS_VALUE) ts = s->frame->pts;
            AVStream *st = s->fmt->streams[s->stream];
            int64_t start = st->start_time != AV_NOPTS_VALUE ? st->start_time : 0;
            return ts == AV_NOPTS_VALUE ? 0 : av_rescale_q(ts - start, st->time_base, AV_TIME_BASE_Q);
        }
        if (r == AVERROR_EOF) return -1;
        if (r != AVERROR(EAGAIN)) return -2;
        if (s->eof_sent) return -1;
        r = av_read_frame(s->fmt, s->pkt);
        if (r < 0) { avcodec_send_packet(s->dec, NULL); s->eof_sent = 1; continue; }
        if (s->pkt->stream_index == s->stream) avcodec_send_packet(s->dec, s->pkt);
        av_packet_unref(s->pkt);
    }
}

JNIEXPORT jlong JNICALL
Java_br_com_riseedit_transcode_FfmpegIngest_nativeDecode(JNIEnv *env, jclass cls, jlong h,
        jobject y, jint ys, jobject u, jint us, jint up, jobject v, jint vs, jint vp, jint layout) {
    Ingest *s = (Ingest *)(intptr_t)h;
    int64_t pts = next_frame(s);
    if (pts < 0) return pts;
    uint8_t *py = (*env)->GetDirectBufferAddress(env, y);
    uint8_t *pu = (*env)->GetDirectBufferAddress(env, u);
    uint8_t *pv = (*env)->GetDirectBufferAddress(env, v);
    if (!py || !pu || !pv) return -2;
    uint8_t *dst[4] = {py, NULL, NULL, NULL};
    int stride[4] = {ys, 0, 0, 0};
    int fmt;
    if (layout == 2) {                    // P210: 4:2:2 semi-planar, 16-bit, 10 bits in the MSBs
        fmt = AV_PIX_FMT_P210LE;
        dst[1] = pu < pv ? pu : pv; stride[1] = us;
    } else if (layout == 1) {             // P010: interleaved 16-bit Cb/Cr, 10 bits in the MSBs
        fmt = AV_PIX_FMT_P010LE;
        dst[1] = pu < pv ? pu : pv; stride[1] = us;
    } else if (up == 1) {                 // I420
        fmt = AV_PIX_FMT_YUV420P;
        dst[1] = pu; stride[1] = us; dst[2] = pv; stride[2] = vs;
    } else if (pv == pu + 1) {            // NV12
        fmt = AV_PIX_FMT_NV12; dst[1] = pu; stride[1] = us;
    } else {                              // NV21
        fmt = AV_PIX_FMT_NV21; dst[1] = pv; stride[1] = vs;
    }
    AVFrame *f = s->frame;
    if (!s->sws || s->src_fmt != f->format || s->dst_fmt != fmt) {
        sws_freeContext(s->sws);
        s->sws = sws_getContext(f->width, f->height, f->format, f->width, f->height, fmt,
                                SWS_BILINEAR | SWS_ACCURATE_RND, NULL, NULL, NULL);
        s->src_fmt = f->format; s->dst_fmt = fmt;
        if (!s->sws) return -2;
    }
    sws_scale(s->sws, (const uint8_t *const *)f->data, f->linesize, 0, f->height, dst, stride);
    av_frame_unref(f);
    return pts;
}

JNIEXPORT void JNICALL
Java_br_com_riseedit_transcode_FfmpegIngest_nativeClose(JNIEnv *env, jclass cls, jlong h) {
    close_ingest((Ingest *)(intptr_t)h);
}

JNIEXPORT jstring JNICALL
Java_br_com_riseedit_transcode_FfmpegIngest_nativeVersion(JNIEnv *env, jclass cls) {
    char b[128];
    snprintf(b, sizeof b, "FFmpeg %s · %s", av_version_info(), avcodec_license());
    return (*env)->NewStringUTF(env, b);
}
