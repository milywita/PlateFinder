package com.milywita.platefinder.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecipeRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recipes", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    init {
        loadRecipes()
    }

    private fun loadRecipes() {
        val recipesJson = prefs.getString("saved_recipes", "[]")
        val type = object : TypeToken<List<Recipe>>() {}.type
        val loadedRecipes = gson.fromJson<List<Recipe>>(recipesJson, type) ?: emptyList()
        _recipes.value = loadedRecipes.sortedByDescending { it.timestamp }
    }

    fun saveRecipe(title: String, content: String, difficulty: String) {
        val recipe = Recipe(
            title = title,
            content = content,
            difficulty = difficulty
        )
        val updatedRecipes = _recipes.value.toMutableList().apply {
            add(0, recipe)
        }
        _recipes.value = updatedRecipes
        saveToPrefs(updatedRecipes)
    }

    private fun saveToPrefs(recipes: List<Recipe>) {
        val recipesJson = gson.toJson(recipes)
        prefs.edit().putString("saved_recipes", recipesJson).apply()
    }

    fun deleteRecipe(recipe: Recipe) {
        val updatedRecipes = _recipes.value.toMutableList().apply {
            remove(recipe)
        }
        _recipes.value = updatedRecipes
        saveToPrefs(updatedRecipes)
    }
}