package com.galaxy.ring

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.galaxy.ring.health.PermissionsRationaleActivity
import com.galaxy.ring.ui.screens.MainScreen
import com.galaxy.ring.ui.theme.GalaxyRingTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var hasHealthPermissions by mutableStateOf(false)

    // Request permissions for Health Connect
    private val requestHealthPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        lifecycleScope.launch {
            checkHealthPermissions()
        }
    }

    // Request permissions for Bluetooth LE scanning & connection
    private val requestBlePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Bluetooth permission needed to scan and connect with Galaxy Ring",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkHealthPermissions()

        setContent {
            GalaxyRingTheme {
                MainScreen(
                    onRequestHealthPermissions = { requestHealthPermissions() },
                    onRequestBlePermissions = { requestBlePermissions() },
                    onOpenRationale = {
                        startActivity(Intent(this, PermissionsRationaleActivity::class.java))
                    },
                    hasHealthPermissions = hasHealthPermissions
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkHealthPermissions()
    }

    private fun checkHealthPermissions() {
        lifecycleScope.launch {
            hasHealthPermissions = GalaxyRingApp.instance.healthConnectWriter.hasAllPermissions()
        }
    }

    private fun requestHealthPermissions() {
        val healthWriter = GalaxyRingApp.instance.healthConnectWriter
        if (healthWriter.isHealthConnectAvailable()) {
            requestHealthPermissionsLauncher.launch(healthWriter.requiredPermissions)
        } else {
            Toast.makeText(
                this,
                "Health Connect is not installed or available on this device",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestBlePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            requestBlePermissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}
