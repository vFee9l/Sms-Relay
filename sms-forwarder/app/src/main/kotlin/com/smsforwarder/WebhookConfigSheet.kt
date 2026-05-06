package com.smsforwarder

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.smsforwarder.databinding.LayoutWebhookSheetBinding

class WebhookConfigSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutWebhookSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: WebhookRepository
    private var editingWebhook: Webhook? = null

    // Working copies (mutable during editing)
    private val senders     = mutableListOf<String>()
    private val headers     = mutableMapOf<String, String>()
    private val bodyFields  = mutableMapOf<String, String>()

    var onSaved: (() -> Unit)? = null

    // ─────────────────────────────────────── lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = LayoutWebhookSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand to full height
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        repo = WebhookRepository(requireContext())

        val wh = editingWebhook
        if (wh != null) {
            // Editing existing
            binding.tvSheetTitle.text = wh.name.ifBlank { "Edit Webhook" }
            binding.btnDelete.visibility = View.VISIBLE
            binding.etName.setText(wh.name)
            binding.etUrl.setText(wh.url)
            binding.etSecret.setText(wh.secret)
            binding.etDeviceId.setText(wh.deviceId)
            binding.etPhone.setText(wh.phone)
            binding.switchDisableSsl.isChecked = wh.disableSsl

            when (wh.simSlot) {
                Webhook.SIM_1 -> binding.radioSim1.isChecked = true
                Webhook.SIM_2 -> binding.radioSim2.isChecked = true
                else          -> binding.radioSimAny.isChecked = true
            }

            senders.addAll(wh.allowedSenders)
            headers.putAll(wh.customHeaders)
            bodyFields.putAll(wh.extraBody)
        } else {
            binding.tvSheetTitle.text = "New Webhook"
            binding.btnDelete.visibility = View.GONE
        }

        refreshSenderChips()
        refreshHeaderChips()
        refreshBodyChips()
        updateSenderHint()

        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────── listeners

    private fun setupListeners() {
        // Senders
        binding.btnAddSender.setOnClickListener { addSender() }
        binding.etSender.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { addSender(); true } else false
        }

        // Headers
        binding.btnAddHeader.setOnClickListener { addHeader() }

        // Body
        binding.btnAddBody.setOnClickListener { addBodyField() }

        // Test
        binding.btnTest.setOnClickListener { testConnection() }

        // Save
        binding.btnSave.setOnClickListener { save() }

        // Delete
        binding.btnDelete.setOnClickListener {
            editingWebhook?.let { wh ->
                repo.delete(wh.id)
                PreferencesManager(requireContext()).addSyslog("[CONFIG] Webhook '${wh.name}' deleted")
                onSaved?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    // ─────────────────────────────────────── senders

    private fun addSender() {
        val s = binding.etSender.text?.toString()?.trim() ?: return
        if (s.isEmpty()) return
        senders.add(s)
        binding.etSender.setText("")
        refreshSenderChips()
        updateSenderHint()
    }

    private fun refreshSenderChips() {
        binding.chipGroupSenders.removeAllViews()
        senders.toList().forEach { s ->
            binding.chipGroupSenders.addView(makeChip(s) {
                senders.remove(s)
                refreshSenderChips()
                updateSenderHint()
            })
        }
    }

    private fun updateSenderHint() {
        binding.tvSenderHint.text = if (senders.isEmpty())
            "No filter — all senders forwarded"
        else
            "${senders.size} sender(s) — only these forwarded"
    }

    // ─────────────────────────────────────── headers

    private fun addHeader() {
        val k = binding.etHeaderKey.text?.toString()?.trim() ?: return
        val v = binding.etHeaderValue.text?.toString()?.trim() ?: return
        if (k.isEmpty()) { binding.etHeaderKey.error = "Required"; return }
        headers[k] = v
        binding.etHeaderKey.setText(""); binding.etHeaderValue.setText("")
        refreshHeaderChips()
    }

    private fun refreshHeaderChips() {
        binding.chipGroupHeaders.removeAllViews()
        headers.toMap().forEach { (k, v) ->
            val label = "$k: $v"
            binding.chipGroupHeaders.addView(makeChip(label) {
                headers.remove(k)
                refreshHeaderChips()
            })
        }
    }

    // ─────────────────────────────────────── body fields

    private fun addBodyField() {
        val k = binding.etBodyKey.text?.toString()?.trim() ?: return
        val v = binding.etBodyValue.text?.toString()?.trim() ?: return
        if (k.isEmpty()) { binding.etBodyKey.error = "Required"; return }
        bodyFields[k] = v
        binding.etBodyKey.setText(""); binding.etBodyValue.setText("")
        refreshBodyChips()
    }

    private fun refreshBodyChips() {
        binding.chipGroupBody.removeAllViews()
        bodyFields.toMap().forEach { (k, v) ->
            val label = "$k: $v"
            binding.chipGroupBody.addView(makeChip(label) {
                bodyFields.remove(k)
                refreshBodyChips()
            })
        }
    }

    // ─────────────────────────────────────── test

    private fun testConnection() {
        val url = binding.etUrl.text?.toString()?.trim() ?: ""
        if (url.isEmpty()) {
            binding.tilUrl.error = "Enter a URL first"
            return
        }
        binding.tilUrl.error = null
        binding.btnTest.isEnabled = false
        binding.tvTestResult.visibility = View.VISIBLE
        binding.tvTestResult.setTextColor(Color.parseColor("#6B7280"))
        binding.tvTestResult.text = "Sending test…"

        WebhookManager.send(
            webhookUrl    = url,
            secret        = binding.etSecret.text?.toString()?.trim() ?: "",
            from          = "TEST",
            message       = "T2-SMS-forwarding test message",
            sentTimestamp = System.currentTimeMillis(),
            sentTo        = binding.etPhone.text?.toString()?.trim() ?: "TEST",
            deviceId      = binding.etDeviceId.text?.toString()?.trim() ?: "",
            customHeaders = HashMap(headers),
            extraBody     = HashMap(bodyFields),
            disableSsl    = binding.switchDisableSsl.isChecked,
            onSuccess = {
                activity?.runOnUiThread {
                    binding.btnTest.isEnabled = true
                    binding.tvTestResult.setTextColor(Color.parseColor("#16A34A"))
                    binding.tvTestResult.text = "✓ Success — webhook received the request"
                }
            },
            onError = { err ->
                activity?.runOnUiThread {
                    binding.btnTest.isEnabled = true
                    binding.tvTestResult.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvTestResult.text = "✗ Failed: $err"
                }
            }
        )
    }

    // ─────────────────────────────────────── save

    private fun save() {
        val url = binding.etUrl.text?.toString()?.trim() ?: ""
        if (url.isEmpty()) {
            binding.tilUrl.error = "Webhook URL is required"
            return
        }
        binding.tilUrl.error = null

        val simSlot = when (binding.radioGroupSim.checkedRadioButtonId) {
            R.id.radioSim1 -> Webhook.SIM_1
            R.id.radioSim2 -> Webhook.SIM_2
            else           -> Webhook.SIM_ANY
        }

        val webhook = (editingWebhook ?: Webhook()).copy(
            name           = binding.etName.text?.toString()?.trim().takeIf { !it.isNullOrBlank() } ?: "Webhook",
            url            = url,
            secret         = binding.etSecret.text?.toString()?.trim() ?: "",
            deviceId       = binding.etDeviceId.text?.toString()?.trim() ?: "",
            phone          = binding.etPhone.text?.toString()?.trim() ?: "",
            simSlot        = simSlot,
            allowedSenders = senders.toList(),
            customHeaders  = HashMap(headers),
            extraBody      = HashMap(bodyFields),
            disableSsl     = binding.switchDisableSsl.isChecked,
            enabled        = editingWebhook?.enabled ?: true
        )

        repo.save(webhook)
        PreferencesManager(requireContext()).addSyslog("[CONFIG] Webhook '${webhook.name}' saved — url=$url  sim=${webhook.simLabel}")

        Toast.makeText(requireContext(), "Webhook saved", Toast.LENGTH_SHORT).show()
        onSaved?.invoke()
        dismissAllowingStateLoss()
    }

    // ─────────────────────────────────────── helpers

    private fun makeChip(label: String, onRemove: () -> Unit): Chip =
        Chip(requireContext()).apply {
            text = label
            isCloseIconVisible = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = resources.displayMetrics.widthPixels / 2
            setOnCloseIconClickListener { onRemove() }
        }

    // ─────────────────────────────────────── factory

    companion object {
        fun newInstance(existing: Webhook? = null): WebhookConfigSheet =
            WebhookConfigSheet().also { it.editingWebhook = existing }
    }
}
