package com.arijit.pomodoro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arijit.pomodoro.fragments.TimerFragment
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import android.app.NotificationChannel
import android.app.NotificationManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.view.View
import com.airbnb.lottie.Lottie
import com.airbnb.lottie.LottieAnimationView
import androidx.core.view.isVisible
import androidx.core.content.edit
import java.io.File
import android.os.Environment
import com.arijit.pomodoro.fragments.LongBreakFragment
import com.arijit.pomodoro.fragments.ShortBreakFragment
import com.arijit.pomodoro.services.TimerService
import com.arijit.pomodoro.utils.UltraFocusManager
import android.content.SharedPreferences
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {
    private lateinit var settings_btn: ImageView
    private lateinit var frame_layout: FrameLayout
    private lateinit var sessionsTxt: TextView
    private lateinit var musicBtn: ImageView
    private lateinit var musicAnim: LottieAnimationView
    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPlaying = false
    private lateinit var sharedPreferences: SharedPreferences

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == SettingsActivity.RESULT_TIMER_SETTINGS_CHANGED) {
            // Only reset if timer settings were modified
            val sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
            if (sharedPreferences.getBoolean("wereTimerSettingsModified", false)) {
                // Always reset currentSession to 1
                sharedPreferences.edit().putInt("currentSession", 1).apply()
                // Check if timer is running
                val isTimerRunning = sharedPreferences.getBoolean("timerRunning", false)
                // Clear timeLeftInMillis to force reload from settings
                sharedPreferences.edit().putLong("timeLeftInMillis", 0).apply()
                if (!isTimerRunning) {
                    // Only reset if timer is not running
                    sharedPreferences.edit().apply {
                        putBoolean("wereTimerSettingsModified", false)
                        apply()
                    }
                    // Reload the timer fragment with new settings
                    showTimerFragment()
                } else {
                    // If timer is running, just update the settings without resetting
                    sharedPreferences.edit().putBoolean("wereTimerSettingsModified", false).apply()
                    // Restore the current fragment with its state, but force session to 1
                    val fragmentType = sharedPreferences.getString("currentFragment", "focus") ?: "focus"
                    val totalSessions = sharedPreferences.getInt("sessions", 4)
                    when (fragmentType) {
                        "focus" -> {
                            val fragment = TimerFragment.newInstance(1, totalSessions, false)
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.frame_layout, fragment)
                                .commit()
                        }
                        "shortBreak" -> {
                            val fragment = ShortBreakFragment()
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.frame_layout, fragment)
                                .commit()
                        }
                        "longBreak" -> {
                            val fragment = LongBreakFragment()
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.frame_layout, fragment)
                                .commit()
                        }
                    }
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            createNotificationChannel()
        } else {
            // If permission is denied, show settings dialog
            AlertDialog.Builder(this)
                .setTitle("Notification Permission Required")
                .setMessage("This app needs notification permission to alert you when timers complete. Please enable it in Settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    // Open app settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Not Now") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    createNotificationChannel()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationPermissionRationale()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            createNotificationChannel()
        }
    }

    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("This app needs notification permission to alert you when timers complete. Please grant the permission to continue.")
            .setPositiveButton("Grant Permission") { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        // Show notification code here
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "timer_notifications",
                "Timer Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for timer completion"
                enableVibration(true)
                // Do not set a vibration pattern here unless you want a custom one and it's not empty
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setMainBackgroundForFragment(fragmentType: String) {
        val mainLayout = findViewById<android.widget.RelativeLayout>(R.id.main)
        val sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            mainLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        } else if (darkMode) {
            when (fragmentType) {
                "timer" -> mainLayout.setBackgroundResource(R.color.deep_red)
                "short_break" -> mainLayout.setBackgroundResource(R.color.deep_green)
                "long_break" -> mainLayout.setBackgroundResource(R.color.deep_blue)
            }
        }
    }

    private fun showTimerFragment() {
        setMainBackgroundForFragment("timer")
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, com.arijit.pomodoro.fragments.TimerFragment())
            .commit()
    }

    private fun showShortBreakFragment() {
        setMainBackgroundForFragment("short_break")
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, com.arijit.pomodoro.fragments.ShortBreakFragment())
            .commit()
    }

    private fun showLongBreakFragment() {
        setMainBackgroundForFragment("long_break")
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, com.arijit.pomodoro.fragments.LongBreakFragment())
            .commit()
    }

    private fun getMusicFile(musicName: String): File {
        val musicDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "pomodoro_music")
        return File(musicDir, "${musicName}.mp3")
    }

    private fun playSelectedMusic() {
        val sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val selectedMusic = sharedPreferences.getString("selected_music", null)

        if (selectedMusic != null) {
            val musicFile = getMusicFile(selectedMusic)
            if (musicFile.exists()) {
                try {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(musicFile.absolutePath)
                        isLooping = true
                        prepare()
                        start()
                    }
                    isMusicPlaying = true
                    musicBtn.visibility = View.GONE
                    musicAnim.visibility = View.VISIBLE
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Select audio from settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopMusic() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        isMusicPlaying = false
        musicAnim.visibility = View.GONE
        musicBtn.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize sharedPreferences
        sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        
        // Lock to portrait unless ultra focus mode is enabled
        if (sharedPreferences.getBoolean("ultraFocusMode", false)) {
            UltraFocusManager.enableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, true)
        } else {
            UltraFocusManager.setOrientation(this, false)
        }
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topPadding = if (shouldHideAmoledAwakeWindowBars()) {
                getStatusBarHeight()
            } else {
                systemBars.top
            }
            v.setPadding(systemBars.left, topPadding, systemBars.right, systemBars.bottom)
            insets
        }
        applyAmoledAwakeWindowBars()

        // Check if we need to restore a specific fragment from notification
        val fragmentType = intent.getStringExtra("fragmentType")
        val sessionInfo = intent.getStringExtra("sessionInfo")
        
        if (fragmentType != null && sessionInfo != null) {
            when (fragmentType) {
                "focus" -> {
                    val sessionParts = sessionInfo.split("/")
                    if (sessionParts.size == 2) {
                        val currentSession = sessionParts[0].toIntOrNull() ?: 1
                        val totalSessions = sessionParts[1].toIntOrNull() ?: 4
                        val fragment = TimerFragment.newInstance(currentSession, totalSessions, false)
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.frame_layout, fragment)
                            .commit()
                    }
                }
                "shortBreak" -> {
                    val fragment = ShortBreakFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frame_layout, fragment)
                        .commit()
                }
                "longBreak" -> {
                    val fragment = LongBreakFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frame_layout, fragment)
                        .commit()
                }
            }
            // Notify service that app is opened
            TimerService.appOpened(this)
        } else {
            // Normal app start
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_layout, TimerFragment())
                .commit()
        }

        // Initialize MediaPlayer with the audio file
        mediaPlayer = MediaPlayer.create(this, R.raw.music)

        // Check and request notification permission
        checkNotificationPermission()

        sessionsTxt = findViewById(R.id.sessions_txt)
        settings_btn = findViewById(R.id.settings_btn)
        musicBtn = findViewById(R.id.music_btn)
        musicAnim = findViewById(R.id.music_animated_btn)

        settings_btn.setOnClickListener {
            vibrate()
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        musicBtn.setOnClickListener {
            vibrate()
            if (!isMusicPlaying) {
                playSelectedMusic()
            } else {
                stopMusic()
            }
        }

        musicAnim.setOnClickListener {
            vibrate()
            stopMusic()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ignore system theme changes
        val sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            sharedPreferences.edit().putBoolean("darkMode", false).apply()
        } else if (darkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            sharedPreferences.edit().putBoolean("amoledMode", false).apply()
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val ultraFocusMode = sharedPreferences.getBoolean("ultraFocusMode", false)

        // Lock to portrait unless ultra focus mode is enabled
        if (ultraFocusMode) {
            UltraFocusManager.enableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, true)
        } else {
            UltraFocusManager.disableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, false)
        }
        applyAmoledAwakeWindowBars()
    }

    private fun applyAmoledAwakeWindowBars() {
        if (sharedPreferences.getBoolean("ultraFocusMode", false)) return

        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (shouldHideAmoledAwakeWindowBars()) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun shouldHideAmoledAwakeWindowBars(): Boolean {
        return sharedPreferences.getBoolean("amoledMode", false) &&
            sharedPreferences.getBoolean("keepScreenAwake", false)
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("hapticFeedback", true)) return // Don't vibrate if haptic feedback is disabled
        
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(50) // Vibrate for 50 milliseconds
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the current fragment type
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout)
        if (currentFragment != null) {
            outState.putString("currentFragment", currentFragment.javaClass.simpleName)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore the appropriate fragment based on the saved state
        val fragmentType = savedInstanceState.getString("currentFragment")
        when (fragmentType) {
            "TimerFragment" -> setMainBackgroundForFragment("timer")
            "ShortBreakFragment" -> setMainBackgroundForFragment("short_break")
            "LongBreakFragment" -> setMainBackgroundForFragment("long_break")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMusic()
        // Disable ultra focus mode when app is closed
        if (sharedPreferences.getBoolean("ultraFocusMode", false)) {
            UltraFocusManager.disableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, false)
        }
    }
}
