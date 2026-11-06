package com.teknosolvo.personalhub.vault

import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.roundToInt

data class GeneratorOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
)

enum class Strength(val label: String) { VERY_WEAK("Sangat lemah"), WEAK("Lemah"), FAIR("Cukup"), STRONG("Kuat"), VERY_STRONG("Sangat kuat") }

/** Cryptographically secure password generator (SecureRandom, unbiased index selection). */
object PasswordGenerator {
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?/~"
    private const val AMBIGUOUS = "Il1O0o"

    private val random = SecureRandom()

    fun pools(o: GeneratorOptions): List<String> = buildList {
        if (o.upper) add(UPPER)
        if (o.lower) add(LOWER)
        if (o.digits) add(DIGITS)
        if (o.symbols) add(SYMBOLS)
    }.map { pool -> if (o.avoidAmbiguous) pool.filterNot { it in AMBIGUOUS } else pool }

    fun generate(o: GeneratorOptions, rng: SecureRandom = random): String {
        val pools = pools(o)
        require(pools.isNotEmpty()) { "Pilih minimal satu jenis karakter" }
        val length = o.length.coerceIn(pools.size.coerceAtLeast(4), 128)
        val all = pools.joinToString("")
        val chars = CharArray(length)
        // Guarantee at least one character from every selected pool …
        pools.forEachIndexed { i, pool -> chars[i] = pool[rng.nextInt(pool.length)] }
        for (i in pools.size until length) chars[i] = all[rng.nextInt(all.length)]
        // … then Fisher–Yates shuffle so the guaranteed ones are not at fixed positions.
        for (i in length - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars).also { chars.fill('\u0000') }
    }

    /** Entropy estimate in bits based on the character classes present. */
    fun entropyBits(password: String): Int {
        if (password.isEmpty()) return 0
        var pool = 0
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 33
        return (password.length * log2(pool.toDouble().coerceAtLeast(2.0))).roundToInt()
    }

    fun strength(password: String): Strength {
        val bits = entropyBits(password)
        return when {
            bits < 28 -> Strength.VERY_WEAK
            bits < 40 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            bits < 90 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }
}
