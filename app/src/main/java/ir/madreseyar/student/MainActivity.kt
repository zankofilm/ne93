package ir.madreseyar.student

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm by viewModels<StudentViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReporter.install(applicationContext)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val crashReport = remember { mutableStateOf(CrashReporter.read(this@MainActivity)) }
            if (crashReport.value.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MadreseyarTheme {
                        CrashReportScreen(crashReport.value) {
                            CrashReporter.clear(this@MainActivity)
                            crashReport.value = ""
                        }
                    }
                }
                return@setContent
            }
            val state by vm.state.collectAsStateWithLifecycle()
            DisposableEffect(state.activeExam != null) {
                if (state.activeExam != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { if (state.activeExam != null) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MadreseyarTheme {
                    StudentRoot(vm) { url ->
                        val uri=Uri.parse(url)
                        if (uri.scheme == "https" || uri.scheme == "content") {
                            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                if(uri.scheme=="content") addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppForeground()
    }

    override fun onStop() {
        vm.onAppBackground()
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1301)
        }
    }
}
