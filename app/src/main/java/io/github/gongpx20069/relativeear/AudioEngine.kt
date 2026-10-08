package io.github.gongpx20069.relativeear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import io.github.gongpx20069.relativeear.core.ToneSynthesis
import io.github.gongpx20069.relativeear.core.ToneVoice
import io.github.gongpx20069.relativeear.core.MelodyClip
import io.github.gongpx20069.relativeear.core.PitchDetector
import io.github.gongpx20069.relativeear.core.PitchFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioFailure(message: String, cause: Throwable? = null, val localized: UiMessage? = null) : Exception(message, cause) {
    constructor(resource: Int, vararg arguments: Any, cause: Throwable? = null) :
        this("Audio failure: $resource", cause, UiMessage(resource, arguments.toList()))
}

interface AudioSession {
    suspend fun play(chords: List<List<Int>>, a4: Double, soundMs: Int = 450, gapMs: Long = 100,
        voice: ToneVoice = ToneVoice.PURE, onChord: (Int) -> Unit = {})
    suspend fun playMelody(clip: MelodyClip, onPosition: (Long) -> Unit)
    suspend fun capture(limitMs: Long? = null, onFrame: (PitchFrame) -> Boolean)
}

class AudioEngine(context: Context, private val onInterrupted: () -> Unit) : AudioSession {
    private val context = context.applicationContext
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private fun watchRoute(): AudioDeviceCallback {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.isNotEmpty()) onInterrupted()
            }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        return callback
    }

    override suspend fun play(
        chords: List<List<Int>>, a4: Double, soundMs: Int, gapMs: Long,
        voice: ToneVoice, onChord: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(soundMs > 0 && gapMs >= 0 && chords.isNotEmpty() && chords.all { it.isNotEmpty() })
        val samples = ToneSynthesis.render(chords, a4, soundMs, gapMs, voice)
        val slot = ToneSynthesis.samplesPerTone(soundMs, gapMs)
        var announced = -1
        playSamples(samples) { head ->
            val index = (head / slot).toInt().coerceAtMost(chords.lastIndex)
            if (index != announced) { onChord(index); announced = index }
        }
    }

    override suspend fun playMelody(clip: MelodyClip, onPosition: (Long) -> Unit) = withContext(Dispatchers.IO) {
        require(clip.notes.isNotEmpty())
        playSamples(ToneSynthesis.render(clip)) { head -> onPosition(head * 1000 / ToneSynthesis.SAMPLE_RATE) }
    }

    private suspend fun playSamples(samples: ShortArray, onHead: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change < 0) onInterrupted()
            }.build()
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw AudioFailure(R.string.audio_focus_denied)
        }
        val route = watchRoute()
        try {
            val rate = ToneSynthesis.SAMPLE_RATE
            val format = AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) throw AudioFailure(R.string.audio_play_format)
            val track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minimum, 4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
            try {
                if (track.state != AudioTrack.STATE_INITIALIZED) throw AudioFailure(R.string.audio_play_init)
                track.play()
                val observer = launch {
                    while (true) {
                        val head = track.playbackHeadPosition.toLong().coerceAtMost(samples.size.toLong())
                        onHead(head)
                        if (head >= samples.size) break
                        delay(10)
                    }
                }
                try {
                    var offset = 0
                    while (offset < samples.size) {
                        currentCoroutineContext().ensureActive()
                        val written = track.write(samples, offset, minOf(320, samples.size - offset), AudioTrack.WRITE_BLOCKING)
                        if (written <= 0) throw AudioFailure(R.string.audio_play_write, written)
                        offset += written
                    }
                    // Silence is part of the PCM timeline, so gaps do not accumulate coroutine timing drift.
                    var waitMs = 0
                    while (track.playbackHeadPosition.toLong() < samples.size && waitMs < 1000) {
                        delay(10)
                        waitMs += 10
                    }
                    if (track.playbackHeadPosition.toLong() < samples.size) throw AudioFailure(R.string.audio_play_incomplete)
                    observer.join()
                } finally {
                    withContext(NonCancellable) { observer.cancelAndJoin() }
                }
            } finally {
                track.release()
            }
        } catch (error: IllegalStateException) {
            throw AudioFailure(R.string.audio_play_state, cause = error)
        } catch (error: IllegalArgumentException) {
            throw AudioFailure(R.string.audio_play_config, cause = error)
        } finally {
            manager.unregisterAudioDeviceCallback(route)
            manager.abandonAudioFocusRequest(focus)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun capture(limitMs: Long?, onFrame: (PitchFrame) -> Boolean) = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission not granted")
        }
        val rate = 16_000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) throw AudioFailure(R.string.audio_record_format)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192),
            )
        } catch (error: IllegalArgumentException) {
            throw AudioFailure(R.string.audio_record_config, cause = error)
        }
        val route = watchRoute()
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) throw AudioFailure(R.string.audio_record_init)
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw AudioFailure(R.string.audio_record_start)
            val detector = PitchDetector(rate, 2048)
            val block = ShortArray(512)
            val window = FloatArray(2048)
            val start = SystemClock.elapsedRealtime()
            var filled = 0
            var total = 0L
            var lastFrameTime = -1L
            while (limitMs == null || SystemClock.elapsedRealtime() - start < limitMs) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) throw AudioFailure(R.string.audio_record_read, read)
                window.copyInto(window, 0, read, window.size)
                for (index in 0 until read) window[window.size - read + index] = block[index] / 32768f
                filled = minOf(window.size, filled + read)
                total += read
                val frameTime = total * 1000 / rate
                if (filled == window.size && frameTime > lastFrameTime) {
                    lastFrameTime = frameTime
                    val frame = detector.detect(window, frameTime)
                    if (!onFrame(frame)) break
                }
            }
        } catch (error: IllegalStateException) {
            throw AudioFailure(R.string.audio_record_interrupted, cause = error)
        } finally {
            manager.unregisterAudioDeviceCallback(route)
            recorder.release()
        }
    }
}
