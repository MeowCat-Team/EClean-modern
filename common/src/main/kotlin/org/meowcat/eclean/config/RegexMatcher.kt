package org.meowcat.eclean.config

fun Iterable<Regex>.matches(value: String): Boolean = any { value.matches(it) }
