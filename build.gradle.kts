buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 provides built-in Kotlin with a runtime dependency on KGP 2.2.10;
        // declare the tooling versions here to run Kotlin/KSP at the catalog versions.
        classpath(libs.tooling.kotlin.gradle.plugin)
        classpath(libs.tooling.ksp.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
