package com.silaeva.facecontourdetection

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silaeva.facecontourdetection.ui.theme.FaceContourDetectionTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private val isStartCamera = mutableStateOf(false)
    private var bitmap = mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceContourDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewModel: FaceDetectionViewModel = viewModel()
                    val cameraSelector = remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

                    if (allPermissionsGranted() || isStartCamera.value) {
                        CameraPreview(
                            viewModel = viewModel,
                            cameraSelector = cameraSelector.value,
                            onCapture = { bit, file ->
                                bitmap.value = bit
                                onCapturePhoto(bitmap.value, file = file)
                            },
                            onSwitchCamera = {
                                cameraSelector.value = onSwitchLens(cameraSelector.value)
                            }
                        )
                    } else {
                        requestPermissions()
                    }
                }
            }
        }
    }

    private fun onSwitchLens(cameraSelector: CameraSelector): CameraSelector =
        if (CameraSelector.DEFAULT_FRONT_CAMERA == cameraSelector) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }


    private fun onCapturePhoto(bitmap: Bitmap?, file: File) {
        bitmap?.let { bmp ->
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        }
    }

    private val REQUIRED_PERMISSIONS =
        mutableListOf (
            android.Manifest.permission.CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                isStartCamera.value = true
            }
        }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }
}
