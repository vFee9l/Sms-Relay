package com.smsforwarder

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.smsforwarder.databinding.ActivityMainBinding
import com.smsforwarder.databinding.ItemWebhookCardBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var repo: WebhookRepository

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshHistory(); refreshSyslog(); updateStatusBanner()
        }
    }

    // ─────────────────────────── lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = PreferencesManager(this)
        repo  = WebhookRepository(this)
        prefs.addSyslog("[APP] Started")

        setupTabs()
        setupListeners()
        setupWallpaperTab()
        updateStatusBanner()
        checkBatteryOptimization()
        refreshWebhookCards()
        refreshHistory()
        refreshSyslog()
        requestRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusBanner()
        checkBatteryOptimization()
        refreshWebhookCards()
        updateWallpaperStatus()

        val filter = IntentFilter(ForwarderService.ACTION_UPDATE_UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(uiUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(uiUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(uiUpdateReceiver)
    }

    // ─────────────────────────── tabs

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                binding.viewWebhooks.visibility  = if (t.position == 0) View.VISIBLE else View.GONE
                binding.viewWallpaper.visibility = if (t.position == 1) View.VISIBLE else View.GONE
                binding.viewHistory.visibility   = if (t.position == 2) View.VISIBLE else View.GONE
                binding.viewSyslog.visibility    = if (t.position == 3) View.VISIBLE else View.GONE
                if (t.position == 1) updateWallpaperStatus()
                if (t.position == 2) refreshHistory()
                if (t.position == 3) refreshSyslog()
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    // ─────────────────────────── listeners

    private fun setupListeners() {
        binding.switchService.setOnCheckedChangeListener { _, checked ->
            if (checked) startForwarding() else stopForwarding()
        }
        binding.fab.setOnClickListener { openSheet(null) }
        binding.btnDisableBatteryOpt.setOnClickListener { requestDisableBatteryOptimization() }
        binding.btnClearHistory.setOnClickListener { prefs.clearLogs(); refreshHistory() }
        binding.btnClearSyslog.setOnClickListener  { prefs.clearSyslog(); refreshSyslog() }
    }

    // ─────────────────────────── wallpaper tab

    private fun setupWallpaperTab() {
        // Restore saved state into UI
        binding.switchWallpaper.isChecked = prefs.wallpaperEnabled
        binding.sliderThreshold.value     = prefs.wallpaperThresholdMinutes.toFloat()
        updateThresholdLabel(prefs.wallpaperThresholdMinutes)

        // Enable/disable toggle
        binding.switchWallpaper.setOnCheckedChangeListener { _, checked ->
            prefs.wallpaperEnabled = checked
            if (checked) WallpaperWatchdog.enable(this)
            else         WallpaperWatchdog.disable(this)
            updateWallpaperStatus()
        }

        // Threshold slider
        binding.sliderThreshold.addOnChangeListener { _, value, fromUser ->
            val minutes = value.toInt()
            updateThresholdLabel(minutes)
            if (fromUser) {
                prefs.wallpaperThresholdMinutes = minutes
                if (prefs.wallpaperEnabled) WallpaperWatchdog.scheduleAlarm(this)
            }
        }

        // Check & Apply Now button
        binding.btnCheckNow.setOnClickListener {
            if (!prefs.wallpaperEnabled) {
                Toast.makeText(this, "Enable the watchdog first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            WallpaperWatchdog.checkNow(this)
            Toast.makeText(this, "Wallpaper updated", Toast.LENGTH_SHORT).show()
            updateWallpaperStatus()
        }
    }

    private fun updateThresholdLabel(minutes: Int) {
        binding.tvThresholdValue.text = if (minutes >= 60) {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "$h h" else "$h h $m min"
        } else {
            "$minutes min"
        }
    }

    private fun updateWallpaperStatus() {
        val lastSms  = prefs.lastSmsReceivedTime
        val now      = System.currentTimeMillis()
        val threshMs = prefs.wallpaperThresholdMinutes * 60_000L

        // Last SMS label
        if (lastSms == 0L) {
            binding.tvLastSmsTime.text = "Never"
        } else {
            val elapsedMs  = now - lastSms
            val elapsedMin = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
            binding.tvLastSmsTime.text = when {
                elapsedMin < 1   -> "Just now"
                elapsedMin < 60  -> "$elapsedMin min ago"
                else             -> "${elapsedMin / 60} h ${elapsedMin % 60} min ago"
            }
        }

        // Wallpaper state label
        if (!prefs.wallpaperEnabled) {
            binding.tvWallpaperState.text      = "Disabled"
            binding.tvWallpaperState.setTextColor(getColor(R.color.text_secondary))
        } else if (lastSms == 0L || (now - lastSms) >= threshMs) {
            binding.tvWallpaperState.text      = "There is Delay 🔴"
            binding.tvWallpaperState.setTextColor(getColor(R.color.red))
        } else {
            binding.tvWallpaperState.text      = "Active 🟢"
            binding.tvWallpaperState.setTextColor(getColor(R.color.green))
        }
    }

    // ─────────────────────────── battery optimization

    private fun checkBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        binding.batteryOptBanner.visibility =
            if (pm.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE
    }

    private fun requestDisableBatteryOptimization() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ─────────────────────────── webhook cards

    private fun refreshWebhookCards() {
        val webhooks = repo.getAll()
        binding.viewEmpty.visibility = if (webhooks.isEmpty()) View.VISIBLE else View.GONE
        binding.webhooksContainer.removeAllViews()

        for (wh in webhooks) {
            val cb = ItemWebhookCardBinding.inflate(layoutInflater, binding.webhooksContainer, false)

            cb.tvWebhookName.text = wh.name.ifBlank { "Unnamed" }
            cb.tvWebhookUrl.text  = wh.url.ifBlank  { "No URL set" }

            val color = if (wh.simSlot == Webhook.SIM_2)
                getColor(R.color.sim2_color) else getColor(R.color.sim1_color)
            cb.tvSimBadge.text = wh.simLabel
            cb.tvSimBadge.backgroundTintList = ColorStateList.valueOf(color)
            cb.simStripe.setBackgroundColor(color)

            cb.switchWebhookEnabled.isChecked = wh.enabled
            updateActiveLabel(cb, wh.enabled)

            cb.switchWebhookEnabled.setOnCheckedChangeListener { _, checked ->
                repo.save(wh.copy(enabled = checked))
                updateActiveLabel(cb, checked)
                prefs.addSyslog("[CONFIG] '${wh.name}' ${if (checked) "enabled" else "disabled"}")
                updateStatusBanner()
            }

            cb.btnCardTest.setOnClickListener { runCardTest(wh, cb) }
            cb.btnCardEdit.setOnClickListener { openSheet(wh) }

            binding.webhooksContainer.addView(cb.root)
        }
    }

    private fun updateActiveLabel(cb: ItemWebhookCardBinding, enabled: Boolean) {
        cb.tvActiveLabel.text = if (enabled) "Active" else "Inactive"
        cb.tvActiveLabel.setTextColor(
            if (enabled) getColor(R.color.green) else getColor(R.color.text_secondary)
        )
    }

    private fun runCardTest(wh: Webhook, cb: ItemWebhookCardBinding) {
        if (wh.url.isBlank()) {
            Toast.makeText(this, "No URL configured", Toast.LENGTH_SHORT).show()
            return
        }
        cb.btnCardTest.isEnabled = false
        cb.tvCardTestResult.visibility = View.VISIBLE
        cb.tvCardTestResult.setTextColor(Color.parseColor("#6B7280"))
        cb.tvCardTestResult.text = "Sending test…"

        WebhookManager.send(
            webhookUrl    = wh.url,
            bodyTemplate  = wh.bodyTemplate,
            from          = "TEST_SENDER",
            message       = "T2-SMS-forwarding test message",
            sentTimestamp = System.currentTimeMillis(),
            customHeaders = wh.customHeaders,
            disableSsl    = wh.disableSsl,
            onSuccess = {
                runOnUiThread {
                    cb.btnCardTest.isEnabled = true
                    cb.tvCardTestResult.setTextColor(Color.parseColor("#16A34A"))
                    cb.tvCardTestResult.text = "✓ Webhook received the request"
                }
            },
            onError = { err ->
                runOnUiThread {
                    cb.btnCardTest.isEnabled = true
                    cb.tvCardTestResult.setTextColor(Color.parseColor("#DC2626"))
                    cb.tvCardTestResult.text = "✗ $err"
                }
            }
        )
    }

    private fun openSheet(webhook: Webhook?) {
        val sheet = WebhookConfigSheet.newInstance(webhook)
        sheet.onSaved = { refreshWebhookCards(); refreshSyslog(); updateStatusBanner() }
        sheet.show(supportFragmentManager, "webhook_sheet")
    }

    // ─────────────────────────── service control

    private fun startForwarding() {
        if (repo.getAll().none { it.url.isNotBlank() }) {
            Toast.makeText(this, "Add at least one webhook first", Toast.LENGTH_LONG).show()
            binding.switchService.isChecked = false
            return
        }
        prefs.isForwardingEnabled = true
        BootReceiver.start(this)
        updateStatusBanner()
    }

    private fun stopForwarding() {
        prefs.isForwardingEnabled = false
        startService(Intent(this, ForwarderService::class.java).apply {
            action = ForwarderService.ACTION_STOP_SERVICE
        })
        updateStatusBanner()
    }

    private fun isServiceRunning(): Boolean {
        @Suppress("DEPRECATION")
        return (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ForwarderService::class.java.name }
    }

    private fun updateStatusBanner() {
        val running = isServiceRunning()
        binding.switchService.setOnCheckedChangeListener(null)
        binding.switchService.isChecked = running
        binding.switchService.setOnCheckedChangeListener { _, c ->
            if (c) startForwarding() else stopForwarding()
        }
        if (running) {
            val n = repo.getAll().count { it.enabled && it.url.isNotBlank() }
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_green)
            binding.tvServiceStatus.text  = "Service Running"
            binding.tvStatusSubtitle.text = "$n active webhook${if (n != 1) "s" else ""}"
        } else {
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_red)
            binding.tvServiceStatus.text  = "Service Stopped"
            binding.tvStatusSubtitle.text = "Toggle to start forwarding"
        }
    }

    // ─────────────────────────── logs

    private fun refreshHistory() {
        val logs = prefs.getLogs()
        binding.tvHistory.text =
            if (logs.isEmpty()) "No forwarding events yet"
            else logs.take(100).joinToString("\n\n")
    }

    private fun refreshSyslog() {
        val logs = prefs.getSyslog()
        binding.tvSyslog.text =
            if (logs.isEmpty()) "No system events yet"
            else logs.take(200).joinToString("\n")
    }

    // ─────────────────────────── permissions

    private fun requestRequiredPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }
}
