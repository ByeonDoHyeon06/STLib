import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    kotlin("plugin.serialization") version "2.3.20" apply false
}

group = "studio.singlethread"
version = "1.0.0-SNAPSHOT"

val aggregatorProjects = setOf(
    ":framework",
    ":storage",
    ":registry",
    ":dependency",
    ":platform",
)

allprojects {
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc-repo"
        }
        maven("https://repo.alessiodp.com/releases/") {
            name = "alessiodp"
        }
        maven("https://repo.codemc.io/repository/maven-public/") {
            name = "codemc"
        }
        maven("https://repo.oraxen.com/releases") {
            name = "oraxen"
        }
        maven("https://repo.extendedclip.com/releases/") {
            name = "extendedclip"
        }
        maven("https://mvn.lumine.io/repository/maven-public/") {
            name = "lumine"
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatype-snapshots"
        }
    }
}

subprojects {
    group = "${rootProject.group}.${project.path.trimStart(':').replace(':', '.')}"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (project.path !in aggregatorProjects) {
        dependencies {
            add("implementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8")
            add("testImplementation", kotlin("test"))
            add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.4")
        }
    }
}

dependencies {
    implementation(project(":framework:api"))
    implementation(project(":framework:kernel"))
    implementation(project(":framework:core"))
    implementation(project(":framework:bukkit"))
    implementation(project(":configurate"))
    implementation(project(":storage:common"))
    implementation(project(":storage:jdbc"))
    implementation(project(":storage:json"))
    implementation(project(":registry:common"))
    implementation(project(":registry:itemsadder"))
    implementation(project(":registry:oraxen"))
    implementation(project(":registry:nexo"))
    implementation(project(":registry:mmoitems"))
    implementation(project(":registry:ecoitems"))
    implementation(project(":registry:vanilla"))
    implementation(project(":dependency:bukkit"))
    implementation(project(":platform:bukkit"))
    implementation(project(":platform:folia"))
    implementation(project(":platform:velocity"))
    implementation(project(":platform:bungee"))
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    runServer {
        minecraftVersion("1.20")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("STLib")
        archiveVersion.set(project.version.toString())
        destinationDirectory.set(layout.buildDirectory.dir("libs"))

        relocate("dev.jorel.commandapi", "studio.singlethread.lib.libs.commandapi")
        relocate("net.byteflux.libby", "studio.singlethread.lib.libs.libby")

        doLast {
            copy {
                from(archiveFile)
                into(layout.projectDirectory.dir("builds/plugin"))
            }
        }
    }

    build {
        dependsOn(shadowJar)
    }

    register("verificationMatrix") {
        group = "verification"
        description = "Runs the primary STLib verification matrix (tests + compile boundaries)."
        dependsOn(
            ":framework:api:test",
            ":framework:kernel:test",
            ":framework:bukkit:test",
            ":framework:core:test",
            ":configurate:test",
            ":storage:common:test",
            ":storage:jdbc:test",
            ":storage:json:test",
            ":registry:common:test",
            ":registry:itemsadder:test",
            ":registry:oraxen:test",
            ":registry:nexo:test",
            ":registry:mmoitems:test",
            ":registry:ecoitems:test",
            ":registry:vanilla:test",
            ":dependency:common:compileKotlin",
            ":dependency:bukkit:compileKotlin",
            ":dependency:velocity:compileKotlin",
            ":dependency:bungee:compileKotlin",
            ":platform:common:compileKotlin",
            ":platform:bukkit:compileKotlin",
            ":platform:folia:compileKotlin",
            ":platform:velocity:compileKotlin",
            ":platform:bungee:compileKotlin",
        )
    }

    register("verificationMatrixWithExamples") {
        group = "verification"
        description = "Runs verification matrix and example-consumer build when included."
        dependsOn("verificationMatrix")
        if (project.findProject(":stlib-example-consumer") != null) {
            dependsOn(":stlib-example-consumer:build")
        }
    }

    register("qualityGate") {
        group = "verification"
        description = "Runs CI/local quality gate: verification matrix + production build."
        dependsOn("verificationMatrix")
        dependsOn("build")
    }

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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
