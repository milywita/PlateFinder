package com.milywita.platefinder

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.milywita.platefinder.ui.MainScreen
import com.milywita.platefinder.ui.camera.CameraScreen
import com.milywita.platefinder.ui.camera.ImagePreviewScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var capturedImage: File? by mutableStateOf(null)
    private var currentScreen: Screen by mutableStateOf(Screen.Main)
    private var aiResponse: String by mutableStateOf("")
    private var isProcessing: Boolean by mutableStateOf(false)

    private val isTestMode = true // Set to false to use real API

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val galleryLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let { selectedUri ->
                            capturedImage = selectedUri.toFile(context)
                            currentScreen = Screen.Preview
                        }
                    }

                    when (currentScreen) {
                        Screen.Main -> MainScreen(
                            onCameraClick = { currentScreen = Screen.Camera },
                            onGalleryClick = { galleryLauncher.launch("image/*") }
                        )

                        Screen.Camera -> CameraScreen(
                            onImageCaptured = { file ->
                                capturedImage = file
                                currentScreen = Screen.Preview
                            },
                            onError = { errorMessage ->
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            },
                            onGoBack = { currentScreen = Screen.Main }
                        )
                        Screen.Preview -> capturedImage?.let { image ->
                            ImagePreviewScreen(
                                imageFile = image,
                                aiResponse = aiResponse,
                                isProcessing = isProcessing,
                                onRetakePhoto = {
                                    currentScreen = Screen.Camera
                                    aiResponse = ""
                                },
                                onAnalyzePhoto = { file ->
                                    isProcessing = true
                                    analyzeImageWithAI(
                                        context = context,
                                        file = file,
                                        onError = { errorMessage ->
                                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG)
                                                .show()
                                            isProcessing = false
                                        },
                                        onSuccess = { response ->
                                            aiResponse = response
                                            isProcessing = false
                                        }
                                    )
                                },
                                onGoBack = {
                                    currentScreen = Screen.Main
                                    aiResponse = ""
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun Uri.toFile(context: Context): File {
        val inputStream = context.contentResolver.openInputStream(this)
        val file = File(context.cacheDir, "selected_image.jpg")
        inputStream?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private fun getTestResponse(): String {
        return """
            # Homemade Pizza Margherita
            
            ## Ingredients
            - 2 1/4 cups (280g) bread flour
            - 1 tsp instant yeast
            - 1 tsp salt
            - 1 cup (240ml) warm water
            - 2 tbsp olive oil
            - 1 cup (240ml) tomato sauce
            - 8 oz (225g) fresh mozzarella
            - Fresh basil leaves
            - Extra virgin olive oil for drizzling
            
            ## Instructions
            1. Mix flour, yeast, and salt in a large bowl
            2. Add water and oil, knead until smooth (about 10 minutes)
            3. Let dough rise for 1 hour in a warm place
            4. Preheat oven to 450°F (230°C) with pizza stone
            5. Roll out dough and add toppings
            6. Bake for 12-15 minutes until crust is golden
            7. Add fresh basil after baking
            
            ## Additional Information
            **Cooking Time:** 1 hour 30 minutes
            **Difficulty:** Medium
            **Tips:** 
            - Let dough come to room temperature before shaping
            - Use high-quality ingredients for best results
            - For extra crispy crust, brush edges with olive oil
        """.trimIndent()
    }

    private fun analyzeImageWithAI(
        context: Context,
        file: File,
        onError: (String) -> Unit,
        onSuccess: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            if (isTestMode) {
                // Simulate network delay
                delay(1500)
                withContext(Dispatchers.Main) {
                    onSuccess(getTestResponse())
                }
                return@launch
            }

            // Original API implementation
            try {
                Log.d(TAG, "Starting image analysis for file: ${file.absolutePath}")

                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap == null) {
                    throw IllegalStateException("Failed to decode image file")
                }
                Log.d(TAG, "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")

                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = "X"
                )
                Log.d(TAG, "Created GenerativeModel instance")

                val prompt = """
                    You are a professional chef and food recognition expert. Look at this image and:
                    1. Identify the main dish or food item in the image
                    2. Provide a detailed recipe for this dish including:
                       - List of ingredients with measurements
                       - Step-by-step cooking instructions
                       - Estimated cooking time
                       - Difficulty level (Easy/Medium/Hard)
                    3. Add any cooking tips or variations that might be helpful
                    
                    Format your response in clear sections using markdown:
                    # [Dish Name]
                    ## Ingredients
                    [ingredients list]
                    ## Instructions
                    [numbered steps]
                    ## Additional Information
                    [cooking time, difficulty, and tips]
                """.trimIndent()

                Log.d(TAG, "Creating input content with image and prompt")
                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                Log.d(TAG, "Sending request to Gemini API")
                val response = generativeModel.generateContent(inputContent)
                Log.d(TAG, "Received response from Gemini API: $response")

                withContext(Dispatchers.Main) {
                    if (response.text != null) {
                        Log.d(TAG, "Successfully got response text: ${response.text}")
                        onSuccess(response.text!!)
                    } else {
                        val errorMessage = "No response text generated"
                        Log.e(TAG, errorMessage)
                        onError(errorMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during image analysis", e)
                val errorMessage = when (e) {
                    is OutOfMemoryError -> "Image is too large to process"
                    is IllegalStateException -> e.message ?: "Failed to process image"
                    else -> "Error analyzing image: ${e.message}\nCause: ${e.cause?.message}"
                }
                withContext(Dispatchers.Main) {
                    onError(errorMessage)
                }
            }
        }
    }
}

enum class Screen { Main, Camera, Preview }