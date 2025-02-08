package com.milywita.platefinder.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
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
import getCameraProvider
import getOutputDirectory
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    onImageCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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
        Box(modifier = Modifier.fillMaxSize()) {
            var previewView by remember { mutableStateOf<PreviewView?>(null) }
            var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
            var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

            DisposableEffect(Unit) {
                onDispose {
                    try {
                        cameraProvider?.unbindAll()
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error unbinding camera uses cases", e)
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_START
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(previewView) {
                previewView?.let { view ->
                    scope.launch {
                        try {
                            val provider = context.getCameraProvider()
                            cameraProvider = provider

                            // Force portrait mode by setting rotation to 0
                            val preview = Preview.Builder()
                                .setTargetRotation(Surface.ROTATION_0)
                                .build()
                                .also {
                                    it.setSurfaceProvider(view.surfaceProvider)
                                }

                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .setTargetRotation(Surface.ROTATION_0)
                                .build()

                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                .build()

                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (exc: Exception) {
                                Log.e("CameraScreen", "Use case binding failed", exc)
                                onError("Failed to bind camera: ${exc.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Camera setup failed", e)
                            onError(e.localizedMessage ?: "Failed to set up camera")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        takePhoto(
                            imageCapture = imageCapture,
                            outputDirectory = getOutputDirectory(context),
                            executor = ContextCompat.getMainExecutor(context),
                            onImageCaptured = onImageCaptured,
                            onError = onError
                        )
                    }
                ) {
                    Text("Take Photo")
                }
            }

            Button(
                onClick = onGoBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text("Go Back")
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission not granted")
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture?,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (File) -> Unit,
    onError: (String) -> Unit
) {
    imageCapture?.let { capture ->
        val photoFile = File(
            outputDirectory,
            "IMG_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        try {
            capture.takePicture(
                outputOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        onImageCaptured(photoFile)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e("CameraScreen", "Photo capture failed", exc)
                        onError("Failed to capture photo: ${exc.message}")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("CameraScreen", "Taking photo failed", e)
            onError("Failed to take photo: ${e.message}")
        }
    } ?: onError("Camera not initialized")
}