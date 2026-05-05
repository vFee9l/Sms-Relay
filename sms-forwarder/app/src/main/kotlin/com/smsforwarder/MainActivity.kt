package com.smsforwarder

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
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
        // SIM 1
        binding.switchSim1Enabled.isChecked = prefs.sim1Enabled
        binding.etSim1WebhookUrl.setText(prefs.sim1WebhookUrl)
        binding.etSim1Secret.setText(prefs.sim1Secret)
        binding.etSim1DeviceId.setText(prefs.sim1DeviceId)
        binding.etSim1Phone.setText(prefs.sim1PhoneNumber)
        refreshSim1SenderChips()
        updateSim1SenderHint()

        // SIM 2
        binding.switchSim2Enabled.isChecked = prefs.sim2Enabled
        binding.etSim2WebhookUrl.setText(prefs.sim2WebhookUrl)
        binding.etSim2Secret.setText(prefs.sim2Secret)
        binding.etSim2DeviceId.setText(prefs.sim2DeviceId)
        binding.etSim2Phone.setText(prefs.sim2PhoneNumber)
        refreshSim2SenderChips()
        updateSim2SenderHint()
    }

    private fun setupListeners() {
        // Global service toggle
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startForwarding() else stopForwarding()
        }

        // ── SIM 1 ──────────────────────────────────────────────────────────────

        // Per-SIM enable toggle
        binding.switchSim1Enabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.sim1Enabled = isChecked
            val state = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "SIM 1 forwarding $state", Toast.LENGTH_SHORT).show()
        }

        // SIM 1 save
        binding.btnSaveSim1.setOnClickListener {
            val url = binding.etSim1WebhookUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                binding.tilSim1WebhookUrl.error = "URL is required"
                return@setOnClickListener
            }
            binding.tilSim1WebhookUrl.error = null
            prefs.sim1WebhookUrl  = url
            prefs.sim1Secret      = binding.etSim1Secret.text?.toString()?.trim() ?: ""
            prefs.sim1DeviceId    = binding.etSim1DeviceId.text?.toString()?.trim() ?: ""
            prefs.sim1PhoneNumber = binding.etSim1Phone.text?.toString()?.trim() ?: ""
            Toast.makeText(this, "SIM 1 settings saved", Toast.LENGTH_SHORT).show()
        }

        // SIM 1 test
        binding.btnTestSim1.setOnClickListener {
            testConnection(
                button     = binding.btnTestSim1,
                statusView = binding.tvTestSim1Result,
                url        = binding.etSim1WebhookUrl.text?.toString()?.trim() ?: "",
                secret     = binding.etSim1Secret.text?.toString()?.trim() ?: "",
                deviceId   = binding.etSim1DeviceId.text?.toString()?.trim() ?: "",
                sentTo     = binding.etSim1Phone.text?.toString()?.trim() ?: "SIM1",
                simLabel   = "SIM1"
            )
        }

        // SIM 1 senders
        binding.btnAddSim1Sender.setOnClickListener { addSim1Sender() }
        binding.etSim1SenderInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addSim1Sender(); true } else false
        }

        // ── SIM 2 ──────────────────────────────────────────────────────────────

        // Per-SIM enable toggle
        binding.switchSim2Enabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.sim2Enabled = isChecked
            val state = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "SIM 2 forwarding $state", Toast.LENGTH_SHORT).show()
        }

        // SIM 2 save
        binding.btnSaveSim2.setOnClickListener {
            val url = binding.etSim2WebhookUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                binding.tilSim2WebhookUrl.error = "URL is required"
                return@setOnClickListener
            }
            binding.tilSim2WebhookUrl.error = null
            prefs.sim2WebhookUrl  = url
            prefs.sim2Secret      = binding.etSim2Secret.text?.toString()?.trim() ?: ""
            prefs.sim2DeviceId    = binding.etSim2DeviceId.text?.toString()?.trim() ?: ""
            prefs.sim2PhoneNumber = binding.etSim2Phone.text?.toString()?.trim() ?: ""
            Toast.makeText(this, "SIM 2 settings saved", Toast.LENGTH_SHORT).show()
        }

        // SIM 2 test
        binding.btnTestSim2.setOnClickListener {
            testConnection(
                button     = binding.btnTestSim2,
                statusView = binding.tvTestSim2Result,
                url        = binding.etSim2WebhookUrl.text?.toString()?.trim() ?: "",
                secret     = binding.etSim2Secret.text?.toString()?.trim() ?: "",
                deviceId   = binding.etSim2DeviceId.text?.toString()?.trim() ?: "",
                sentTo     = binding.etSim2Phone.text?.toString()?.trim() ?: "SIM2",
                simLabel   = "SIM2"
            )
        }

        // SIM 2 senders
        binding.btnAddSim2Sender.setOnClickListener { addSim2Sender() }
        binding.etSim2SenderInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addSim2Sender(); true } else false
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
        if (prefs.sim1WebhookUrl.isBlank() && prefs.sim2WebhookUrl.isBlank()) {
            Toast.makeText(this, "Please save at least one SIM webhook URL first", Toast.LENGTH_LONG).show()
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
        binding.switchService.setOnCheckedChangeListener(null)
        binding.switchService.isChecked = running
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startForwarding() else stopForwarding()
        }

        if (running) {
            binding.tvServiceStatus.text = "Service Running"
            binding.tvStatusSubtitle.text = "Forwarding SMS to webhooks"
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_green)
        } else {
            binding.tvServiceStatus.text = "Service Stopped"
            binding.tvStatusSubtitle.text = "Tap the toggle to start"
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_red)
        }
    }

    // ------------------------------------------------------------------ test connection

    private fun testConnection(
        button: MaterialButton,
        statusView: TextView,
        url: String,
        secret: String,
        deviceId: String,
        sentTo: String,
        simLabel: String
    ) {
        if (url.isEmpty()) {
            statusView.visibility = View.VISIBLE
            statusView.setTextColor(Color.parseColor("#F44336"))
            statusView.text = "✗ Enter a Webhook URL first"
            return
        }

        button.isEnabled = false
        statusView.visibility = View.VISIBLE
        statusView.setTextColor(Color.parseColor("#757575"))
        statusView.text = "Sending test…"

        WebhookManager.send(
            webhookUrl    = url,
            secret        = secret,
            from          = "TEST_$simLabel",
            message       = "T2-SMS-forwarding test message from $simLabel",
            sentTimestamp = System.currentTimeMillis(),
            sentTo        = sentTo,
            deviceId      = deviceId,
            onSuccess = {
                runOnUiThread {
                    button.isEnabled = true
                    statusView.setTextColor(Color.parseColor("#388E3C"))
                    statusView.text = "✓ Connection OK — webhook received the request"
                }
            },
            onError = { error ->
                runOnUiThread {
                    button.isEnabled = true
                    statusView.setTextColor(Color.parseColor("#F44336"))
                    statusView.text = "✗ Failed: $error"
                }
            }
        )
    }

    // ------------------------------------------------------------------ SIM 1 senders

    private fun addSim1Sender() {
        val sender = binding.etSim1SenderInput.text?.toString()?.trim() ?: return
        if (sender.isEmpty()) return
        prefs.sim1AllowedSenders = prefs.sim1AllowedSenders.toMutableSet().apply { add(sender) }
        binding.etSim1SenderInput.setText("")
        refreshSim1SenderChips()
        updateSim1SenderHint()
    }

    private fun refreshSim1SenderChips() {
        binding.chipGroupSim1Senders.removeAllViews()
        prefs.sim1AllowedSenders.forEach { sender ->
            val chip = Chip(this).apply {
                text = sender
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    prefs.sim1AllowedSenders = prefs.sim1AllowedSenders.toMutableSet().apply { remove(sender) }
                    refreshSim1SenderChips()
                    updateSim1SenderHint()
                }
            }
            binding.chipGroupSim1Senders.addView(chip)
        }
    }

    private fun updateSim1SenderHint() {
        val count = prefs.sim1AllowedSenders.size
        binding.tvSim1SenderHint.text = if (count == 0)
            "No filter — all senders forwarded on SIM 1"
        else
            "$count sender(s) — only these forwarded on SIM 1"
    }

    // ------------------------------------------------------------------ SIM 2 senders

    private fun addSim2Sender() {
        val sender = binding.etSim2SenderInput.text?.toString()?.trim() ?: return
        if (sender.isEmpty()) return
        prefs.sim2AllowedSenders = prefs.sim2AllowedSenders.toMutableSet().apply { add(sender) }
        binding.etSim2SenderInput.setText("")
        refreshSim2SenderChips()
        updateSim2SenderHint()
    }

    private fun refreshSim2SenderChips() {
        binding.chipGroupSim2Senders.removeAllViews()
        prefs.sim2AllowedSenders.forEach { sender ->
            val chip = Chip(this).apply {
                text = sender
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    prefs.sim2AllowedSenders = prefs.sim2AllowedSenders.toMutableSet().apply { remove(sender) }
                    refreshSim2SenderChips()
                    updateSim2SenderHint()
                }
            }
            binding.chipGroupSim2Senders.addView(chip)
        }
    }

    private fun updateSim2SenderHint() {
        val count = prefs.sim2AllowedSenders.size
        binding.tvSim2SenderHint.text = if (count == 0)
            "No filter — all senders forwarded on SIM 2"
        else
            "$count sender(s) — only these forwarded on SIM 2"
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
                Toast.makeText(this, "Battery optimisation already disabled ✓", Toast.LENGTH_SHORT).show()
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
