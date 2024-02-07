/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2024 Matthias Emde
 */

package app.musikus.repository

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import app.musikus.di.IoScope
import app.musikus.usecase.recordings.Recording
import app.musikus.usecase.recordings.RecordingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

class RecordingsRepositoryImpl(
    private val application: Application,
    private val contentResolver: ContentResolver,
    @IoScope private val ioScope: CoroutineScope
) : RecordingsRepository {

    private val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    override val recordings: Flow<List<Recording>>
        get() = subscribeTo { getRecordings() }


    // source: https://stackoverflow.com/questions/55212362/how-to-decode-an-m4a-audio-on-android
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getRawRecording(contentUri: Uri): Result<FloatArray> {

        val mediaExtractor = MediaExtractor()

        Log.d("RecordingsRepository", "getRawRecording: $contentUri")

        val recordingBuffer = ArrayList<Float>()
        var mediaFormat : MediaFormat? = null

        mediaExtractor.setDataSource(application, contentUri, null)

        repeat(mediaExtractor.trackCount) { trackIndex ->
            mediaFormat = mediaExtractor.getTrackFormat(trackIndex)
            if(mediaFormat?.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                mediaExtractor.selectTrack(trackIndex)
                return@repeat
            }
        }

        mediaFormat?.let { nonNullMediaFormat ->
            // we get the mime type from the mediaFormat
            val mimeType = nonNullMediaFormat.getString(MediaFormat.KEY_MIME) ?: return Result.failure(Exception("No mime type found"))

            Log.d("RecordingsRepository", "Mime type: $mimeType")

            val decoder = MediaCodec.createDecoderByType(mimeType)

            return withContext(ioScope.coroutineContext) {
                suspendCancellableCoroutine { coroutine ->

                    coroutine.invokeOnCancellation {
                        decoder.release()
                        mediaExtractor.release()
                    }

                    decoder.setCallback(object : MediaCodec.Callback() {
                        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                            val inputBuffer = codec.getInputBuffer(index) ?: return
                            val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)
                            if(sampleSize < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                codec.queueInputBuffer(index, 0, sampleSize, mediaExtractor.sampleTime, 0)
                                mediaExtractor.advance()
                            }
                        }

                        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                            val outputBuffer = codec.getOutputBuffer(index) ?: return

                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                Log.d("RecordingsRepository", "End of stream reached")
                                coroutine.resume(
                                    value = Result.success(recordingBuffer.toFloatArray()),
                                    onCancellation = null
                                )
                                return
                            }

                            for (i in 0 until info.size step 2) {
                                recordingBuffer.add (outputBuffer.getShort(i).toFloat())
                            }

                            outputBuffer.clear()
                            codec.releaseOutputBuffer(index, false)
                        }

                        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                            Log.e("RecordingsRepository", "Error in mediacodec callback: $e")
                            coroutine.resume(Result.failure(e), onCancellation = null)
                        }

                        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                            Log.d("RecordingsRepository", "Output format changed: $format")
                        }
                    })

                    /**
                     *  ------------------- Configure and start the decoder -----------------------
                     */
                    decoder.configure(nonNullMediaFormat, null, null, 0)
                    decoder.start()
                }
            }

        } ?: return Result.failure(Exception("No audio track found"))
    }

    private fun <T> subscribeTo(query: () -> T): Flow<T> {
        val notify = MutableSharedFlow<String>()

        var observer: ContentObserver? = null

        return flow {
            observer = observer ?: (object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    ioScope.launch {
                        notify.emit("changed")
                    }
                }
            }).also {
                contentResolver.registerContentObserver(collection, true, it)
            }

            // emit the initial value
            emit(query())

            notify.collect {
                emit(query())
            }
        }.onCompletion {
            observer?.let {
                contentResolver.unregisterContentObserver(it)
            }
        }
    }

    private fun getRecordings() : List<Recording> {
        val recordingList = mutableListOf<Recording>()

        contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
            ),
            "${MediaStore.Audio.Media.ALBUM} = 'Musikus'",
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while(cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn)
                val duration = cursor.getInt(durationColumn)
                val date = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                recordingList.add(
                    Recording(
                        mediaItem = MediaItem.fromUri(contentUri),
                        title = displayName,
                        duration = duration.milliseconds,
                        date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(date), ZoneId.systemDefault()),
                        contentUri = contentUri
                    )
                )
            }
        }

        return recordingList
    }

}