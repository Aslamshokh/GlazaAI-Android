package com.aslamshoh.glazaai.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Играет тон, высота которого следует за яркостью кадра. Останавливается сам через [maxSeconds]. */
class LightTonePlayer {
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    fun start(maxSeconds: Int = 40, lumaProvider: () -> Double?) {
        stop()
        running = true
        thread = Thread {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    LightTone.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(LightTone.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                val deadline = System.currentTimeMillis() + maxSeconds * 1000L
                var hz = LightTone.MIN_HZ
                while (running && System.currentTimeMillis() < deadline) {
                    val luma = lumaProvider()
                    if (luma != null) {
                        // сглаживаем, чтобы тон не дёргался от шума кадра
                        hz = hz * 0.5 + LightTone.frequency(luma) * 0.5
                    }
                    val chunk = LightTone.samples(hz, 220)
                    track.write(chunk, 0, chunk.size)
                }
            } catch (_: Exception) {
                // нет звука — не страшно
            } finally {
                running = false
                try { track?.stop() } catch (_: Exception) {}
                try { track?.release() } catch (_: Exception) {}
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        thread = null
    }
}
