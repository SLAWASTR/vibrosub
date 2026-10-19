package com.vibrosub.player.audio

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

data class DeviceAudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val albumArtUri: Uri? = null,
    val isDemo: Boolean = false
)

object DeviceAudioScanner {
    private const val TAG = "DeviceAudioScanner"

    fun scanDeviceAudio(context: Context): List<DeviceAudioItem> {
        val tracks = mutableListOf<DeviceAudioItem>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Без названия"
                    val artist = c.getString(artistCol)?.let { if (it == "<unknown>") "Неизвестный исполнитель" else it } ?: "Неизвестный исполнитель"
                    val album = c.getString(albumCol) ?: "Неизвестный альбом"
                    val durationMs = c.getLong(durationCol)
                    val albumId = c.getLong(albumIdCol)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

                    tracks.add(
                        DeviceAudioItem(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            uri = contentUri,
                            albumArtUri = artUri,
                            isDemo = false
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Audio permission not yet granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning device audio: ${e.message}")
        }

        return tracks
    }
}
