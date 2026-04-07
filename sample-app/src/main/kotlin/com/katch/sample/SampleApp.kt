package com.katch.sample

import android.app.Application
import com.katch.Katch

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Katch once from the Application so every activity shares the same logger.
        Katch.init(this, Katch.EncryptionKey.Auto)
    }
}
