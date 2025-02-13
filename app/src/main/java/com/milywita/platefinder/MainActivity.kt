package com.milywita.platefinder

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.milywita.platefinder.data.Recipe
import com.milywita.platefinder.data.RecipeRepository
import com.milywita.platefinder.ui.MainScreen
import com.milywita.platefinder.ui.RecipeDetailScreen
import com.milywita.platefinder.ui.SavedRecipesScreen
import com.milywita.platefinder.ui.camera.CameraScreen
import com.milywita.platefinder.ui.camera.ImagePreviewScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Added new enum entry for RecipeDetail
enum class Screen { Main, Camera, Preview, SavedRecipes, RecipeDetail }

class MainActivity : ComponentActivity() {

    private var capturedImage: File? by mutableStateOf(null)
    private var currentScreen: Screen by mutableStateOf(Screen.Main)
    private var aiResponse: String by mutableStateOf("")
    private var isProcessing: Boolean by mutableStateOf(false)
    private val isTestMode = false // false to API
    private lateinit var recipeRepository: RecipeRepository
    private val recipes = mutableStateOf(emptyList<Recipe>())


    private var selectedRecipe: Recipe? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipeRepository = RecipeRepository(this)

        lifecycleScope.launch {
            recipeRepository.recipes.collect { recipeList ->
                recipes.value = recipeList
            }
        }

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
                            onGalleryClick = { galleryLauncher.launch("image/*") },
                            onSavedRecipesClick = { currentScreen = Screen.SavedRecipes }
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
                                        },
                                        onGoBack = {
                                            currentScreen = Screen.Main
                                            aiResponse = ""
                                        },
                                        recipeRepository = recipeRepository
                                    )
                                },
                                onGoBack = {
                                    currentScreen = Screen.Main
                                    aiResponse = ""
                                },
                                recipeRepository = recipeRepository
                            )
                        }
                        Screen.SavedRecipes -> SavedRecipesScreen(
                            recipes = recipes.value,
                            onBackClick = { currentScreen = Screen.Main },
                            onRecipeClick = { recipe ->
                                selectedRecipe = recipe
                                currentScreen = Screen.RecipeDetail
                            },
                            onDeleteRecipe = { recipe ->
                                recipeRepository.deleteRecipe(recipe)
                                Toast.makeText(
                                    applicationContext,
                                    "Recipe deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        Screen.RecipeDetail -> {
                            selectedRecipe?.let { recipe ->
                                RecipeDetailScreen(
                                    recipe = recipe,
                                    onBackClick = { currentScreen = Screen.SavedRecipes }
                                )
                            }
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
        onSuccess: (String) -> Unit,
        onGoBack: () -> Unit,
        recipeRepository: RecipeRepository
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            if (isTestMode) {
                delay(1500)
                withContext(Dispatchers.Main) {
                    onSuccess(getTestResponse())
                }
                return@launch
            }

            try {
                Log.d(TAG, "Starting image analysis for file: ${file.absolutePath}")
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: throw IllegalStateException("Failed to decode image file")
                Log.d(TAG, "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = "x"
                )
                val prompt = """
                    You are a professional chef and food recognition expert. Analyze the image and provide a recipe using these guidelines:
                
                    1. For the ingredients section:
                       - List main ingredients with exact measurements
                       - Put basic seasonings (salt, pepper, common spices) at the end with "to taste"
                       - Assume standard cooking oils are available
                       - Format each ingredient as: "- [quantity] [unit] [ingredient name]"
                    
                    Example format:
                    # [Recipe Name]
                    
                    ## Ingredients
                    - 2 cups flour
                    - 1 pound chicken breast
                    - 3 large eggs
                    - 2 cups heavy cream
                    Basic seasonings (assumed available):
                    - Salt and pepper to taste
                    - Olive oil for cooking
                    - Common spices as needed
                    
                    ## Instructions
                    [numbered steps...]
                    
                    ## Details
                    - Cooking time: [duration]
                    - Difficulty: [Easy/Medium/Hard]
                    - Tips: [cooking tips]""".trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                Log.d("AI_RESPONSE", "Received AI response: ${response.text}")
                withContext(Dispatchers.Main) {
                    if (response.text != null) {
                        onSuccess(response.text!!)
                    } else {
                        onError("No response text generated")
                    }
                }
            } catch (e: Exception) {
                Log.e("AI_RESPONSE", "Error generating AI response", e)
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