package com.vibevault.app.player.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import com.vibevault.app.R
import java.io.File
import java.io.FileOutputStream

/**
 * ContentProvider serving square, high-resolution artwork and transparent tab icons to Android Auto's process (Gearhead).
 * Handles missing files gracefully by generating/returning fallback square artwork, never throwing uncaught.
 */
class AutoArtworkContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY_SUFFIX = ".artwork"

        fun getArtworkUriString(packageName: String, name: String = "default.jpg"): String {
            return "content://$packageName$AUTHORITY_SUFFIX/$name"
        }

        fun getArtworkUri(packageName: String, name: String = "default.jpg"): Uri? {
            return try {
                Uri.parse(getArtworkUriString(packageName, name))
            } catch (e: Throwable) {
                null
            }
        }

        fun getTabIconUriString(tabName: String): String {
            val name = if (tabName.endsWith(".png")) tabName else "$tabName.png"
            return "content://com.vibevault.app$AUTHORITY_SUFFIX/$name"
        }

        fun getTabIconUri(tabName: String): Uri? {
            return try {
                Uri.parse(getTabIconUriString(tabName))
            } catch (e: Throwable) {
                null
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        return try {
            val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "default.jpg"
            val artworkDir = File(ctx.cacheDir, "auto_artwork").apply { mkdirs() }
            val file = File(artworkDir, fileName)

            if (!file.exists() || file.length() == 0L) {
                when (fileName) {
                    "tab_home.png" -> renderDrawableToPng(ctx, R.drawable.ic_auto_tab_home, file)
                    "tab_search.png", "tab_browse.png" -> renderDrawableToPng(ctx, R.drawable.ic_auto_tab_search, file)
                    "tab_library.png" -> renderDrawableToPng(ctx, R.drawable.ic_auto_tab_library, file)
                    "tab_recents.png" -> renderDrawableToPng(ctx, R.drawable.ic_auto_tab_recents, file)
                    else -> {
                        // Ensure a square (>= 300x300) bitmap is generated for Android Auto album/playlist art
                        val bitmap = try {
                            BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_app_icon_hi_fi)
                        } catch (e: Throwable) {
                            null
                        } ?: Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)

                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    }
                }
            }

            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Throwable) {
            Log.e("AutoArtworkProvider", "Error opening artwork for $uri", e)
            null
        }
    }

    private fun renderDrawableToPng(context: Context, resId: Int, outputFile: File): Boolean {
        return try {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return false
            val size = 192 // 192x192 transparent PNG for crisp Android Auto tab icons
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            true
        } catch (e: Throwable) {
            Log.e("AutoArtworkProvider", "Failed to render drawable $resId to PNG", e)
            false
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String {
        val segment = uri.lastPathSegment.orEmpty()
        return if (segment.endsWith(".png", ignoreCase = true)) {
            "image/png"
        } else {
            "image/jpeg"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
