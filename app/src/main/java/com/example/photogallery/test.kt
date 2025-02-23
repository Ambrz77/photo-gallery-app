package com.example.photogallery

import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase

inline fun test(func: Int.(Int) -> Int) {
    100.func(200)
}
fun String.uppercase() = this.toUpperCase(Locale.current)

fun main() {
    "abd".uppercase()
    test {
       this * it
    }
}
