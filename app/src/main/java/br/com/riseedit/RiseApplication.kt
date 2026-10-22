package br.com.riseedit

import android.app.Application
import br.com.riseedit.diagnostics.Diagnostics

class RiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
        br.com.riseedit.media.FrameCompat.load(this)
        br.com.riseedit.settings.SettingsStore.install(this)
        br.com.riseedit.color.GradeLuts.install(this)
    }
}
