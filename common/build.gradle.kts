plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

apply(from = rootProject.file("gradle/convention/eclean-common.gradle"))

dependencies {
    // MiniMessage / Adventure are general-purpose libraries, not loader APIs.
    api(libs.adventure.api)
    api(libs.adventure.text.minimessage)
    api(libs.adventure.text.serializer.plain)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    implementation(libs.cron.utils)
    implementation(libs.snakeyaml)

    testImplementation(libs.kotlin.test)
}
