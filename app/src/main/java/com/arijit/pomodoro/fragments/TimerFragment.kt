package com.arijit.pomodoro.fragments

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresPermission
import androidx.cardview.widget.CardView
import com.arijit.pomodoro.R
import android.widget.RelativeLayout
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

class TimerFragment : Fragment() {
    private lateinit var minTxt: TextView
    private lateinit var secTxt: TextView
    private lateinit var playBtn: CardView
    private lateinit var pauseBtn: CardView
    private lateinit var resetBtn: ImageView
    private lateinit var skipBtn: ImageView
    private lateinit var brain: ImageView
    private lateinit var focusCardBg: LinearLayout
    private lateinit var focusTxt: TextView
    private lateinit var sessionsTxt: TextView
    private var mediaPlayer: MediaPlayer? = null
    
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var timerRunning = false
    private var currentSession = 1
    private var totalSessions = 4
    private var autoStart = false
    private var isFromShortBreak = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            timeLeftInMillis = savedInstanceState.getLong("timeLeftInMillis")
            timerRunning = savedInstanceState.getBoolean("timerRunning")
            currentSession = savedInstanceState.getInt("currentSession")
            totalSessions = savedInstanceState.getInt("totalSessions")
            autoStart = savedInstanceState.getBoolean("autoStart")
            isFromShortBreak = savedInstanceState.getBoolean("isFromShortBreak")
        } else {
            loadSettings()
        }
    }
    
    private fun loadSettings() {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        timeLeftInMillis = sharedPreferences.getInt("focusedTime", 25) * 60 * 1000L
        totalSessions = sharedPreferences.getInt("sessions", 4)
        autoStart = sharedPreferences.getBoolean("autoStart", false)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_timer, container, false)
        minTxt = view.findViewById(R.id.min_txt)
        secTxt = view.findViewById(R.id.sec_txt)
        playBtn = view.findViewById(R.id.play_btn)
        pauseBtn = view.findViewById(R.id.pause_btn)
        resetBtn = view.findViewById(R.id.reset_btn)
        skipBtn = view.findViewById(R.id.skip_btn)
        focusCardBg = view.findViewById(R.id.focus_card_bg)
        focusTxt = view.findViewById(R.id.focus_txt)
        brain = view.findViewById(R.id.brain)
        sessionsTxt = requireActivity().findViewById(R.id.sessions_txt)

        val parentLayout = requireActivity().findViewById<android.widget.RelativeLayout>(R.id.main)
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        }

        updateCountdownText()
        updateSessionsText()
        
        playBtn.setOnClickListener {
            vibrate()
            startTimer()
        }
        
        pauseBtn.setOnClickListener {
            vibrate()
            pauseTimer()
        }
        
        resetBtn.setOnClickListener {
            vibrate()
            resetTimer()
        }
        
        skipBtn.setOnClickListener {
            vibrate()
            loadShortBreakFragment()
        }

        // Restore timer state if it was running
        if (timerRunning) {
            playBtn.visibility = View.GONE
            pauseBtn.visibility = View.VISIBLE
            resetBtn.visibility = View.VISIBLE
            skipBtn.visibility = View.VISIBLE
            startTimer()
        } else if (autoStart && isFromShortBreak) {
            startTimer()
        }

        return view
    }
    
    private fun startTimer() {
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountdownText()
            }

            override fun onFinish() {
                timerRunning = false
                updateTimerState(false)
                playAlarm()
                if (currentSession < totalSessions) {
                    loadShortBreakFragment()
                } else {
                    Toast.makeText(requireContext(), "All sessions completed", Toast.LENGTH_SHORT).show()
                    loadLongBreakFragment()
                }
            }
        }.start()
        
        timerRunning = true
        updateTimerState(true)
        playBtn.visibility = View.GONE
        pauseBtn.visibility = View.VISIBLE
        resetBtn.visibility = View.VISIBLE
        skipBtn.visibility = View.VISIBLE
    }
    
    private fun pauseTimer() {
        countDownTimer?.cancel()
        timerRunning = false
        updateTimerState(false)
        playBtn.visibility = View.VISIBLE
        pauseBtn.visibility = View.GONE
    }
    
    private fun resetTimer() {
        countDownTimer?.cancel()
        loadSettings()
        timerRunning = false
        updateTimerState(false)
        updateCountdownText()
        
        playBtn.visibility = View.VISIBLE
        pauseBtn.visibility = View.GONE
        resetBtn.visibility = View.GONE
        skipBtn.visibility = View.GONE
    }
    
    private fun updateCountdownText() {
        val minutes = (timeLeftInMillis / 1000).toInt() / 60
        val seconds = (timeLeftInMillis / 1000).toInt() % 60
        
        minTxt.text = String.format("%02d", minutes)
        secTxt.text = String.format("%02d", seconds)
    }

    private fun updateSessionsText() {
        sessionsTxt.text = "$currentSession/$totalSessions"
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(50)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun loadShortBreakFragment() {
        val parentLayout = requireActivity().findViewById<RelativeLayout>(R.id.main)
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        } else if (darkMode) {
            parentLayout.setBackgroundResource(R.color.deep_green)
        } else {
            parentLayout.setBackgroundResource(R.color.light_green)
        }

        val fragment = ShortBreakFragment()
        fragment.setSessionInfo(currentSession, totalSessions, autoStart, true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }

    private fun loadLongBreakFragment() {
        val parentLayout = requireActivity().findViewById<RelativeLayout>(R.id.main)
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        } else if (darkMode) {
            parentLayout.setBackgroundResource(R.color.deep_blue)
        } else {
            parentLayout.setBackgroundResource(R.color.light_blue)
        }

        val fragment = LongBreakFragment()
        fragment.setSessionInfo(1, totalSessions) // Reset to first session
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }

    private fun updateTimerState(isRunning: Boolean) {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putBoolean("isTimerRunning", isRunning)
            putBoolean("isBreakActive", false)
            apply()
        }
    }

    private fun playAlarm() {
        try {
            val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
            val alarmDuration = sharedPreferences.getInt("alarmDuration", 3) * 1000L // Convert to milliseconds

            // Show notification
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(requireContext(), "timer_notifications")
                .setSmallIcon(R.drawable.brain)
                .setContentTitle("Timer Complete")
                .setContentText("Your timer is up. Good job!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(1, builder.build())

            // Vibrate
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrationEffect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
                    vibrator.vibrate(vibrationEffect)
                } else {
                    vibrator.vibrate(longArrayOf(0, 500, 500), 0)
                }
            }

            // Stop after configured duration
            android.os.Handler().postDelayed({
                vibrator.cancel()
            }, alarmDuration)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("timeLeftInMillis", timeLeftInMillis)
        outState.putBoolean("timerRunning", timerRunning)
        outState.putInt("currentSession", currentSession)
        outState.putInt("totalSessions", totalSessions)
        outState.putBoolean("autoStart", autoStart)
        outState.putBoolean("isFromShortBreak", isFromShortBreak)
    }

    companion object {
        fun newInstance(currentSession: Int, totalSessions: Int, autoStart: Boolean, isFromShortBreak: Boolean = false): TimerFragment {
            return TimerFragment().apply {
                this.currentSession = currentSession
                this.totalSessions = totalSessions
                this.autoStart = autoStart
                this.isFromShortBreak = isFromShortBreak
            }
        }
    }
}