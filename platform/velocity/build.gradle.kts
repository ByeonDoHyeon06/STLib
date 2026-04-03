dependencies {
    implementation(project(":platform:common"))
    implementation(project(":dependency:velocity"))

    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
}
