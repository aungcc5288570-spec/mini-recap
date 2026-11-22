package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

data class SavedMediaLocation(
    val uriString: String,
    val destinationLabel: String,
)

/**
 * Zero-permission exporter saving rendered MP4 videos to the device Gallery (MediaStore Movies/MiniRecap)
 * or a user-selected SAF Uri while emitting live byte-level progress updates.
 */
class DeviceMediaSaver(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun saveVideoToGallery(
        sourceFile: File,
        fileName: String,
        onProgress: (Float, Long, Long) -> Unit,
    ): SavedMediaLocation = withContext(ioDispatcher) {
        require(sourceFile.exists() && sourceFile.length() > 0L) {
            "Rendered video file is missing or empty"
        }
        val cleanName = fileName.ifBlank { "mini_recap_${System.currentTimeMillis()}.mp4" }
        val totalBytes = sourceFile.length()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relativeDir = "${Environment.DIRECTORY_MOVIES}/MiniRecap"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, relativeDir)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(collection, values)
                ?: error("Could not create MediaStore entry in Gallery")

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        copyWithProgress(input, out, totalBytes, onProgress)
                    }
                    out.flush()
                } ?: error("Gallery OutputStream was null")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
                SavedMediaLocation(
                    uriString = itemUri.toString(),
                    destinationLabel = "Gallery • $relativeDir/$cleanName",
                )
            } catch (t: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw t
            }
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
                "MiniRecap",
            ).apply { mkdirs() }
            val outFile = File(dir, cleanName)
            outFile.outputStream().use { out ->
                sourceFile.inputStream().use { input ->
                    copyWithProgress(input, out, totalBytes, onProgress)
                }
                out.flush()
            }
            SavedMediaLocation(
                uriString = Uri.fromFile(outFile).toString(),
                destinationLabel = "Movies/MiniRecap/$cleanName",
            )
        }
    }

    suspend fun saveVideoToUri(
        sourceFile: File,
        targetUri: Uri,
        onProgress: (Float, Long, Long) -> Unit,
    ): SavedMediaLocation = withContext(ioDispatcher) {
        require(sourceFile.exists() && sourceFile.length() > 0L) {
            "Rendered video file is missing or empty"
        }
        val totalBytes = sourceFile.length()
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            sourceFile.inputStream().use { input ->
                copyWithProgress(input, out, totalBytes, onProgress)
            }
            out.flush()
        } ?: error("OutputStream was null")

        val tailName = targetUri.lastPathSegment?.substringAfterLast('/') ?: "mini_recap.mp4"
        SavedMediaLocation(
            uriString = targetUri.toString(),
            destinationLabel = "Device Storage • $tailName",
        )
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (Float, Long, Long) -> Unit,
    ) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        var lastReportBytes = -1L
        onProgress(0f, 0L, totalBytes)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            copied += read
            if (copied - lastReportBytes >= 64 * 1024 || copied >= totalBytes) {
                val fraction = if (totalBytes > 0L) (copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 1f
                onProgress(fraction, copied, totalBytes)
                lastReportBytes = copied
            }
        }
        onProgress(1f, copied, totalBytes)
    }

    companion object {
        fun formatBytesMb(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.2f MB", mb)
        }
    }
}
