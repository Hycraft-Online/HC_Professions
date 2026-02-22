package com.hcprofessions.models;

public record RecipeGate(
    String recipeOutputId,
    Profession requiredProfession,
    int requiredLevel,
    int professionXpGranted,
    boolean enabled,
    String ingredientsJson,
    int timeSeconds,
    int learnCost,
    String benchCategory
) {}
