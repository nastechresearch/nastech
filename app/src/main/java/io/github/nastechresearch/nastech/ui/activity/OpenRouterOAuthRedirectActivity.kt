package io.github.nastechresearch.nastech.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.nastechresearch.nastech.RouteActivity
import io.github.nastechresearch.nastech.data.openrouter.OpenRouterOAuthManager
import org.koin.android.ext.android.inject

class OpenRouterOAuthRedirectActivity : ComponentActivity() {
    private val oauthManager by inject<OpenRouterOAuthManager>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthManager.handleRedirect(intent?.data?.getQueryParameter("code"))
        startActivity(
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(RouteActivity.EXTRA_OPEN_OPENROUTER_SETTINGS, true)
            },
        )
        finish()
    }
}
