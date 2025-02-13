object KitchenStaples {
    // Basic seasonings and spices
    private val basicSeasonings = setOf(
        "salt", "pepper", "black pepper", "kosher salt",
        "garlic powder", "onion powder", "dried oregano",
        "dried basil", "ground cumin", "paprika"
    )

    // Common cooking oils
    private val cookingOils = setOf(
        "olive oil", "vegetable oil", "canola oil",
        "cooking oil", "oil", "cooking spray"
    )

    // Basic pantry items
    private val pantryBasics = setOf(
        "water", "flour", "sugar", "brown sugar",
        "baking powder", "baking soda", "vanilla extract"
    )

    // Combined set of all staples
    val allStaples = basicSeasonings + cookingOils + pantryBasics

    // Function to check if an ingredient is a staple
    fun isStaple(ingredient: String): Boolean {
        val normalizedIngredient = ingredient.lowercase().trim()
        return allStaples.any { staple ->
            normalizedIngredient.contains(staple) ||
                    normalizedIngredient.endsWith("to taste") // Catches "salt and pepper to taste" etc.
        }
    }
}