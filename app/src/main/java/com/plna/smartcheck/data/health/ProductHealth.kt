package com.plna.smartcheck.data.health

import androidx.compose.ui.graphics.Color

/**
 * Оценка пользы товара (локальный «умный» классификатор по названию).
 * Работает офлайн, без внешних API.
 */
enum class ProductHealth(val code: String) {
    HEALTHY("healthy"),
    HARMFUL("harmful"),
    NEUTRAL("neutral"),
    ;

    companion object {
        fun fromCode(code: String?): ProductHealth =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NEUTRAL
    }
}

object ProductHealthColors {
    val Healthy = Color(0xFF2E7D32)
    val HealthyBg = Color(0xFFE8F5E9)
    val Harmful = Color(0xFFC62828)
    val HarmfulBg = Color(0xFFFFEBEE)
    val Neutral = Color(0xFF546E7A)
    val NeutralBg = Color(0xFFECEFF1)
}

/**
 * Простая эвристика по русским/белорусским названиям с чека.
 * Пепси/чипсы → вредно, яблоко/банан/молоко → полезно.
 */
object ProductHealthAnalyzer {

    private val harmful = listOf(
        "пепси", "кола", "coca", "fanta", "sprite", "лимонад", "энергет",
        "чипсы", "chips", "lay's", "lays", "сухарик", "снек", "попкорн",
        "шоколад", "конфет", "вафл", "печень", "торт", "пирожн", "мармелад",
        "мороженое", "сникерс", "mars", "kitkat", "батончик",
        "колбас", "сосис", "сардель", "ветчин", "бекон", "хот-дог",
        "майонез", "кетчуп", "соус барбекю",
        "пиво", "вино", "водк", "алкогол", "сидр",
        "лапша быстр", "доширак", "роллтон", "fast food",
        "газиров", "напиток б/алк", "напиток б алк",
        "жвачк", "жевател",
    )

    private val healthy = listOf(
        "яблок", "банан", "груш", "апельсин", "мандарин", "киви", "виноград",
        "персик", "абрикос", "слива", "ягод", "клубник", "черник", "малин",
        "арбуз", "дыня", "лимон", "грейпфрут",
        "огурец", "помидор", "томат", "морков", "капуст", "свекл", "лук",
        "чеснок", "перец слад", "кабачок", "баклажан", "салат", "зелень",
        "укроп", "петруш", "шпинат", "брокколи",
        "молоко", "кефир", "творог", "йогурт натур", "ряженк", "простокваш",
        "сметан", // спорно, но ближе к молочке
        "хлеб цел", "хлеб ржан", "хлеб зерн", "овсян", "гречк", "рис",
        "каш", "крупа", "мюсли", "хлопья овсян",
        "куриц", "индейк", "филе", "рыба", "треск", "лосось", "сельдь",
        "яйц", "орех", "миндал", "фундук", "семечк",
        "вода пить", "вода минерал", "соков натур",
        "фасоль", "горох", "чечевиц", "нут",
    )

    fun classify(name: String, barcode: String = ""): ProductHealth {
        val text = name.lowercase().trim()
        if (text.isBlank()) return ProductHealth.NEUTRAL

        val harmfulHit = harmful.any { text.contains(it) }
        val healthyHit = healthy.any { text.contains(it) }

        return when {
            harmfulHit && !healthyHit -> ProductHealth.HARMFUL
            healthyHit && !harmfulHit -> ProductHealth.HEALTHY
            harmfulHit && healthyHit -> {
                // «молоко шоколадное» и т.п. — нейтрально/вредно
                if (text.contains("шоколад") || text.contains("сахар") || text.contains("газир")) {
                    ProductHealth.HARMFUL
                } else {
                    ProductHealth.NEUTRAL
                }
            }
            else -> ProductHealth.NEUTRAL
        }
    }
}
