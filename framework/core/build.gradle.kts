dependencies {
    implementation(project(":framework:api"))
    implementation(project(":framework:kernel"))
    implementation(project(":framework:bukkit"))
    implementation(project(":configurate"))
    implementation(project(":storage:common"))
    implementation(project(":platform:common"))
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
        filesMatching("bungee.yml") {
            expand(props)
        }
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }
}
