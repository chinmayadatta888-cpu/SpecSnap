package com.specsnap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun RequestCameraPermission() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionWasDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        permissionWasDenied = !granted
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                hasCameraPermission = granted
                if (granted) permissionWasDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasCameraPermission) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Camera permission granted. Live preview is added in the next step.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        CameraPermissionPrompt(
            permissionWasDenied = permissionWasDenied,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(settingsIntent)
            },
        )
    }
}

@Composable
private fun CameraPermissionPrompt(
    permissionWasDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (permissionWasDenied) {
                    "Camera access was denied"
                } else {
                    "Camera access is needed"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Allow SpecSnap to use the camera so you can scan electronic components.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onRequestPermission) {
                Text(if (permissionWasDenied) "Try again" else "Allow camera access")
            }
            TextButton(onClick = onOpenSettings) {
                Text("Open app settings")
            }
        }
    }
}
