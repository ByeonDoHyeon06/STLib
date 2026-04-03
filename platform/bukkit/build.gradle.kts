dependencies {
    implementation(project(":platform:common"))
    implementation(project(":framework:api"))
    implementation(project(":framework:bukkit"))
    implementation(project(":configurate"))
    implementation(project(":dependency:common"))
    implementation(project(":storage:jdbc"))
    implementation(project(":storage:common"))
    implementation(project(":storage:json"))
    implementation(project(":registry:common"))
    implementation(project(":registry:itemsadder"))
    implementation(project(":registry:oraxen"))
    implementation(project(":registry:nexo"))
    implementation(project(":dependency:bukkit"))

    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
}
