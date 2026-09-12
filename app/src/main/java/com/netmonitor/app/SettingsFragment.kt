package com.netmonitor.app

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.frag_settings, container, false)
        val prefs = PrefsRepo(requireContext())

        val darkToggle = v.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        darkToggle.isChecked = prefs.darkMode
        darkToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.darkMode = isChecked
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked)
                    AppCompatDelegate.MODE_NIGHT_YES
                else
                    AppCompatDelegate.MODE_NIGHT_NO
            )
            requireActivity().recreate()
        }

        val showSpeed = v.findViewById<SwitchMaterial>(R.id.switchShowSpeed)
        showSpeed?.isChecked = prefs.showSpeed
        showSpeed?.setOnCheckedChangeListener { _, isChecked ->
            prefs.showSpeed = isChecked
        }

        val haptics = v.findViewById<SwitchMaterial>(R.id.switchHaptics)
        haptics?.isChecked = prefs.haptics
        haptics?.setOnCheckedChangeListener { _, isChecked ->
            prefs.haptics = isChecked
        }

        val txtBilling = v.findViewById<TextView>(R.id.txtBillingDay)
        val rowBilling = v.findViewById<View>(R.id.rowBillingDay)
        fun renderBilling() {
            txtBilling?.text = "Day ${prefs.billingResetDay}"
        }
        renderBilling()
        rowBilling?.setOnClickListener {
            val input = EditText(requireContext())
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.setText(prefs.billingResetDay.toString())
            AlertDialog.Builder(requireContext())
                .setTitle("Billing reset day (1–28)")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val day = input.text.toString().toIntOrNull()?.coerceIn(1, 28) ?: 1
                    prefs.billingResetDay = day
                    renderBilling()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        val txtLimit = v.findViewById<TextView>(R.id.txtDataLimit)
        val rowLimit = v.findViewById<View>(R.id.rowDataLimit)
        fun renderLimit() {
            txtLimit?.text = if (prefs.dataLimitMb > 0) "${prefs.dataLimitMb} MB" else "Not set"
        }
        renderLimit()
        rowLimit?.setOnClickListener {
            val input = EditText(requireContext())
            input.inputType = InputType.TYPE_CLASS_NUMBER
            if (prefs.dataLimitMb > 0) input.setText(prefs.dataLimitMb.toString())
            input.hint = "MB"
            AlertDialog.Builder(requireContext())
                .setTitle("Monthly data limit (MB)")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    prefs.dataLimitMb = input.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    renderLimit()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return v
    }
}
