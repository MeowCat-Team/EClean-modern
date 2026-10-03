package org.meowcat.eclean.util

fun String.isMatch(list: List<Regex>): Regex? = list.firstOrNull { it matches this }

fun <T> Map<String, List<T>>.filterByMatchers(
    matchers: List<Regex>,
    blackList: Boolean,
): Map<String, List<T>> =
    if (blackList) filterKeys { type -> type.isMatch(matchers) != null }
    else filterKeys { type -> type.isMatch(matchers) == null }
