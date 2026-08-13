package io.github.nastechresearch.nastech.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import io.github.nastechresearch.nastech.utils.InstallRequestResult
import io.github.nastechresearch.nastech.utils.UpdateInstaller

/** Opens the system package installer only after an explicit user tap on Nastech's update notice. */
class UpdateInstallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (UpdateInstaller.installDownloaded(this, intent.getLongExtra(UpdateInstaller.EXTRA_DOWNLOAD_ID, -1L))) {
            InstallRequestResult.INSTALLER_OPENED -> Unit
            InstallRequestResult.SOURCE_PERMISSION_REQUIRED -> {
                Toast.makeText(this, "Allow Nastech to install updates, then tap Install again.", Toast.LENGTH_LONG).show()
            }
            InstallRequestResult.NOT_READY -> {
                Toast.makeText(this, "The update file is not ready yet.", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
