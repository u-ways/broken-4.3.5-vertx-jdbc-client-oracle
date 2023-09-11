package io.github.u.ways.util

fun randomString(n: Int): String = (0..n)
    .map { ('a'..'z').random() }
    .joinToString("")