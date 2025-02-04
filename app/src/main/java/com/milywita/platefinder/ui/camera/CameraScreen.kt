// CameraScreen.kt
package com.milywita.platefinder.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(
    onImageCaptured: (File) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraPreview(
            onImageCaptured = onImageCaptured,
            onError = onError
        )
    } else {
        // Display fallback UI if permission is not granted
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission not granted")
        }
    }
}

@Composable
private fun CameraPreview(
    onImageCaptured: (File) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Declare the capturing state here
    var isCapturing by remember { mutableStateOf(false) }

    val preview = remember { Preview.Builder().build() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = remember { CameraSelector.DEFAULT_BACK_CAMERA }

    // Create output directory
    val outputDirectory = remember { getOutputDirectory(context) }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(previewView) {
        val cameraProvider = context.getCameraProvider()
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            onError(exc.message ?: "Error setting up camera")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {
                // Prevent multiple clicks while a capture is in progress
                if (!isCapturing) {
                    takePhoto(
                        imageCapture = imageCapture,
                        outputDirectory = outputDirectory,
                        executor = executor,
                        onImageCaptured = onImageCaptured,
                        onError = onError,
                        onCapturingChanged = { isCapturing = it }
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(if (isCapturing) "Capturing..." else "Take Photo")
        }
    }
}

private fun getOutputDirectory(context: Context): File {
    val mediaDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PlateFinderPhotos").apply {
            mkdirs()
        }
    } else {
        File(context.getExternalFilesDir(null), "PlateFinderPhotos").apply {
            mkdirs()
        }
    }
    return if (mediaDir.exists()) mediaDir else context.filesDir
}


private suspend fun Context.getCameraProvider(): ProcessCameraProvider {
    return suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future[1, TimeUnit.SECONDS]
                continuation.resume(provider)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
}



private fun takePhoto(
    filenameFormat: String = "yyyy-MM-dd-HH-mm-ss-SSS",
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    onCapturingChanged: (Boolean) -> Unit // Callback to update capturing state
) {
    // Set capturing state to true before taking the photo
    onCapturingChanged(true)

    val photoFile = File(
        outputDirectory,
        SimpleDateFormat(filenameFormat, Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                onCapturingChanged(false)
                Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
                onError(exc.message ?: "Photo capture failed")
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onCapturingChanged(false)
                Log.d("CameraScreen", "Photo capture succeeded: ${photoFile.absolutePath}")
                onImageCaptured(photoFile)
            }
        }
    )
}
