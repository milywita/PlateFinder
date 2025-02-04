package com.milywita.platefinder.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import java.io.File

@Composable
fun ImagePreviewScreen(
    imageFile: File,
    onRetakePhoto: () -> Unit,
    onPhotoAccepted: (File) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Load and display the image
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        bitmap?.let {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Fit
            )
        }

        // Buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onRetakePhoto) {
                Text("Retake")
            }

            Button(onClick = { onPhotoAccepted(imageFile) }) {
                Text("Use Photo")
            }
        }
    }
}