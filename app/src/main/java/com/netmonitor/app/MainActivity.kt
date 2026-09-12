package com.netmonitor.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Degrade gracefully — fragments render "—" when denied.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PrefsRepo(this).let { prefs ->
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                if (prefs.darkMode)
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DashboardDataProvider.init(this)
        ensurePermissions()
        startSpeedService()

        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            buzzTick()
            when (item.itemId) {
                R.id.dashboard -> loadFragment(DashboardFragment())
                R.id.data -> loadFragment(DataFragment())
                R.id.battery -> loadFragment(BatteryFragment())
                R.id.settings -> loadFragment(SettingsFragment())
            }
            true
        }
    }

    private fun buzzTick() {
        if (!PrefsRepo(this).haptics) return
        try {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(30)
            }
        } catch (_: Exception) {
        }
    }

    private fun startSpeedService() {
        try {
            val intent = Intent(this, NetworkSpeedMonitorService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } catch (_: Exception) {
        }
    }

    private fun ensurePermissions() {
        val perms = arrayListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return
        val needsRationale = missing.any {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(it)
        }
        if (needsRationale) {
            AlertDialog.Builder(this)
                .setTitle("Permissions needed")
                .setMessage(
                    "Phone state is used to read carrier name and signal strength. " +
                        "Location is required by Android to read the WiFi SSID on API 29+. " +
                        "You can deny either — the app will show “—” instead."
                )
                .setPositiveButton("Continue") { _, _ ->
                    permissionLauncher.launch(missing.toTypedArray())
                }
                .setNegativeButton("Not now", null)
                .show()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
