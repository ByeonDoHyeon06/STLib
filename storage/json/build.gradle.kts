plugins {
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":storage:common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}
