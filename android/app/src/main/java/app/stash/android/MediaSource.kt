package app.stash.android

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns as MC
import androidx.core.content.ContextCompat

data class MediaItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val modified: Long, // MediaStore DATE_MODIFIED, seconds
    val bucket: String,
)

object Perms {
    private fun granted(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun hasMedia(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            granted(c, Manifest.permission.READ_MEDIA_IMAGES) || granted(c, Manifest.permission.READ_MEDIA_VIDEO)
        else granted(c, Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Without this, Android hides the GPS position inside photos we read (the backup would not be an exact copy). */
    fun hasMediaLocation(c: Context) = granted(c, Manifest.permission.ACCESS_MEDIA_LOCATION)

    fun canNotify(c: Context) = Build.VERSION.SDK_INT < 33 || granted(c, Manifest.permission.POST_NOTIFICATIONS)

    fun requestList(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION, Manifest.permission.POST_NOTIFICATIONS,
            )
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

/** Lists the photos/videos the user chose to back up, via MediaStore (no raw file paths needed). */
object MediaSource {
    fun list(ctx: Context, p: Prefs): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        if (p.includePhotos) query(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, p, out)
        if (p.includeVideos) query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, p, out)
        out.sortByDescending { it.modified } // newest first
        return out
    }

    /** Every folder name that holds photos or videos, for the folder picker. */
    fun buckets(ctx: Context): Set<String> {
        val names = sortedSetOf<String>()
        for (collection in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) {
            ctx.contentResolver.query(collection, arrayOf(MC.BUCKET_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { if (it.isNotEmpty()) names.add(it) }
            }
        }
        return names
    }

    private fun query(ctx: Context, collection: Uri, p: Prefs, out: MutableList<MediaItem>) {
        val projection = arrayOf(MC._ID, MC.DISPLAY_NAME, MC.SIZE, MC.MIME_TYPE, MC.DATE_MODIFIED, MC.BUCKET_DISPLAY_NAME)
        val sel = StringBuilder("${MC.SIZE} > 0")
        val args = ArrayList<String>()
        if (p.buckets.isNotEmpty()) {
            sel.append(" AND ${MC.BUCKET_DISPLAY_NAME} IN (${p.buckets.joinToString(",") { "?" }})")
            args.addAll(p.buckets)
        }
        if (!p.includeExisting) {
            sel.append(" AND ${MC.DATE_ADDED} >= ?")
            args.add((p.enabledSince / 1000).toString())
        }
        ctx.contentResolver.query(collection, projection, sel.toString(), args.toTypedArray(), null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MC._ID)
            val iName = c.getColumnIndexOrThrow(MC.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MC.SIZE)
            val iMime = c.getColumnIndexOrThrow(MC.MIME_TYPE)
            val iMod = c.getColumnIndexOrThrow(MC.DATE_MODIFIED)
            val iBucket = c.getColumnIndexOrThrow(MC.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                out.add(
                    MediaItem(
                        uri = ContentUris.withAppendedId(collection, c.getLong(iId)),
                        name = c.getString(iName) ?: "file-${c.getLong(iId)}",
                        size = c.getLong(iSize),
                        mime = c.getString(iMime) ?: "application/octet-stream",
                        modified = c.getLong(iMod),
                        bucket = c.getString(iBucket) ?: "",
                    )
                )
            }
        }
    }
}
