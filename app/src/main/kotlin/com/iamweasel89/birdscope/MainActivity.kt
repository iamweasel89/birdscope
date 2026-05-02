package com.iamweasel89.birdscope

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.iamweasel89.birdscope.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var updater: Updater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pi = packageManager.getPackageInfo(packageName, 0)
        binding.buildInfo.text = "Build " + pi.longVersionCode + " - " + pi.versionName

        updater = Updater(
            ctx = this,
            onStatus = { status -> runOnUiThread { binding.updateStatus.text = status } },
            onUpdateAvailable = { latest -> runOnUiThread { confirmUpdate(latest) } }
        )

        binding.checkUpdateButton.setOnClickListener { updater.check() }
    }

    private fun confirmUpdate(latestBuild: Long) {
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("Build " + latestBuild + " is available. Download and install?")
            .setPositiveButton("Download") { _, _ -> updater.confirmDownload() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
