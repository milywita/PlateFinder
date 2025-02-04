package com.milywita.platefinder

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.milywita.platefinder.ui.camera.CameraScreen
import com.milywita.platefinder.ui.camera.ImagePreviewScreen
import com.milywita.platefinder.ui.theme.PlateFinderTheme

import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlateFinderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var capturedImage by remember { mutableStateOf<File?>(null) }

                    if (capturedImage != null) {
                        ImagePreviewScreen(
                            imageFile = capturedImage!!,
                            onRetakePhoto = { capturedImage = null },
                            onPhotoAccepted = { file ->
                                // Here we'll later add the AI processing
                                Log.d("MainActivity", "Processing image: ${file.absolutePath}")
                                Toast.makeText(
                                    this@MainActivity,
                                    "Processing image...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    } else {
                        CameraScreen(
                            onImageCaptured = { file ->
                                Log.d("MainActivity", "Image captured: ${file.absolutePath}")
                                capturedImage = file
                            },
                            onError = { error ->
                                Log.e("MainActivity", "Camera error: $error")
                                Toast.makeText(
                                    this@MainActivity,
                                    "Error: $error",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }
}