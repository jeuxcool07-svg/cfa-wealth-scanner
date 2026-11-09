package com.adtech.cfawealth

import kotlin.math.pow

object WealthScorer {
    private val styleLabels = mapOf(
        "suit" to 1.0f, "formal wear" to 1.0f, "tuxedo" to 1.0f,
        "dress shirt" to 0.8f, "blazer" to 0.8f, "tie" to 0.7f,
        "shirt" to 0.5f, "jacket" to 0.5f, "sunglasses" to 0.4f,
        "t-shirt" to 0.2f, "hoodie" to 0.1f
    )

    fun styleScore(labels: List<Pair<String, Float>>): Float =
        labels.maxOfOrNull { (name, conf) ->
            (styleLabels[name.lowercase()] ?: 0f) * conf
        } ?: 0f

    fun toFcfa(style: Float, clean: Float): Long {
        val total = (style * 0.6f + clean * 0.4f).coerceIn(0f, 1f)
        return when {
            total < 0.35f -> (5_000 + total / 0.35f * 45_000).toLong()
            total < 0.7f -> (50_000 + ((total - 0.35f) / 0.35f).pow(2) * 20_000_000).toLong()
            else -> (150_000_000 + ((total - 0.7f) / 0.3f).pow(2) * 850_000_000).toLong()
        }
    }
}
