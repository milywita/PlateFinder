package com.milywita.platefinder.data

data class Recipe(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val content: String,
    val difficulty: String,
    val timestamp: Long = System.currentTimeMillis()
)