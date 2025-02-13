package com.milywita.platefinder.utils

import com.milywita.platefinder.data.OrderIngredient
import com.milywita.platefinder.data.OrderRequest
import com.milywita.platefinder.data.Recipe

class RecipeParser {
    /**
     * Parses markdown content and creates a Recipe object
     */
    fun parseMarkdownToRecipe(markdown: String): Recipe {
        val sections = markdown.split("## ")
        val title = sections[0].substringAfter("# ").trim()
        val difficulty = sections.find { it.contains("**Difficulty:**") }
            ?.let { section ->
                section.lines()
                    .find { it.contains("**Difficulty:**") }
                    ?.substringAfter("**Difficulty:**")
                    ?.trim()
            } ?: "Medium"

        return Recipe(
            title = title,
            content = markdown,
            difficulty = difficulty
        )
    }

    /**
     * Extracts ingredients from markdown for ordering purposes
     */
    fun extractIngredientsForOrder(markdown: String): OrderRequest {
        val recipeId = System.currentTimeMillis().toString()
        val sections = markdown.split("## ")

        val ingredients = sections.find { it.startsWith("Ingredients") }
            ?.lines()
            ?.filter { it.startsWith("- ") }
            ?.mapNotNull { line ->
                val ingredient = parseIngredientLine(line.removePrefix("- ").trim())
                // Only include non-staple ingredients
                if (ingredient != null && !KitchenStaples.isStaple(ingredient.name)) {
                    ingredient
                } else null
            } ?: emptyList()

        return OrderRequest(recipeId, ingredients)
    }

    private fun parseIngredientLine(line: String): OrderIngredient? {
        // Handle common measurement patterns
        val patterns = listOf(
            // Pattern: "2 tablespoons olive oil"
            Regex("^(\\d+(?:/\\d+)?(?:\\.\\d+)?)\\s+(\\w+)\\s+(.+)$"),
            // Pattern: "1 (8-ounce) steak"
            Regex("^(\\d+)\\s+\\((\\d+(?:-\\w+)?)\\)\\s+(.+)$"),
            // Pattern: "1 large onion"
            Regex("^(\\d+)\\s+(\\w+)\\s+(.+)$")
        )

        for (pattern in patterns) {
            pattern.find(line)?.let { match ->
                val (quantity, unit, name) = match.destructured
                return OrderIngredient(
                    quantity = quantity.trim(),
                    unit = unit.trim(),
                    name = name.trim()
                )
            }
        }

        // Fallback for ingredients without clear measurements
        return if (line.isNotEmpty()) {
            OrderIngredient(
                quantity = "1",
                unit = "piece",
                name = line
            )
        } else null
    }
}