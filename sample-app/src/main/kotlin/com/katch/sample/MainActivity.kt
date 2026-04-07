package com.katch.sample

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.katch.Katch
import com.katch.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val exportedKey = Katch.exportKey()
        val keyHex = KeyExportFormatter.format(exportedKey)

        binding.encryptionStatusText.text = getString(
            if (keyHex != null) R.string.encryption_status_ready else R.string.encryption_status_unavailable
        )
        binding.keyValueText.text = keyHex ?: getString(R.string.key_unavailable)
        binding.decryptCommandText.text = buildDecryptCommand(keyHex)

        binding.copyKeyButton.setOnClickListener {
            copyKeyToClipboard(keyHex)
        }

        binding.testReportButton.setOnClickListener {
            // These entries seed the generated report so developers can inspect a realistic log trail.
            Katch.d("SampleApp", "Preparing a synthetic encrypted report from the sample app")
            Katch.i("SampleApp", "Sample report requested from the main screen")
            Katch.w("SampleApp", "Reports are only written when testCrash() or a real crash runs")
            Katch.e("SampleApp", "Generating encrypted sample crash report without killing the app")
            Katch.testCrash()
            showToast(R.string.test_report_generated)
        }

        binding.crashAppButton.setOnClickListener {
            // This intentionally exercises the real uncaught-exception path handled by Katch.
            Katch.e("SampleApp", "Crash App was tapped; throwing an uncaught exception for verification")
            throw RuntimeException("Sample app crash triggered for Katch verification")
        }
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }

    private fun copyKeyToClipboard(keyHex: String?) {
        if (keyHex == null) {
            showToast(R.string.key_copy_unavailable)
            return
        }

        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboardManager == null) {
            showToast(R.string.clipboard_unavailable)
            return
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.exported_key_label), keyHex))
        showToast(R.string.key_copied)
    }

    private fun buildDecryptCommand(keyHex: String?): String {
        val commandKey = keyHex ?: getString(R.string.decrypt_key_placeholder)
        return getString(R.string.decrypt_command_template, commandKey)
    }
}
