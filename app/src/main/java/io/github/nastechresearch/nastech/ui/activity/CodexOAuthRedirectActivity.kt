package io.github.nastechresearch.nastech.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.nastechresearch.nastech.RouteActivity

class CodexOAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(RouteActivity.EXTRA_OPEN_CODEX_SETTINGS, true)
            }
        )
        finish()
    }
}
