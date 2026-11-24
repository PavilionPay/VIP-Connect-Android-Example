package com.pavilionpay.vipconnect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A simple Activity that shows a Landing view and launches the VIP Connect SDK in an Auth Tab.
 *
 * An Auth Tab is a browser tab made for sign-in style flows: the app starts it with a url and a redirect scheme, and
 * the browser hands the result back to the app when the page navigates to that scheme. It is the Android counterpart
 * of iOS's ASWebAuthenticationSession.
 *
 * This app uses Compose to create its UI, but Compose is not a requirement for the VIP SDK.
 */
class MainActivity : ComponentActivity() {

    /** Shown on the Landing view after a session ends. */
    private var sessionStatus by mutableStateOf<String?>(null)

    // Receives the result of the Auth Tab. Must be registered before the Activity is started.
    private val authTabLauncher: ActivityResultLauncher<Intent> =
        AuthTabIntent.registerActivityResultLauncher(this) { result ->
            sessionStatus = when (result.resultCode) {
                // The SDK navigated to the return url: the session is complete. result.resultUri holds the full url,
                // including the transactionId and transactionType query parameters that VIP Connect appends.
                AuthTabIntent.RESULT_OK -> "VIP Connect session complete."
                // The patron closed the tab. The transaction may still have completed if they closed it at the end.
                AuthTabIntent.RESULT_CANCELED -> "VIP Connect session closed."
                else -> "VIP Connect session ended unexpectedly (result code ${result.resultCode})."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleReturnUrl(intent)

        val viewModel: VIPSessionUrlViewModel by viewModels()
        setContent {
            Scaffold { innerPadding ->
                Surface(
                    modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Landing screen view
                    // When the Launch button is pressed, the VIPSessionUrlViewModel attempts to retrieve a valid session id,
                    // and if successful will build the VIP SDK url for that session and launch it.
                    LandingView(viewModel, sessionStatus) { sessionUrl ->
                        sessionStatus = null
                        launchVipConnect(sessionUrl)
                    }
                }
            }
        }
    }

    /**
     * Opens the VIP Connect SDK url in an Auth Tab.
     */
    private fun launchVipConnect(sessionUrl: String) {
        val builder = AuthTabIntent.Builder()

        // Run the session in private (incognito) mode when the patron's browser supports it. An ephemeral tab shares no
        // cookies or website data with the browser or with earlier sessions, so every patron starts from a clean
        // browser and nothing from VIP Connect or the bank sign-in is left behind when the session ends.
        // Browsers that do not support it open a normal tab instead.
        val browserPackage = CustomTabsClient.getPackageName(this, emptyList())
        if (browserPackage != null && CustomTabsClient.isEphemeralBrowsingSupported(this, browserPackage)) {
            builder.setEphemeralBrowsingEnabled(true)
        }

        // The redirect scheme must match the scheme of the return url supplied when the session was created.
        // If the patron's browser does not support Auth Tab, this opens a Custom Tab instead, and the return url
        // comes back through the intent filter in AndroidManifest.xml (see handleReturnUrl).
        builder.build().launch(authTabLauncher, Uri.parse(sessionUrl), VIPSessionUrlViewModel.RETURN_SCHEME)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReturnUrl(intent)
    }

    /**
     * Handles the return url when it arrives as an intent. This only happens when the browser opened a Custom Tab
     * because it does not support Auth Tab. MainActivity is declared singleTask, so the intent closes the Custom Tab
     * and comes back to this Activity.
     */
    private fun handleReturnUrl(intent: Intent?) {
        if (intent?.data?.scheme == VIPSessionUrlViewModel.RETURN_SCHEME) {
            sessionStatus = "VIP Connect session complete."
        }
    }
}
