package com.smsforwarder

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.smsforwarder.databinding.ActivityMainBinding
import com.smsforwarder.databinding.ItemWebhookCardBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var repo: WebhookRepository

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshHistory()
            refreshSyslog()
            updateStatusBanner()
        }
    }

    // ─────────────────────────────────────────── lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = PreferencesManager(this)
        repo  = WebhookRepository(this)

        prefs.addSyslog("[APP] MainActivity started")

        setupTabs()
        setupListeners()
        updateStatusBanner()
        refreshWebhookCards()
        refreshHistory()
        refreshSyslog()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusBanner()
        refreshWebhookCards()
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

    // ─────────────────────────────────────────── tabs

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.viewWebhooks.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                binding.viewHistory.visibility  = if (tab.position == 1) View.VISIBLE else View.GONE
                binding.viewSyslog.visibility   = if (tab.position == 2) View.VISIBLE else View.GONE
                if (tab.position == 1) refreshHistory()
                if (tab.position == 2) refreshSyslog()
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    // ─────────────────────────────────────────── listeners

    private fun setupListeners() {
        // Service toggle
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startForwarding() else stopForwarding()
        }

        // FAB → open blank config sheet
        binding.fab.setOnClickListener {
            openSheet(null)
        }

        // History/Syslog clear
        binding.btnClearHistory.setOnClickListener { prefs.clearLogs(); refreshHistory() }
        binding.btnClearSyslog.setOnClickListener  { prefs.clearSyslog(); refreshSyslog() }
    }

    // ─────────────────────────────────────────── webhook cards

    private fun refreshWebhookCards() {
        val webhooks = repo.getAll()
        binding.viewEmpty.visibility      = if (webhooks.isEmpty()) View.VISIBLE else View.GONE
        binding.webhooksContainer.removeAllViews()

        for (wh in webhooks) {
            val cardBinding = ItemWebhookCardBinding.inflate(layoutInflater, binding.webhooksContainer, false)

            // Populate card
            cardBinding.tvWebhookName.text = wh.name.ifBlank { "Unnamed Webhook" }
            cardBinding.tvWebhookUrl.text  = wh.url.ifBlank  { "No URL configured" }
            cardBinding.switchWebhookEnabled.isChecked = wh.enabled

            // SIM badge
            val (badgeText, badgeColor) = when (wh.simSlot) {
                Webhook.SIM_1 -> "SIM 1" to getColor(R.color.sim1_color)
                Webhook.SIM_2 -> "SIM 2" to getColor(R.color.sim2_color)
                else          -> "Any SIM" to getColor(R.color.sim_any_color)
            }
            cardBinding.tvSimBadge.text = badgeText
            cardBinding.tvSimBadge.backgroundTintList =
                android.content.res.ColorStateList.valueOf(badgeColor)

            // Left stripe color
            cardBinding.simStripe.setBackgroundColor(badgeColor)

            // Enable toggle
            cardBinding.switchWebhookEnabled.setOnCheckedChangeListener { _, checked ->
                val updated = wh.copy(enabled = checked)
                repo.save(updated)
                prefs.addSyslog("[CONFIG] Webhook '${wh.name}' ${if (checked) "enabled" else "disabled"}")
            }

            // Click card → open edit sheet
            cardBinding.cardRoot.setOnClickListener { openSheet(wh) }

            binding.webhooksContainer.addView(cardBinding.root)
        }
    }

    private fun openSheet(webhook: Webhook?) {
        val sheet = WebhookConfigSheet.newInstance(webhook)
        sheet.onSaved = {
            refreshWebhookCards()
            refreshSyslog()
        }
        sheet.show(supportFragmentManager, "webhook_config")
    }

    // ─────────────────────────────────────────── service control

    private fun startForwarding() {
        val webhooks = repo.getAll()
        if (webhooks.isEmpty() || webhooks.none { it.url.isNotBlank() }) {
            android.widget.Toast.makeText(this, "Add at least one webhook first", android.widget.Toast.LENGTH_LONG).show()
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

        // Detach listener while syncing state
        binding.switchService.setOnCheckedChangeListener(null)
        binding.switchService.isChecked = running
        binding.switchService.setOnCheckedChangeListener { _, checked ->
            if (checked) startForwarding() else stopForwarding()
        }

        if (running) {
            val count = repo.getAll().count { it.enabled && it.url.isNotBlank() }
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_green)
            binding.tvServiceStatus.text    = "Service Running"
            binding.tvStatusSubtitle.text   = "$count active webhook${if (count != 1) "s" else ""}"
        } else {
            binding.statusIndicator.setBackgroundResource(R.drawable.circle_red)
            binding.tvServiceStatus.text    = "Service Stopped"
            binding.tvStatusSubtitle.text   = "Toggle to start forwarding"
        }
    }

    // ─────────────────────────────────────────── history / syslog

    private fun refreshHistory() {
        val logs = prefs.getLogs()
        binding.tvHistory.text = if (logs.isEmpty()) "No forwarding events yet"
        else logs.take(100).joinToString("\n\n")
    }

    private fun refreshSyslog() {
        val logs = prefs.getSyslog()
        binding.tvSyslog.text = if (logs.isEmpty()) "No system events yet"
        else logs.take(200).joinToString("\n")
    }

    // ─────────────────────────────────────────── permissions

    private fun requestPermissions() {
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
