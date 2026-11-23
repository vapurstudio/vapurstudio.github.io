package com.vapurstudio.hyperkit.ui

import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.vapurstudio.hyperkit.KitService
import com.vapurstudio.hyperkit.Prefs
import com.vapurstudio.hyperkit.R

class SoundActivity : BaseActivity() {
    override val screenTitle = R.string.mod_sound
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    override fun build() {
        if (!KitService.running) {
            val c = card()
            row(c, getString(R.string.service_off), getString(R.string.setup_body), R.drawable.ic_power) { openAccessibility() }
            button(c, getString(R.string.setup_open)) { openAccessibility() }
        }
        sectionTitle(R.string.sound_keys)
        val k = card()
        switchRow(k, getString(R.string.sound_media_default), getString(R.string.sound_media_default_desc),
            prefs.getBoolean(Prefs.MEDIA_KEYS, false)) { prefs.edit().putBoolean(Prefs.MEDIA_KEYS, it).apply() }
        switchRow(k, getString(R.string.sound_long_press), getString(R.string.sound_long_press_desc),
            prefs.getBoolean(Prefs.LONG_PRESS_SKIP, false)) { prefs.edit().putBoolean(Prefs.LONG_PRESS_SKIP, it).apply() }

        sectionTitle(R.string.sound_levels)
        val v = card()
        stream(v, R.string.stream_media, AudioManager.STREAM_MUSIC)
        stream(v, R.string.stream_ring, AudioManager.STREAM_RING)
        stream(v, R.string.stream_notification, AudioManager.STREAM_NOTIFICATION)
        stream(v, R.string.stream_alarm, AudioManager.STREAM_ALARM)
        stream(v, R.string.stream_call, AudioManager.STREAM_VOICE_CALL)
    }

    private fun stream(parent: android.view.ViewGroup, title: Int, type: Int) {
        val max = audio.getStreamMaxVolume(type)
        val min = audio.getStreamMinVolume(type)
        slider(parent, getString(title), min, max, audio.getStreamVolume(type),
            label = { "${(it * 100f / max).toInt()}%" }) {
            try {
                audio.setStreamVolume(type, it, 0)
            } catch (_: SecurityException) {
                toast(R.string.sound_dnd)
                tryStart(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
    }
}
