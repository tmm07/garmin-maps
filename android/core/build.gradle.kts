plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
