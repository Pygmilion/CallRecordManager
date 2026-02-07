buildscript {
    extra.apply {
        set("compose_version", "1.5.4")
        set("kotlin_version", "1.9.20")
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
