package me.phh.treble.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioSystem
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ForceHeadsetAudio(val audioManager: AudioManager) : EntryStartup, BroadcastReceiver() {
    private val lock = ReentrantLock(true)
    // private var mode: Int = AudioManager.MODE_INVALID // УДАЛЕНО

    fun speaker() {
        lock.withLock {
            // mode = audioManager.mode // УДАЛЕНО
            // --- Начало манипуляций ---
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            // Again. Who knows why, but otherwise it just stays in in-call speakerphone
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION // Оставляем, может быть нужно
            audioManager.mode = AudioManager.MODE_NORMAL
            // --- Конец манипуляций ---
            // audioManager.mode = mode // УДАЛЕНО
        }
    }

    fun headset() {
        lock.withLock {
            // mode = audioManager.mode // УДАЛЕНО
            // --- Начало манипуляций ---
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false // Оставляем, может быть нужно
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false // Оставляем, может быть нужно
            // --- Конец манипуляций ---
            // audioManager.mode = mode // УДАЛЕНО
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_HEADSET_PLUG)
            return
        val state = intent.getIntExtra("state", -1)
        when (state) {
            AudioSystem.DEVICE_STATE_UNAVAILABLE -> speaker()
            AudioSystem.DEVICE_STATE_AVAILABLE -> headset()
            else ->
                Log.e("PlugReceiver", "Unrecognised headset plug state!", Throwable())
        }
        // Apply the changes by setting the volume to the current volume
        // Fails in DND but to exit that you must change the volume later so meh.
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Добавим проверку, чтобы избежать потенциального крэша, если volume == -1 или что-то такое
        if (volume >= 0) {
             try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
             } catch (e: Exception) {
                 Log.w(tag, "Failed to set stream volume in onReceive", e)
             }
        } else {
             Log.w(tag, "Invalid volume $volume obtained in onReceive")
        }
    }

    override fun startup(ctxt: Context) {
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        // val plugReceiver = this // Не нужно, т.к. this уже BroadcastReceiver
        try {
            // Используйте ContextCompat для регистрации, если поддерживаете старые версии Android
            // ContextCompat.registerReceiver(ctxt, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED) // Пример для >= API 33
            // или просто:
             ctxt.registerReceiver(this, filter) // Убедитесь, что это безопасно с точки зрения экспорта ресивера
             Log.d(tag, "Registered for headset plug")
        } catch (e: Exception) {
            Log.e(tag, "Failed to register headset plug receiver", e)
        }
    }

    fun shutdown(ctxt: Context) {
        try {
            ctxt.unregisterReceiver(this)
            Log.d(tag, "Unregistered for headset plug")
        } catch (e: IllegalArgumentException) {
            // Игнорируем ошибку, если ресивер уже не зарегистрирован
            Log.w(tag, "Receiver not registered or already unregistered.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to unregister headset plug receiver", e)
        }
    }

    companion object : EntryStartup {
        const val tag = "ForceHeadsetAudio"
        private var self: ForceHeadsetAudio? = null

        override fun startup(ctxt: Context) {
            if (self == null) { // Предотвращаем повторную инициализацию
                val audioManager = ctxt.getSystemService(AudioManager::class.java)
                if (audioManager != null) {
                    self = ForceHeadsetAudio(audioManager)
                    self!!.startup(ctxt) // Вызываем startup экземпляра
                } else {
                    Log.e(tag, "AudioManager is null, cannot start ForceHeadsetAudio.")
                }
            } else {
                 Log.w(tag, "ForceHeadsetAudio already started.")
            }
        }

        fun shutdown(ctxt: Context) {
            // Return to normal state if we were initialized earlier
            // self?.speaker() // Теперь speaker() просто оставит MODE_NORMAL, что может быть нормально.
                           // Можно оставить, чтобы убедиться, что динамик включен при выключении.
            self?.shutdown(ctxt) // Вызываем shutdown экземпляра
            self = null // Очищаем ссылку
        }
    }
}
