package com.smsforwarder

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.smsforwarder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshLogs()
            updateServiceStatusView()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = PreferencesManager(this)

        loadSettings()
        setupListeners()
        updateServiceStatusView()
        refreshLogs()
        requestRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusView()
        val filter = IntentFilter(ForwarderService.ACTION_UPDATE_UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(uiUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(uiUpdateReceiver)
    }

    // ------------------------------------------------------------------ settings

    private fun loadSettings() {
        binding.etWebhookUrl.setText(prefs.webhookUrl)
        binding.etSecret.setText(prefs.secret)
        binding.etDeviceId.setText(prefs.deviceId)
        binding.etSim1Number.setText(prefs.sim1Number)
        binding.etSim2Number.setText(prefs.sim2Number)

        binding.switchFilterSenders.isChecked = prefs.filterBySender
        updateFilterUI(prefs.filterBySender)
        refreshSenderChips()
    }

    private fun setupListeners() {
        // Service toggle
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startForwarding() else stopForwarding()
        }

        // Webhook settings save
        binding.btnSaveWebhook.setOnClickListener {
            val url = binding.etWebhookUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                binding.tilWebhookUrl.error = "URL is required"
                return@setOnClickListener
            }
            binding.tilWebhookUrl.error = null
            prefs.webhookUrl = url
            prefs.secret = binding.etSecret.text?.toString()?.trim() ?: ""
            prefs.deviceId = binding.etDeviceId.text?.toString()?.trim() ?: ""
            Toast.makeText(this, "Webhook settings saved", Toast.LENGTH_SHORT).show()
        }

        // SIM numbers save
        binding.btnSaveSim.setOnClickListener {
            prefs.sim1Number = binding.etSim1Number.text?.toString()?.trim() ?: ""
            prefs.sim2Number = binding.etSim2Number.text?.toString()?.trim() ?: ""
            Toast.makeText(this, "SIM numbers saved", Toast.LENGTH_SHORT).show()
        }

        // Sender filter toggle
        binding.switchFilterSenders.setOnCheckedChangeListener { _, isChecked ->
            prefs.filterBySender = isChecked
            updateFilterUI(isChecked)
        }

        // Add sender button
        binding.btnAddSender.setOnClickListener { addSender() }
        binding.etSenderInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addSender(); true } else false
        }

        // Clear logs
        binding.btnClearLogs.setOnClickListener {
            prefs.clearLogs()
            binding.tvLogs.text = "No activity yet"
        }

        // Battery optimisation
        binding.btnBatteryOptimization.setOnClickListener {
            requestBatteryOptimisationExclusion()
        }
    }

    // ------------------------------------------------------------------ service control

    private fun startForwarding() {
        if (prefs.webhookUrl.isBlank()) {
            Toast.makeText(this, "Please set a Webhook URL first", Toast.LENGTH_LONG).show()
            binding.switchService.isChecked = false
            return
        }
        prefs.isForwardingEnabled = true
        BootReceiver.start(this)
        updateServiceStatusView()
    }

    private fun stopForwarding() {
        prefs.isForwardingEnabled = false
        startService(Intent(this, ForwarderService::class.java).apply {
            action = ForwarderService.ACTION_STOP_SERVICE
        })
        updateServiceStatusView()
    }

    private fun isServiceRunning(): Boolean {
        @Suppress("DEPRECATION")
        return (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ForwarderService::class.java.name }
    }

    private fun updateServiceStatusView() {
        val running = isServiceRunning()

        // Temporarily detach listener to avoid feedback loop
        binding.switchService.setOnCheckedChangeListener(null)
        binding.switchService.isChecked = running
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startForwarding() else stopForwarding()
        }

        if (running) {
            binding.tvServiceStatus.text = "Service Running"
            binding.tvStatusSubtitle.text = "Forwarding SMS to webhook"
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_green)
        } else {
            binding.tvServiceStatus.text = "Service Stopped"
            binding.tvStatusSubtitle.text = "Tap the toggle to start"
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_red)
        }
    }

    // ------------------------------------------------------------------ senders

    private fun addSender() {
        val sender = binding.etSenderInput.text?.toString()?.trim() ?: return
        if (sender.isEmpty()) return
        prefs.allowedSenders = prefs.allowedSenders.toMutableSet().apply { add(sender) }
        binding.etSenderInput.setText("")
        refreshSenderChips()
    }

    private fun refreshSenderChips() {
        binding.chipGroupSenders.removeAllViews()
        prefs.allowedSenders.forEach { sender ->
            val chip = Chip(this).apply {
                text = sender
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    prefs.allowedSenders = prefs.allowedSenders.toMutableSet().apply { remove(sender) }
                    refreshSenderChips()
                }
            }
            binding.chipGroupSenders.addView(chip)
        }
    }

    private fun updateFilterUI(enabled: Boolean) {
        binding.layoutSenderInput.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.tvFilterHint.text = if (enabled)
            "Filter ON — only listed senders will be forwarded"
        else
            "Filter OFF — all incoming SMS will be forwarded"
    }

    // ------------------------------------------------------------------ logs

    private fun refreshLogs() {
        val logs = prefs.getLogs()
        binding.tvLogs.text = if (logs.isEmpty()) "No activity yet"
        else logs.take(30).joinToString("\n\n")
    }

    // ------------------------------------------------------------------ battery

    private fun requestBatteryOptimisationExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                Toast.makeText(this, "Battery optimisation is already disabled ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------ permissions

    private fun requestRequiredPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
