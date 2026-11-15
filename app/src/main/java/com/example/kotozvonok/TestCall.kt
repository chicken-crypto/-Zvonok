package com.example.kotozvonok

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

// Имитация разговора для тестового звонка: 5 секунд белого шума, затем автозавершение.
object TestCall {
    private var track: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    fun playWhiteNoiseThenEnd(durationMs: Long = 5000, onEnd: () -> Unit) {
        stop()
        try {
            val sampleRate = 44100
            val buf = ShortArray(sampleRate) { ((Math.random() * 2 - 1) * Short.MAX_VALUE * 0.15).toInt().toShort() }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buf.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(buf, 0, buf.size)
            t.setLoopPoints(0, buf.size, -1)
            t.play()
            track = t
        } catch (e: Exception) { /* на некоторых устройствах может не сработать — это лишь тестовая имитация */ }

        handler.postDelayed({
            stop()
            onEnd()
        }, durationMs)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        try { track?.stop(); track?.release() } catch (e: Exception) { }
        track = null
    }
}
