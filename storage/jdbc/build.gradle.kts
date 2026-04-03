dependencies {
    implementation(project(":storage:common"))
    implementation(project(":dependency:common"))

    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation("com.mysql:mysql-connector-j:8.4.0")
    testImplementation("org.postgresql:postgresql:42.7.3")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:mysql:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
}
