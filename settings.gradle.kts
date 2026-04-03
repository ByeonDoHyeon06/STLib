rootProject.name = "STLib"

include(
    "framework",
    "framework:api",
    "framework:kernel",
    "framework:core",
    "framework:bukkit",
    "configurate",
    "storage",
    "storage:common",
    "storage:jdbc",
    "storage:json",
    "registry",
    "registry:common",
    "registry:itemsadder",
    "registry:oraxen",
    "registry:nexo",
    "registry:mmoitems",
    "registry:ecoitems",
    "registry:vanilla",
    "dependency",
    "dependency:common",
    "dependency:bukkit",
    "dependency:velocity",
    "dependency:bungee",
    "platform",
    "platform:common",
    "platform:bukkit",
    "platform:folia",
    "platform:velocity",
    "platform:bungee",
)

val includeExamples =
    providers
        .gradleProperty("includeExamples")
        .map(String::toBoolean)
        .orElse(false)
        .get()

if (includeExamples) {
    include("stlib-example-consumer")
}
