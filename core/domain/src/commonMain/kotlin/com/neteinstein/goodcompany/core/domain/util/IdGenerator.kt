package com.neteinstein.goodcompany.core.domain.util

/** Produces unique ids for app-created entities. Injected so tests are deterministic. */
fun interface IdGenerator {
    fun next(): String

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        val Random: IdGenerator = IdGenerator {
            buildString { repeat(16) { append(ALPHABET[kotlin.random.Random.nextInt(ALPHABET.length)]) } }
        }
    }
}
