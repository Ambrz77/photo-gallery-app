package ir.sharif.android.photogallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data class to hold media item details

sealed class MediaItem(
    val uri: Uri,
    val displayName: String,
    val relativePath: String?,
    val date: Long,
    val mimeType: String,
)

class ImageItem(
    uri: Uri,
    displayName: String,
    relativePath: String?,
    date: Long,
    mimeType: String,
) : MediaItem(
    uri = uri,
    displayName = displayName,
    relativePath = relativePath,
    date = date,
    mimeType = mimeType,
)

class VideoItem(
    uri: Uri,
    displayName: String,
    relativePath: String?,
    date: Long,
    mimeType: String,
    val duration: Long
) : MediaItem(
    uri = uri,
    displayName = displayName,
    relativePath = relativePath,
    date = date,
    mimeType = mimeType,
)

// Fetches both images and videos from the device using MediaStore
suspend fun fetchMediaItems(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
    val mediaItems = mutableListOf<MediaItem>()
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.RELATIVE_PATH, // Available on Android Q+; may be null on older devices
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DURATION,
        MediaStore.MediaColumns.MIME_TYPE
    )
    val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

    // Query Images
    val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.query(imageUri, projection, null, null, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val relativePath = cursor.getString(relativePathColumn)
            val date = cursor.getLong(dateColumn)
            val mimeType = cursor.getString(mimeColumn)
            // Create the content URI for the image
            val contentUri = ContentUris.withAppendedId(imageUri, id)
            mediaItems.add(
                ImageItem(
                    uri = contentUri,
                    displayName = name,
                    relativePath = relativePath,
                    date = date,
                    mimeType = mimeType,
                )
            )
        }
    }

    // Query Videos
    val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.query(videoUri, projection, null, null, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val relativePath = cursor.getString(relativePathColumn)
            val contentUri = ContentUris.withAppendedId(videoUri, id)
            val date = cursor.getLong(dateColumn)
            val mimeType = cursor.getString(mimeColumn)
            val duration = cursor.getLong(durationColumn)
            mediaItems.add(
                VideoItem(
                    uri = contentUri,
                    displayName = name,
                    relativePath = relativePath,
                    date = date,
                    mimeType = mimeType,
                    duration = duration
                )
            )
        }
    }
    mediaItems
}
