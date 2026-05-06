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
import com.google.android.material.tabs.TabLayout
import com.smsforwarder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshHistory()
            refreshSyslog()
            updateServiceStatusView()
        }
    }

    // ─────────────────────────────────────────────── lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = PreferencesManager(this)
        prefs.addSyslog("[APP] MainActivity started")

        setupTabs()
        loadSettings()
        setupListeners()
        updateServiceStatusView()
        refreshHistory()
        refreshSyslog()
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

    // ─────────────────────────────────────────────── tabs

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.viewSettings.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                binding.viewHistory.visibility  = if (tab.position == 1) View.VISIBLE else View.GONE
                binding.viewSyslog.visibility   = if (tab.position == 2) View.VISIBLE else View.GONE
                if (tab.position == 1) refreshHistory()
                if (tab.position == 2) refreshSyslog()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ─────────────────────────────────────────────── load settings

    private fun loadSettings() {
        // SIM 1
        binding.switchSim1Enabled.isChecked = prefs.sim1Enabled
        binding.etSim1WebhookUrl.setText(prefs.sim1WebhookUrl)
        binding.etSim1Secret.setText(prefs.sim1Secret)
        binding.etSim1DeviceId.setText(prefs.sim1DeviceId)
        binding.etSim1Phone.setText(prefs.sim1PhoneNumber)
        binding.switchSim1DisableSsl.isChecked = prefs.sim1DisableSsl
        refreshSim1SenderChips()
        refreshSim1HeaderChips()
        refreshSim1BodyChips()
        updateSim1SenderHint()

        // SIM 2
        binding.switchSim2Enabled.isChecked = prefs.sim2Enabled
        binding.etSim2WebhookUrl.setText(prefs.sim2WebhookUrl)
        binding.etSim2Secret.setText(prefs.sim2Secret)
        binding.etSim2DeviceId.setText(prefs.sim2DeviceId)
        binding.etSim2Phone.setText(prefs.sim2PhoneNumber)
        binding.switchSim2DisableSsl.isChecked = prefs.sim2DisableSsl
        refreshSim2SenderChips()
        refreshSim2HeaderChips()
        refreshSim2BodyChips()
        updateSim2SenderHint()
    }

    // ─────────────────────────────────────────────── listeners

    private fun setupListeners() {
        // Global service toggle
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startForwarding() else stopForwarding()
        }

        // ── SIM 1 ────────────────────────────────────────────────────────────

        binding.switchSim1Enabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.sim1Enabled = isChecked
            prefs.addSyslog("[CONFIG] SIM 1 forwarding ${if (isChecked) "enabled" else "disabled"}")
            Toast.makeText(this, "SIM 1 ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.switchSim1DisableSsl.setOnCheckedChangeListener { _, isChecked ->
            prefs.sim1DisableSsl = isChecked
            prefs.addSyslog("[CONFIG] SIM 1 SSL verification ${if (isChecked) "disabled" else "enabled"}")
        }

        binding.btnSaveSim1.setOnClickListener {
            val url = binding.etSim1WebhookUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) { binding.tilSim1WebhookUrl.error = "URL is required"; return@setOnClickListener }
            binding.tilSim1WebhookUrl.error = null
            prefs.sim1WebhookUrl  = url
            prefs.sim1Secret      = binding.etSim1Secret.text?.toString()?.trim() ?: ""
            prefs.sim1DeviceId    = binding.etSim1DeviceId.text?.toString()?.trim() ?: ""
            prefs.sim1PhoneNumber = binding.etSim1Phone.text?.toString()?.trim() ?: ""
            prefs.addSyslog("[CONFIG] SIM 1 settings saved — webhook: $url")
            Toast.makeText(this, "SIM 1 settings saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestSim1.setOnClickListener {
            testConnection(
                button     = binding.btnTestSim1,
                statusView = binding.tvTestSim1Result,
                url        = binding.etSim1WebhookUrl.text?.toString()?.trim() ?: "",
                secret     = binding.etSim1Secret.text?.toString()?.trim() ?: "",
                deviceId   = binding.etSim1DeviceId.text?.toString()?.trim() ?: "",
                sentTo     = binding.etSim1Phone.text?.toString()?.trim() ?: "SIM1",
                simLabel   = "SIM1",
                customHeaders = PreferencesManager.parseKvSet(prefs.sim1CustomHeaders),
                extraBody     = PreferencesManager.parseKvSet(prefs.sim1ExtraBody),
                disableSsl    = prefs.sim1DisableSsl
            )
        }

        binding.btnAddSim1Sender.setOnClickListener { addSim1Sender() }
        binding.etSim1SenderInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { addSim1Sender(); true } else false
        }

        binding.btnAddSim1Header.setOnClickListener { addSim1Header() }
        binding.btnAddSim1Body.setOnClickListener { addSim1BodyField() }

        // ── SIM 2 ────────────────────────────────────────────────────────────

        binding.switchSim2Enabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.sim2Enabled = isChecked
            prefs.addSyslog("[CONFIG] SIM 2 forwarding ${if (isChecked) "enabled" else "disabled"}")
            Toast.makeText(this, "SIM 2 ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        binding.switchSim2DisableSsl.setOnCheckedChangeListener { _, isChecked ->
            prefs.sim2DisableSsl = isChecked
            prefs.addSyslog("[CONFIG] SIM 2 SSL verification ${if (isChecked) "disabled" else "enabled"}")
        }

        binding.btnSaveSim2.setOnClickListener {
            val url = binding.etSim2WebhookUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) { binding.tilSim2WebhookUrl.error = "URL is required"; return@setOnClickListener }
            binding.tilSim2WebhookUrl.error = null
            prefs.sim2WebhookUrl  = url
            prefs.sim2Secret      = binding.etSim2Secret.text?.toString()?.trim() ?: ""
            prefs.sim2DeviceId    = binding.etSim2DeviceId.text?.toString()?.trim() ?: ""
            prefs.sim2PhoneNumber = binding.etSim2Phone.text?.toString()?.trim() ?: ""
            prefs.addSyslog("[CONFIG] SIM 2 settings saved — webhook: $url")
            Toast.makeText(this, "SIM 2 settings saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestSim2.setOnClickListener {
            testConnection(
                button     = binding.btnTestSim2,
                statusView = binding.tvTestSim2Result,
                url        = binding.etSim2WebhookUrl.text?.toString()?.trim() ?: "",
                secret     = binding.etSim2Secret.text?.toString()?.trim() ?: "",
                deviceId   = binding.etSim2DeviceId.text?.toString()?.trim() ?: "",
                sentTo     = binding.etSim2Phone.text?.toString()?.trim() ?: "SIM2",
                simLabel   = "SIM2",
                customHeaders = PreferencesManager.parseKvSet(prefs.sim2CustomHeaders),
                extraBody     = PreferencesManager.parseKvSet(prefs.sim2ExtraBody),
                disableSsl    = prefs.sim2DisableSsl
            )
        }

        binding.btnAddSim2Sender.setOnClickListener { addSim2Sender() }
        binding.etSim2SenderInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { addSim2Sender(); true } else false
        }

        binding.btnAddSim2Header.setOnClickListener { addSim2Header() }
        binding.btnAddSim2Body.setOnClickListener { addSim2BodyField() }

        // Battery
        binding.btnBatteryOptimization.setOnClickListener { requestBatteryOptimisationExclusion() }

        // History / Syslog clear buttons
        binding.btnClearHistory.setOnClickListener {
            prefs.clearLogs()
            refreshHistory()
        }
        binding.btnClearSyslog.setOnClickListener {
            prefs.clearSyslog()
            refreshSyslog()
        }
    }

    // ─────────────────────────────────────────────── service control

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

    // ─────────────────────────────────────────────── test connection

    private fun testConnection(
        button: MaterialButton,
        statusView: TextView,
        url: String,
        secret: String,
        deviceId: String,
        sentTo: String,
        simLabel: String,
        customHeaders: Map<String, String>,
        extraBody: Map<String, String>,
        disableSsl: Boolean
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
            message       = "T2-SMS-forwarding test from $simLabel",
            sentTimestamp = System.currentTimeMillis(),
            sentTo        = sentTo,
            deviceId      = deviceId,
            customHeaders = customHeaders,
            extraBody     = extraBody,
            disableSsl    = disableSsl,
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

    // ─────────────────────────────────────────────── SIM 1 senders

    private fun addSim1Sender() {
        val s = binding.etSim1SenderInput.text?.toString()?.trim() ?: return
        if (s.isEmpty()) return
        prefs.sim1AllowedSenders = prefs.sim1AllowedSenders.toMutableSet().apply { add(s) }
        binding.etSim1SenderInput.setText("")
        refreshSim1SenderChips(); updateSim1SenderHint()
    }

    private fun refreshSim1SenderChips() {
        binding.chipGroupSim1Senders.removeAllViews()
        prefs.sim1AllowedSenders.forEach { s ->
            binding.chipGroupSim1Senders.addView(makeRemovableChip(s) {
                prefs.sim1AllowedSenders = prefs.sim1AllowedSenders.toMutableSet().apply { remove(s) }
                refreshSim1SenderChips(); updateSim1SenderHint()
            })
        }
    }

    private fun updateSim1SenderHint() {
        val n = prefs.sim1AllowedSenders.size
        binding.tvSim1SenderHint.text = if (n == 0) "No filter — all senders forwarded" else "$n sender(s) — only these forwarded"
    }

    // ─────────────────────────────────────────────── SIM 1 headers

    private fun addSim1Header() {
        val key = binding.etSim1HeaderKey.text?.toString()?.trim() ?: return
        val value = binding.etSim1HeaderValue.text?.toString()?.trim() ?: return
        if (key.isEmpty()) { binding.etSim1HeaderKey.error = "Required"; return }
        prefs.sim1CustomHeaders = prefs.sim1CustomHeaders.toMutableSet()
            .apply { add(PreferencesManager.encodeKv(key, value)) }
        binding.etSim1HeaderKey.setText(""); binding.etSim1HeaderValue.setText("")
        refreshSim1HeaderChips()
    }

    private fun refreshSim1HeaderChips() {
        binding.chipGroupSim1Headers.removeAllViews()
        prefs.sim1CustomHeaders.forEach { entry ->
            binding.chipGroupSim1Headers.addView(makeRemovableChip(entry.replace(PreferencesManager.KV_SEP, ": ")) {
                prefs.sim1CustomHeaders = prefs.sim1CustomHeaders.toMutableSet().apply { remove(entry) }
                refreshSim1HeaderChips()
            })
        }
    }

    // ─────────────────────────────────────────────── SIM 1 body

    private fun addSim1BodyField() {
        val key = binding.etSim1BodyKey.text?.toString()?.trim() ?: return
        val value = binding.etSim1BodyValue.text?.toString()?.trim() ?: return
        if (key.isEmpty()) { binding.etSim1BodyKey.error = "Required"; return }
        prefs.sim1ExtraBody = prefs.sim1ExtraBody.toMutableSet()
            .apply { add(PreferencesManager.encodeKv(key, value)) }
        binding.etSim1BodyKey.setText(""); binding.etSim1BodyValue.setText("")
        refreshSim1BodyChips()
    }

    private fun refreshSim1BodyChips() {
        binding.chipGroupSim1Body.removeAllViews()
        prefs.sim1ExtraBody.forEach { entry ->
            binding.chipGroupSim1Body.addView(makeRemovableChip(entry.replace(PreferencesManager.KV_SEP, ": ")) {
                prefs.sim1ExtraBody = prefs.sim1ExtraBody.toMutableSet().apply { remove(entry) }
                refreshSim1BodyChips()
            })
        }
    }

    // ─────────────────────────────────────────────── SIM 2 senders

    private fun addSim2Sender() {
        val s = binding.etSim2SenderInput.text?.toString()?.trim() ?: return
        if (s.isEmpty()) return
        prefs.sim2AllowedSenders = prefs.sim2AllowedSenders.toMutableSet().apply { add(s) }
        binding.etSim2SenderInput.setText("")
        refreshSim2SenderChips(); updateSim2SenderHint()
    }

    private fun refreshSim2SenderChips() {
        binding.chipGroupSim2Senders.removeAllViews()
        prefs.sim2AllowedSenders.forEach { s ->
            binding.chipGroupSim2Senders.addView(makeRemovableChip(s) {
                prefs.sim2AllowedSenders = prefs.sim2AllowedSenders.toMutableSet().apply { remove(s) }
                refreshSim2SenderChips(); updateSim2SenderHint()
            })
        }
    }

    private fun updateSim2SenderHint() {
        val n = prefs.sim2AllowedSenders.size
        binding.tvSim2SenderHint.text = if (n == 0) "No filter — all senders forwarded" else "$n sender(s) — only these forwarded"
    }

    // ─────────────────────────────────────────────── SIM 2 headers

    private fun addSim2Header() {
        val key = binding.etSim2HeaderKey.text?.toString()?.trim() ?: return
        val value = binding.etSim2HeaderValue.text?.toString()?.trim() ?: return
        if (key.isEmpty()) { binding.etSim2HeaderKey.error = "Required"; return }
        prefs.sim2CustomHeaders = prefs.sim2CustomHeaders.toMutableSet()
            .apply { add(PreferencesManager.encodeKv(key, value)) }
        binding.etSim2HeaderKey.setText(""); binding.etSim2HeaderValue.setText("")
        refreshSim2HeaderChips()
    }

    private fun refreshSim2HeaderChips() {
        binding.chipGroupSim2Headers.removeAllViews()
        prefs.sim2CustomHeaders.forEach { entry ->
            binding.chipGroupSim2Headers.addView(makeRemovableChip(entry.replace(PreferencesManager.KV_SEP, ": ")) {
                prefs.sim2CustomHeaders = prefs.sim2CustomHeaders.toMutableSet().apply { remove(entry) }
                refreshSim2HeaderChips()
            })
        }
    }

    // ─────────────────────────────────────────────── SIM 2 body

    private fun addSim2BodyField() {
        val key = binding.etSim2BodyKey.text?.toString()?.trim() ?: return
        val value = binding.etSim2BodyValue.text?.toString()?.trim() ?: return
        if (key.isEmpty()) { binding.etSim2BodyKey.error = "Required"; return }
        prefs.sim2ExtraBody = prefs.sim2ExtraBody.toMutableSet()
            .apply { add(PreferencesManager.encodeKv(key, value)) }
        binding.etSim2BodyKey.setText(""); binding.etSim2BodyValue.setText("")
        refreshSim2BodyChips()
    }

    private fun refreshSim2BodyChips() {
        binding.chipGroupSim2Body.removeAllViews()
        prefs.sim2ExtraBody.forEach { entry ->
            binding.chipGroupSim2Body.addView(makeRemovableChip(entry.replace(PreferencesManager.KV_SEP, ": ")) {
                prefs.sim2ExtraBody = prefs.sim2ExtraBody.toMutableSet().apply { remove(entry) }
                refreshSim2BodyChips()
            })
        }
    }

    // ─────────────────────────────────────────────── history / syslog

    private fun refreshHistory() {
        val logs = prefs.getLogs()
        binding.tvHistory.text = if (logs.isEmpty()) "No forwarding events yet" else logs.take(100).joinToString("\n\n")
    }

    private fun refreshSyslog() {
        val logs = prefs.getSyslog()
        binding.tvSyslog.text = if (logs.isEmpty()) "No system events yet" else logs.take(200).joinToString("\n")
    }

    // ─────────────────────────────────────────────── helpers

    private fun makeRemovableChip(label: String, onRemove: () -> Unit): Chip {
        return Chip(this).apply {
            text = label
            isCloseIconVisible = true
            setOnCloseIconClickListener { onRemove() }
        }
    }

    // ─────────────────────────────────────────────── battery

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

    // ─────────────────────────────────────────────── permissions

    private fun requestRequiredPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }
}
