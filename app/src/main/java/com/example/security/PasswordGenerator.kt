package com.example.security

import java.security.SecureRandom
import kotlin.math.log2

/**
 * Generador y Evaluador Criptográfico de Contraseñas y Frases de Paso.
 */
object PasswordGenerator {

    private val secureRandom = SecureRandom()

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private const val AMBIGUOUS = "il1Lo0O"

    // Diccionario de palabras seguras en español e inglés para passphrases
    private val PASSPHRASE_WORDS = listOf(
        "bosque", "delta", "galaxia", "aurora", "halcon", "zenit", "orion", "cripta",
        "oceano", "trueno", "prisma", "bambu", "cometa", "sombra", "cuarzo", "volcan",
        "espejo", "pulso", "orbita", "selva", "vortice", "antorcha", "estigma", "abismo",
        "horizonte", "quimera", "solsticio", "nebulosa", "centella", "cascada", "bastion",
        "matrix", "titan", "cyber", "shield", "nexus", "vertex", "vector", "signal",
        "beacon", "canyon", "glacier", "phoenix", "falcon", "quantum", "solaris", "shadow"
    )

    data class GeneratorConfig(
        val length: Int = 18,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeSymbols: Boolean = true,
        val excludeAmbiguous: Boolean = true
    )

    enum class StrengthLevel(val label: String, val colorHex: Long) {
        VERY_WEAK("Muy Débil", 0xFFDC2626),
        WEAK("Débil", 0xFFEA580C),
        MEDIUM("Media", 0xFFF59E0B),
        STRONG("Fuerte", 0xFF10B981),
        MILITARY("Grado Militar", 0xFF059669)
    }

    data class PasswordStrength(
        val score: Float, // 0.0 to 1.0
        val entropyBits: Int,
        val level: StrengthLevel,
        val feedback: String
    )

    fun generatePassword(config: GeneratorConfig): String {
        var pool = ""
        val mandatoryChars = mutableListOf<Char>()

        var upperPool = UPPERCASE
        var lowerPool = LOWERCASE
        var numPool = NUMBERS
        var symPool = SYMBOLS

        if (config.excludeAmbiguous) {
            upperPool = upperPool.filterNot { it in AMBIGUOUS }
            lowerPool = lowerPool.filterNot { it in AMBIGUOUS }
            numPool = numPool.filterNot { it in AMBIGUOUS }
        }

        if (config.includeUppercase) {
            pool += upperPool
            mandatoryChars.add(upperPool[secureRandom.nextInt(upperPool.length)])
        }
        if (config.includeLowercase) {
            pool += lowerPool
            mandatoryChars.add(lowerPool[secureRandom.nextInt(lowerPool.length)])
        }
        if (config.includeNumbers) {
            pool += numPool
            mandatoryChars.add(numPool[secureRandom.nextInt(numPool.length)])
        }
        if (config.includeSymbols) {
            pool += symPool
            mandatoryChars.add(symPool[secureRandom.nextInt(symPool.length)])
        }

        if (pool.isEmpty()) {
            pool = LOWERCASE
            mandatoryChars.add(lowerPool[secureRandom.nextInt(lowerPool.length)])
        }

        val targetLength = config.length.coerceAtLeast(mandatoryChars.size)
        val remainingLength = targetLength - mandatoryChars.size

        val passwordChars = mutableListOf<Char>()
        passwordChars.addAll(mandatoryChars)

        for (i in 0 until remainingLength) {
            val randomIndex = secureRandom.nextInt(pool.length)
            passwordChars.add(pool[randomIndex])
        }

        // Shuffle criptográfico
        for (i in passwordChars.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = passwordChars[i]
            passwordChars[i] = passwordChars[j]
            passwordChars[j] = temp
        }

        return passwordChars.joinToString("")
    }

    fun generatePassphrase(wordCount: Int = 4, separator: String = "-", includeNumber: Boolean = true): String {
        val selectedWords = (1..wordCount).map {
            PASSPHRASE_WORDS[secureRandom.nextInt(PASSPHRASE_WORDS.size)].replaceFirstChar { char ->
                char.uppercase()
            }
        }.toMutableList()

        if (includeNumber) {
            val randomNum = secureRandom.nextInt(90) + 10 // 10..99
            val pos = secureRandom.nextInt(selectedWords.size)
            selectedWords[pos] = "${selectedWords[pos]}$randomNum"
        }

        return selectedWords.joinToString(separator)
    }

    fun evaluateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0f, 0, StrengthLevel.VERY_WEAK, "Introduce una contraseña")
        }

        var poolSize = 0
        var hasUpper = false
        var hasLower = false
        var hasNum = false
        var hasSym = false

        for (ch in password) {
            when {
                ch in UPPERCASE -> hasUpper = true
                ch in LOWERCASE -> hasLower = true
                ch in NUMBERS -> hasNum = true
                else -> hasSym = true
            }
        }

        if (hasUpper) poolSize += 26
        if (hasLower) poolSize += 26
        if (hasNum) poolSize += 10
        if (hasSym) poolSize += 32

        if (poolSize == 0) poolSize = 10

        val entropy = (password.length * log2(poolSize.toDouble())).toInt()

        val (score, level, feedback) = when {
            entropy < 35 -> Triple(0.2f, StrengthLevel.VERY_WEAK, "Muy corta o fácil de descifrar")
            entropy < 55 -> Triple(0.4f, StrengthLevel.WEAK, "Fácil de adivinar por fuerza bruta")
            entropy < 75 -> Triple(0.65f, StrengthLevel.MEDIUM, "Seguridad adecuada para cuentas menores")
            entropy < 100 -> Triple(0.85f, StrengthLevel.STRONG, "Excelente protección criptográfica")
            else -> Triple(1.0f, StrengthLevel.MILITARY, "Seguridad de nivel militar / Máxima protección")
        }

        return PasswordStrength(score, entropy, level, feedback)
    }
}
