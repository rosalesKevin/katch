package com.katch.sample

import android.app.Application
import com.katch.Katch

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // This sample demonstrates the passphrase-based setup; Auto is the alternative if you
        // want Katch to manage key generation and persistence for the app.
        //
        // To save crash reports to a custom directory instead of the default
        // getExternalFilesDir("crash_logs"), call outputDir() before or after init():
        //   Katch.outputDir(File(getExternalFilesDir(null), "my_crashes"))
        Katch.init(this, "katch-sample-passphrase")
    }
}
