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
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
        updateStatusBanner()
        refreshWebhookCards()
        refreshHistory()
        refreshSyslog()
        requestRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusBanner()
        refreshWebhookCards()
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
                binding.viewWebhooks.visibility = if (t.position == 0) View.VISIBLE else View.GONE
                binding.viewHistory.visibility  = if (t.position == 1) View.VISIBLE else View.GONE
                binding.viewSyslog.visibility   = if (t.position == 2) View.VISIBLE else View.GONE
                if (t.position == 1) refreshHistory()
                if (t.position == 2) refreshSyslog()
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
        binding.btnClearHistory.setOnClickListener { prefs.clearLogs(); refreshHistory() }
        binding.btnClearSyslog.setOnClickListener  { prefs.clearSyslog(); refreshSyslog() }
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

            // SIM badge + stripe
            val color = if (wh.simSlot == Webhook.SIM_2)
                getColor(R.color.sim2_color) else getColor(R.color.sim1_color)
            cb.tvSimBadge.text = wh.simLabel
            cb.tvSimBadge.backgroundTintList = ColorStateList.valueOf(color)
            cb.simStripe.setBackgroundColor(color)

            // Active label text
            cb.switchWebhookEnabled.isChecked = wh.enabled
            cb.tvActiveLabel.text = if (wh.enabled) "Active" else "Inactive"
            cb.tvActiveLabel.setTextColor(
                if (wh.enabled) getColor(R.color.green) else getColor(R.color.text_secondary)
            )

            cb.switchWebhookEnabled.setOnCheckedChangeListener { _, checked ->
                repo.save(wh.copy(enabled = checked))
                cb.tvActiveLabel.text = if (checked) "Active" else "Inactive"
                cb.tvActiveLabel.setTextColor(
                    if (checked) getColor(R.color.green) else getColor(R.color.text_secondary)
                )
                prefs.addSyslog("[CONFIG] '${wh.name}' ${if (checked) "enabled" else "disabled"}")
                updateStatusBanner()
            }

            // Test button
            cb.btnCardTest.setOnClickListener {
                if (wh.url.isBlank()) {
                    Toast.makeText(this, "No URL configured", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
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

            // Edit button
            cb.btnCardEdit.setOnClickListener { openSheet(wh) }

            binding.webhooksContainer.addView(cb.root)
        }
    }

    private fun openSheet(webhook: Webhook?) {
        val sheet = WebhookConfigSheet.newInstance(webhook)
        sheet.onSaved = { refreshWebhookCards(); refreshSyslog(); updateStatusBanner() }
        sheet.show(supportFragmentManager, "webhook_sheet")
    }

    // ─────────────────────────── service

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

    private fun isServiceRunning() =
        @Suppress("DEPRECATION")
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ForwarderService::class.java.name }

    private fun updateStatusBanner() {
        val running = isServiceRunning()
        binding.switchService.setOnCheckedChangeListener(null)
        binding.switchService.isChecked = running
        binding.switchService.setOnCheckedChangeListener { _, c -> if (c) startForwarding() else stopForwarding() }

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
            if (logs.isEmpty()) "No forwarding events yet" else logs.take(100).joinToString("\n\n")
    }

    private fun refreshSyslog() {
        val logs = prefs.getSyslog()
        binding.tvSyslog.text =
            if (logs.isEmpty()) "No system events yet" else logs.take(200).joinToString("\n")
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
