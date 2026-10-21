package br.com.riseedit.picker

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat

/** One MediaStore entry shown in the in-app picker. */
data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val video: Boolean,
    val durationMs: Long = 0,
    val name: String = ""
)

enum class GalleryTab(val label: String) { VIDEOS("Vídeos"), IMAGES("Imagens") }

/** Access granted to the app's media (Android 14 can grant only user-selected items). */
enum class GalleryAccess { NONE, PARTIAL, FULL }

object Gallery {
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun access(context: Context): GalleryAccess {
        fun has(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 &&
                (
                    has(Manifest.permission.READ_MEDIA_VIDEO) ||
                        has(Manifest.permission.READ_MEDIA_IMAGES)
                    ) -> GalleryAccess.FULL
            Build.VERSION.SDK_INT >= 34 &&
                has(
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) -> GalleryAccess.PARTIAL
            Build.VERSION.SDK_INT < 33 &&
                has(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) -> GalleryAccess.FULL
            else -> GalleryAccess.NONE
        }
    }

    fun query(context: Context, tab: GalleryTab, limit: Int = 600): List<GalleryItem> =
        runCatching {
            val video = tab == GalleryTab.VIDEOS
            val base = if (video) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val cols = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                if (video) add(MediaStore.Video.VideoColumns.DURATION)
            }.toTypedArray()
            val out = mutableListOf<GalleryItem>()
            context.contentResolver.query(
                base,
                cols,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    out += GalleryItem(
                        id,
                        ContentUris.withAppendedId(base, id),
                        video,
                        if (video) c.getLong(2) else 0L,
                        c.getString(1).orEmpty()
                    )
                }
            }
            out
        }.getOrDefault(emptyList())

    fun thumbnail(context: Context, item: GalleryItem, px: Int = 256): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(item.uri, Size(px, px), null)
        } else if (item.video) {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, item.uri)
                    getFrameAtTime(0)
                } finally {
                    release()
                }
            }
        } else {
            context.contentResolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize =
                            8
                    }
                )
            }
        }
    }.getOrNull()
}

/** Picker selection keeps tap order (numbers shown on the tiles). */
object PickerSelection {
    fun <T> toggle(selected: List<T>, item: T): List<T> = if (item in
        selected
    ) {
        selected - item
    } else {
        selected + item
    }
    fun <T> order(selected: List<T>, item: T): Int? =
        selected.indexOf(item).takeIf { it >= 0 }?.plus(1)
    fun duration(ms: Long): String {
        val s = (ms + 500) / 1000
        return if (s >=
            3600
        ) {
            "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
        } else {
            "%d:%02d".format(
                s / 60,
                s % 60
            )
        }
    }
}
