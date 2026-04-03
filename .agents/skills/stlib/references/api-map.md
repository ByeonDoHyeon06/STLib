# STLib API Map

## Primary docs (repo)

- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/README.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/stplugin.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/services.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/events.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/storage.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/resources.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/config-files.md`

## Embedded docs snapshot (skill-local)

- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/README.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/stplugin.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/services.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/events.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/storage.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/resources.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/config-files.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/translation.md`
- `/Users/byeondohyeon/.codex/skills/stlib/references/docs-api/runtime.md`

## Core contracts (framework:api)

- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/kernel/STKernel.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/capability/CapabilityRegistry.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/command/CommandRegistrar.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/command/CommandDsl.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/command/STCommand.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/event/EventRegistrar.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/config/ConfigService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/config/ConfigRegistry.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/config/ConfigMigration.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/text/TextService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/notifier/NotifierService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/translation/TranslationService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/scheduler/SchedulerService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/scheduler/ScheduleSpec.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/bridge/BridgeService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/di/STComponent.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/di/STInject.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/di/STScope.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/di/ComponentContainer.kt`

## Engine + Ops

- Kernel engine: `framework/kernel/src/main/kotlin/studio/singlethread/lib/framework/core/`
- STLib ops runtime: `framework/core/src/main/kotlin/studio/singlethread/lib/`

## Bukkit runtime

- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/lifecycle/STPlugin.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bootstrap/BukkitKernelBootstrapper.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/scheduler/BukkitSchedulerService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bridge/InMemoryBridgeService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bridge/RedissonBridgeService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bridge/CompositeBridgeService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bridge/NamespacedBridgeService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bridge/KotlinxBridgeCodecs.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/di/ReflectiveComponentResolver.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/event/BukkitEventRegistrar.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/event/STEvent.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/event/STListener.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/config/PluginFileConfigLoader.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/config/BridgeFileSettings.kt`

## Storage

- `storage/common/src/main/kotlin/studio/singlethread/lib/storage/api/`
- `storage/json/src/main/kotlin/studio/singlethread/lib/storage/json/JsonStorageFactory.kt`
- `storage/jdbc/src/main/kotlin/studio/singlethread/lib/storage/jdbc/JdbcStorageFactory.kt`

## Registry providers

- `registry/common/src/main/kotlin/studio/singlethread/lib/registry/common/service/ResourceService.kt`
- `registry/vanilla/src/main/kotlin/studio/singlethread/lib/registry/vanilla/VanillaResourceProvider.kt`
- `registry/itemsadder/src/main/kotlin/studio/singlethread/lib/registry/itemsadder/ItemsAdderResourceProvider.kt`
- `registry/oraxen/src/main/kotlin/studio/singlethread/lib/registry/oraxen/OraxenResourceProvider.kt`
- `registry/nexo/src/main/kotlin/studio/singlethread/lib/registry/nexo/NexoResourceProvider.kt`
- `registry/mmoitems/src/main/kotlin/studio/singlethread/lib/registry/mmoitems/MMOItemsResourceProvider.kt`
- `registry/ecoitems/src/main/kotlin/studio/singlethread/lib/registry/ecoitems/EcoItemsResourceProvider.kt`

## Quick verification

```bash
./gradlew :framework:api:test
./gradlew :framework:bukkit:test
./gradlew :framework:core:test
./gradlew :framework:kernel:compileKotlin :framework:core:compileKotlin
./gradlew -PincludeExamples=true :stlib-example-consumer:build
```
