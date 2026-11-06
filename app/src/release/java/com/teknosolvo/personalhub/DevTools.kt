package com.teknosolvo.personalhub

/** Release builds never contain development seed data. */
object DevTools {
    const val available = false
    suspend fun seed(container: AppContainer): String = "Tidak tersedia di build rilis"
}
