// 0.21 export finisher: decode the high-bitrate intermediate (HEVC/H.264 from Media3) and encode
// ProRes 422 (prores_ks, LGPL) 10-bit 4:2:2 into a .mov. Audio: AAC copied, or AAC -> PCM s16le.
// Keeps per-frame pts (VFR), rotation (display matrix) and color tags.
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <android/log.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/opt.h>
#include <libavutil/channel_layout.h>
#include <libswscale/swscale.h>

#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "RiseProres", __VA_ARGS__)

typedef struct {
    AVFormatContext *in, *out;
    AVCodecContext *vdec, *venc, *adec, *aenc;
    struct SwsContext *sws;
    AVFrame *frame, *conv, *pcm;
    AVPacket *pkt, *opkt;
    int vi, ai, vo, ao;
    int64_t pcm_next;
} Job;

static void job_free(Job *j) {
    if (j->out) {
        if (!(j->out->oformat->flags & AVFMT_NOFILE)) avio_closep(&j->out->pb);
        avformat_free_context(j->out);
    }
    avformat_close_input(&j->in);
    avcodec_free_context(&j->vdec); avcodec_free_context(&j->venc);
    avcodec_free_context(&j->adec); avcodec_free_context(&j->aenc);
    sws_freeContext(j->sws);
    av_frame_free(&j->frame); av_frame_free(&j->conv); av_frame_free(&j->pcm);
    av_packet_free(&j->pkt); av_packet_free(&j->opkt);
}

static int drain(Job *j, AVCodecContext *enc, int ost) {
    for (;;) {
        int r = avcodec_receive_packet(enc, j->opkt);
        if (r == AVERROR(EAGAIN) || r == AVERROR_EOF) return 0;
        if (r < 0) return r;
        av_packet_rescale_ts(j->opkt, enc->time_base, j->out->streams[ost]->time_base);
        j->opkt->stream_index = ost;
        if ((r = av_interleaved_write_frame(j->out, j->opkt)) < 0) return r;
    }
}

static int encode_video(Job *j, AVFrame *src) {
    if (!src) { avcodec_send_frame(j->venc, NULL); return drain(j, j->venc, j->vo); }
    if (!j->sws) {
        j->sws = sws_getContext(src->width, src->height, src->format, src->width, src->height,
                                AV_PIX_FMT_YUV422P10LE, SWS_BICUBIC | SWS_ACCURATE_RND | SWS_FULL_CHR_H_INT,
                                NULL, NULL, NULL);
        if (!j->sws) return -1;
    }
    av_frame_make_writable(j->conv);
    sws_scale(j->sws, (const uint8_t *const *)src->data, src->linesize, 0, src->height, j->conv->data, j->conv->linesize);
    int64_t ts = src->best_effort_timestamp != AV_NOPTS_VALUE ? src->best_effort_timestamp : src->pts;
    j->conv->pts = ts;
    int r = avcodec_send_frame(j->venc, j->conv);
    return r < 0 ? r : drain(j, j->venc, j->vo);
}

// AAC decoder gives planar float; PCM s16le wants interleaved shorts.
static int encode_pcm(Job *j, AVFrame *src) {
    if (!src) { avcodec_send_frame(j->aenc, NULL); return drain(j, j->aenc, j->ao); }
    int ch = j->aenc->ch_layout.nb_channels, n = src->nb_samples;
    av_frame_unref(j->pcm);
    j->pcm->format = AV_SAMPLE_FMT_S16; j->pcm->nb_samples = n; j->pcm->sample_rate = j->aenc->sample_rate;
    av_channel_layout_copy(&j->pcm->ch_layout, &j->aenc->ch_layout);
    if (av_frame_get_buffer(j->pcm, 0) < 0) return -1;
    int16_t *o = (int16_t *)j->pcm->data[0];
    int planar = av_sample_fmt_is_planar(src->format);
    for (int i = 0; i < n; i++) for (int c = 0; c < ch; c++) {
        float v;
        int sc = c < src->ch_layout.nb_channels ? c : 0;
        if (src->format == AV_SAMPLE_FMT_FLTP || src->format == AV_SAMPLE_FMT_FLT)
            v = planar ? ((float *)src->data[sc])[i] : ((float *)src->data[0])[i * src->ch_layout.nb_channels + sc];
        else
            v = (planar ? ((int16_t *)src->data[sc])[i] : ((int16_t *)src->data[0])[i * src->ch_layout.nb_channels + sc]) / 32768.f;
        v = v > 1.f ? 1.f : v < -1.f ? -1.f : v;
        o[i * ch + c] = (int16_t)(v * 32767.f);
    }
    j->pcm->pts = j->pcm_next; j->pcm_next += n;
    int r = avcodec_send_frame(j->aenc, j->pcm);
    return r < 0 ? r : drain(j, j->aenc, j->ao);
}

static int decode_into(Job *j, AVCodecContext *dec, AVPacket *p, int video) {
    int r = avcodec_send_packet(dec, p);
    if (r < 0 && r != AVERROR_EOF) return r;
    for (;;) {
        r = avcodec_receive_frame(dec, j->frame);
        if (r == AVERROR(EAGAIN) || r == AVERROR_EOF) return 0;
        if (r < 0) return r;
        r = video ? encode_video(j, j->frame) : encode_pcm(j, j->frame);
        av_frame_unref(j->frame);
        if (r < 0) return r;
    }
}

static int setup(Job *j, const char *in_url, const char *out_path, int profile, int pcm) {
    int r;
    if ((r = avformat_open_input(&j->in, in_url, NULL, NULL)) < 0) return r;
    if ((r = avformat_find_stream_info(j->in, NULL)) < 0) return r;
    const AVCodec *vc = NULL;
    j->vi = av_find_best_stream(j->in, AVMEDIA_TYPE_VIDEO, -1, -1, &vc, 0);
    if (j->vi < 0 || !vc) return -1;
    j->ai = av_find_best_stream(j->in, AVMEDIA_TYPE_AUDIO, -1, j->vi, NULL, 0);
    AVStream *vs = j->in->streams[j->vi];
    j->vdec = avcodec_alloc_context3(vc);
    avcodec_parameters_to_context(j->vdec, vs->codecpar);
    j->vdec->thread_count = 0;
    j->vdec->pkt_timebase = vs->time_base;
    if ((r = avcodec_open2(j->vdec, vc, NULL)) < 0) return r;

    if ((r = avformat_alloc_output_context2(&j->out, NULL, "mov", out_path)) < 0) return r;
    const AVCodec *pc = avcodec_find_encoder_by_name("prores_ks");
    if (!pc) return -1;
    j->venc = avcodec_alloc_context3(pc);
    j->venc->width = j->vdec->width; j->venc->height = j->vdec->height;
    j->venc->pix_fmt = AV_PIX_FMT_YUV422P10LE;
    j->venc->time_base = vs->time_base;
    j->venc->framerate = vs->avg_frame_rate.num ? vs->avg_frame_rate : vs->r_frame_rate;
    j->venc->sample_aspect_ratio = j->vdec->sample_aspect_ratio;
    j->venc->color_primaries = j->vdec->color_primaries;
    j->venc->color_trc = j->vdec->color_trc;
    j->venc->colorspace = j->vdec->colorspace;
    j->venc->color_range = AVCOL_RANGE_MPEG;
    j->venc->thread_count = 0;
    av_opt_set_int(j->venc->priv_data, "profile", profile, 0);  // 2 = standard (apcn), 3 = HQ (apch)
    av_opt_set(j->venc->priv_data, "vendor", "apl0", 0);
    if (j->out->oformat->flags & AVFMT_GLOBALHEADER) j->venc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    if ((r = avcodec_open2(j->venc, pc, NULL)) < 0) return r;
    AVStream *ov = avformat_new_stream(j->out, NULL);
    j->vo = ov->index;
    avcodec_parameters_from_context(ov->codecpar, j->venc);
    ov->time_base = vs->time_base;
    ov->avg_frame_rate = j->venc->framerate;
    const AVPacketSideData *dm = av_packet_side_data_get(vs->codecpar->coded_side_data,
        vs->codecpar->nb_coded_side_data, AV_PKT_DATA_DISPLAYMATRIX);
    if (dm) {
        AVPacketSideData *c = av_packet_side_data_new(&ov->codecpar->coded_side_data,
            &ov->codecpar->nb_coded_side_data, AV_PKT_DATA_DISPLAYMATRIX, dm->size, 0);
        if (c) memcpy(c->data, dm->data, dm->size);
    }

    j->ao = -1;
    if (j->ai >= 0) {
        AVStream *as = j->in->streams[j->ai];
        AVStream *oa = avformat_new_stream(j->out, NULL);
        j->ao = oa->index;
        if (!pcm) {
            avcodec_parameters_copy(oa->codecpar, as->codecpar);
            oa->codecpar->codec_tag = 0;
            oa->time_base = as->time_base;
        } else {
            const AVCodec *ad = avcodec_find_decoder(as->codecpar->codec_id);
            const AVCodec *pe = avcodec_find_encoder(AV_CODEC_ID_PCM_S16LE);
            if (!ad || !pe) return -1;
            j->adec = avcodec_alloc_context3(ad);
            avcodec_parameters_to_context(j->adec, as->codecpar);
            j->adec->pkt_timebase = as->time_base;
            if ((r = avcodec_open2(j->adec, ad, NULL)) < 0) return r;
            j->aenc = avcodec_alloc_context3(pe);
            j->aenc->sample_fmt = AV_SAMPLE_FMT_S16;
            j->aenc->sample_rate = j->adec->sample_rate;
            int ch = j->adec->ch_layout.nb_channels > 2 ? 2 : j->adec->ch_layout.nb_channels;
            av_channel_layout_default(&j->aenc->ch_layout, ch < 1 ? 2 : ch);
            j->aenc->time_base = (AVRational){1, j->aenc->sample_rate};
            if ((r = avcodec_open2(j->aenc, pe, NULL)) < 0) return r;
            avcodec_parameters_from_context(oa->codecpar, j->aenc);
            oa->time_base = j->aenc->time_base;
        }
    }
    if ((r = avio_open(&j->out->pb, out_path, AVIO_FLAG_WRITE)) < 0) return r;
    if ((r = avformat_write_header(j->out, NULL)) < 0) return r;
    j->frame = av_frame_alloc(); j->pcm = av_frame_alloc();
    j->conv = av_frame_alloc();
    j->conv->format = AV_PIX_FMT_YUV422P10LE; j->conv->width = j->venc->width; j->conv->height = j->venc->height;
    if (av_frame_get_buffer(j->conv, 0) < 0) return -1;
    j->pkt = av_packet_alloc(); j->opkt = av_packet_alloc();
    return 0;
}

// Returns 0 on success, 1 if cancelled, <0 FFmpeg error. progress.onProgress(ptsUs) -> false cancels.
JNIEXPORT jint JNICALL
Java_br_com_riseedit_transcode_ProresNative_nativeEncode(JNIEnv *env, jclass cls, jint fd, jstring out,
        jint profile, jboolean pcm, jobject progress) {
    char url[64];
    snprintf(url, sizeof url, "/proc/self/fd/%d", fd);
    const char *path = (*env)->GetStringUTFChars(env, out, NULL);
    jmethodID cb = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, progress), "onProgress", "(J)Z");
    Job j; memset(&j, 0, sizeof j);
    int r = setup(&j, url, path, profile, pcm);
    (*env)->ReleaseStringUTFChars(env, out, path);
    if (r < 0) { LOGW("setup failed %d", r); job_free(&j); return r; }
    AVStream *vs = j.in->streams[j.vi];
    int64_t start = vs->start_time != AV_NOPTS_VALUE ? vs->start_time : 0;
    int cancelled = 0, n = 0;
    while (r >= 0 && av_read_frame(j.in, j.pkt) >= 0) {
        if (j.pkt->stream_index == j.vi) {
            r = decode_into(&j, j.vdec, j.pkt, 1);
            if ((++n & 7) == 0 && j.pkt->pts != AV_NOPTS_VALUE) {
                jlong us = av_rescale_q(j.pkt->pts - start, vs->time_base, AV_TIME_BASE_Q);
                if (!(*env)->CallBooleanMethod(env, progress, cb, us)) cancelled = 1;
            }
        } else if (j.pkt->stream_index == j.ai && j.ao >= 0) {
            if (j.adec) r = decode_into(&j, j.adec, j.pkt, 0);
            else {
                av_packet_rescale_ts(j.pkt, j.in->streams[j.ai]->time_base, j.out->streams[j.ao]->time_base);
                j.pkt->stream_index = j.ao; j.pkt->pos = -1;
                r = av_interleaved_write_frame(j.out, j.pkt);
            }
        }
        av_packet_unref(j.pkt);
        if (cancelled) break;
    }
    if (!cancelled && r >= 0) {
        r = decode_into(&j, j.vdec, NULL, 1);
        if (r >= 0) r = encode_video(&j, NULL);
        if (r >= 0 && j.adec) { r = decode_into(&j, j.adec, NULL, 0); if (r >= 0) r = encode_pcm(&j, NULL); }
        if (r >= 0) r = av_write_trailer(j.out);
    }
    if (r < 0) LOGW("encode failed %d", r);
    job_free(&j);
    return cancelled ? 1 : (r < 0 ? r : 0);
}
