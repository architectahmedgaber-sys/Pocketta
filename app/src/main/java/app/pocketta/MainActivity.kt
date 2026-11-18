package app.pocketta

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.pocketta.ui.LocalDark
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.pocketta.ui.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.pocketta.data.Prefs
import app.pocketta.data.Repo
import app.pocketta.data.TxType
import app.pocketta.notify.Notifier
import app.pocketta.ui.AppRoot
import app.pocketta.ui.Nav
import app.pocketta.ui.OnboardingScreen
import app.pocketta.ui.PockettaTheme
import app.pocketta.ui.Route

class MainActivity : FragmentActivity() {
    val nav = Nav()
    private var unlocked by mutableStateOf(false)
    private var promptShowing = false
    /** Mirrors the lock setting so onResume can ask for the fingerprint. */
    private var lockOn = false

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val repo = Repo.get(this)
        setContent {
            val prefsFlow = remember { repo.prefsFlow() }
            val prefs by prefsFlow.collectAsState(initial = null as Prefs?)
            val p = prefs
            PockettaTheme(mode = p?.theme ?: "AUTO", lang = p?.lang ?: "EN") {
                val dark = LocalDark.current
                SideEffect {
                    // Status and navigation bar icons follow the theme picked in Settings, not only the phone's.
                    val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    lockOn = p?.lock == true && p.onboarded
                    when {
                        p == null -> Box(Modifier.fillMaxSize())
                        !p.onboarded -> OnboardingScreen(repo) { askForNotifications() }
                        p.lock && !unlocked -> {
                            LaunchedEffect(Unit) { unlock() }
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Pocketta is locked", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = { promptShowing = false; unlock() }) { Text("Unlock") }
                            }
                        }
                        else -> {
                            BackHandler(enabled = nav.canGoBack) { nav.back() }
                            AppRoot(repo, nav, p, authenticate = { title, ok -> authenticate(title, ok) })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Ask for the fingerprint every time the app comes back while locked.
        if (lockOn && !unlocked) window.decorView.post { if (!unlocked) unlock() }
        // Look for a new version every time the app comes to the front (at most every 10 minutes), not only when the process starts.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { app.pocketta.data.Online.checkUpdate(app.pocketta.data.Repo.get(applicationContext)) }
        }
    }

    override fun onStop() {
        super.onStop()
        // Lock again when the app goes to the background (not when a picker or the fingerprint prompt opens on top).
        if (!isChangingConfigurations && !promptShowing) unlocked = false
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD -> nav.open(Route.Add(type = TxType.EXPENSE, category = intent?.getLongExtra(EXTRA_CATEGORY, 0L) ?: 0L))
            ACTION_ADD_INCOME -> nav.open(Route.Add(type = TxType.INCOME))
            ACTION_BILLS -> nav.tab(Route.Commit)
            ACTION_SETTINGS, ACTION_UPDATE -> { nav.tab(Route.Home); nav.open(Route.Settings) }
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun unlock() = authenticate("Unlock Pocketta") { unlocked = true }

    /** Fingerprint, face or the screen lock. If the phone has no lock set, it just continues. */
    fun authenticate(title: String, onSuccess: () -> Unit) {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }
        if (promptShowing) return
        if (lifecycle.currentState < androidx.lifecycle.Lifecycle.State.RESUMED) return
        promptShowing = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptShowing = false
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptShowing = false
            }

            override fun onAuthenticationFailed() {
                // A wrong finger: the prompt stays open and lets the user try again.
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(auth)
                .build()
        )
    }

    companion object {
        const val ACTION_ADD = "app.pocketta.ADD"
        const val ACTION_ADD_INCOME = "app.pocketta.ADD_INCOME"
        const val ACTION_BILLS = "app.pocketta.BILLS"
        const val ACTION_SETTINGS = "app.pocketta.SETTINGS"
        const val ACTION_UPDATE = "app.pocketta.UPDATE"
        const val EXTRA_CATEGORY = "app.pocketta.CATEGORY"
    }
}
