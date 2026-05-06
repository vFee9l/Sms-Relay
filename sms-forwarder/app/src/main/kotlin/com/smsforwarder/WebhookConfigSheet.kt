package com.smsforwarder

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    /** Working copy of custom headers (key → value) */
    private val headers = mutableMapOf<String, String>()

    var onSaved: (() -> Unit)? = null

    // ─────────────────────────── lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = LayoutWebhookSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Always open fully expanded
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        repo = WebhookRepository(requireContext())

        val wh = editingWebhook
        if (wh != null) {
            binding.tvSheetTitle.text    = wh.name.ifBlank { "Edit Webhook" }
            binding.btnDelete.visibility = View.VISIBLE
            binding.etName.setText(wh.name)
            binding.etUrl.setText(wh.url)
            binding.etBody.setText(wh.bodyTemplate)
            binding.switchDisableSsl.isChecked = wh.disableSsl
            binding.toggleGroupSim.check(
                if (wh.simSlot == Webhook.SIM_2) R.id.btnSim2 else R.id.btnSim1
            )
            headers.putAll(wh.customHeaders)
        } else {
            binding.tvSheetTitle.text    = "New Webhook"
            binding.btnDelete.visibility = View.GONE
            binding.etBody.setText(Webhook.DEFAULT_BODY)
            binding.toggleGroupSim.check(R.id.btnSim1)
        }

        refreshHeaderChips()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────── listeners

    private fun setupListeners() {
        binding.btnAddHeader.setOnClickListener { addHeader() }
        binding.btnTest.setOnClickListener      { testConnection() }
        binding.btnSave.setOnClickListener      { save() }

        binding.btnDelete.setOnClickListener {
            editingWebhook?.let { wh ->
                repo.delete(wh.id)
                PreferencesManager(requireContext()).addSyslog("[CONFIG] Webhook '${wh.name}' deleted")
                Toast.makeText(requireContext(), "Webhook deleted", Toast.LENGTH_SHORT).show()
                onSaved?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    // ─────────────────────────── headers

    private fun addHeader() {
        val k = binding.etHeaderKey.text?.toString()?.trim() ?: return
        val v = binding.etHeaderValue.text?.toString()?.trim() ?: return
        if (k.isEmpty()) { binding.etHeaderKey.error = "Name required"; return }
        binding.etHeaderKey.error = null
        headers[k] = v
        binding.etHeaderKey.setText("")
        binding.etHeaderValue.setText("")
        refreshHeaderChips()
    }

    private fun refreshHeaderChips() {
        binding.chipGroupHeaders.removeAllViews()
        headers.toMap().forEach { (k, v) ->
            binding.chipGroupHeaders.addView(
                Chip(requireContext()).apply {
                    text = "$k: $v"
                    isCloseIconVisible = true
                    setOnCloseIconClickListener { headers.remove(k); refreshHeaderChips() }
                }
            )
        }
    }

    // ─────────────────────────── test

    private fun testConnection() {
        val url = binding.etUrl.text?.toString()?.trim() ?: ""
        if (url.isEmpty()) { binding.tilUrl.error = "Enter URL first"; return }
        binding.tilUrl.error = null

        binding.btnTest.isEnabled = false
        binding.tvTestResult.visibility = View.VISIBLE
        binding.tvTestResult.setTextColor(Color.parseColor("#6B7280"))
        binding.tvTestResult.text = "Sending test…"

        val bodyTemplate = binding.etBody.text?.toString() ?: Webhook.DEFAULT_BODY

        WebhookManager.send(
            webhookUrl    = url,
            bodyTemplate  = bodyTemplate,
            from          = "TEST_SENDER",
            message       = "T2-SMS-forwarding test message",
            sentTimestamp = System.currentTimeMillis(),
            customHeaders = HashMap(headers),
            disableSsl    = binding.switchDisableSsl.isChecked,
            onSuccess = {
                activity?.runOnUiThread {
                    binding.btnTest.isEnabled = true
                    binding.tvTestResult.setTextColor(Color.parseColor("#16A34A"))
                    binding.tvTestResult.text = "✓ Webhook received the request"
                }
            },
            onError = { err ->
                activity?.runOnUiThread {
                    binding.btnTest.isEnabled = true
                    binding.tvTestResult.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvTestResult.text = "✗ $err"
                }
            }
        )
    }

    // ─────────────────────────── save

    private fun save() {
        val url = binding.etUrl.text?.toString()?.trim() ?: ""
        if (url.isEmpty()) { binding.tilUrl.error = "URL is required"; return }
        binding.tilUrl.error = null

        val simSlot = if (binding.toggleGroupSim.checkedButtonId == R.id.btnSim2)
            Webhook.SIM_2 else Webhook.SIM_1

        val name = binding.etName.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
            ?: url.removePrefix("https://").removePrefix("http://").substringBefore("/")

        val body = binding.etBody.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
            ?: Webhook.DEFAULT_BODY

        val webhook = (editingWebhook ?: Webhook()).copy(
            name          = name,
            url           = url,
            simSlot       = simSlot,
            bodyTemplate  = body,
            customHeaders = HashMap(headers),
            disableSsl    = binding.switchDisableSsl.isChecked,
            enabled       = editingWebhook?.enabled ?: true
        )

        repo.save(webhook)
        PreferencesManager(requireContext())
            .addSyslog("[CONFIG] Webhook '$name' saved — url=$url  sim=${webhook.simLabel}")

        Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
        onSaved?.invoke()
        dismissAllowingStateLoss()
    }

    companion object {
        fun newInstance(existing: Webhook? = null) =
            WebhookConfigSheet().also { it.editingWebhook = existing }
    }
}
