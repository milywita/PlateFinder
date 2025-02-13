package com.milywita.platefinder.data

import kotlinx.serialization.Serializable

@Serializable
data class OrderIngredient(
    val quantity: String,
    val unit: String,
    val name: String
)

@Serializable
data class OrderRequest(
    val recipeId: String,
    val ingredients: List<OrderIngredient>
)