# STLib API Map

## Primary docs

- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/README.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/stplugin.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/services.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/events.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/storage.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/resources.md`
- `/Users/byeondohyeon/Develop/Kotlin/STLib/docs/api/config-files.md`

## Embedded docs snapshot (skill-local)

- `.agents/skills/stlib/references/docs-api/README.md`
- `.agents/skills/stlib/references/docs-api/stplugin.md`
- `.agents/skills/stlib/references/docs-api/services.md`
- `.agents/skills/stlib/references/docs-api/events.md`
- `.agents/skills/stlib/references/docs-api/storage.md`
- `.agents/skills/stlib/references/docs-api/resources.md`
- `.agents/skills/stlib/references/docs-api/config-files.md`
- `.agents/skills/stlib/references/docs-api/translation.md`
- `.agents/skills/stlib/references/docs-api/runtime.md`

## Core API contracts

- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/kernel/STKernel.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/capability/CapabilityRegistry.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/command/CommandRegistrar.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/config/ConfigService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/config/ConfigRegistry.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/config/ConfigMigration.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/text/TextService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/notifier/NotifierService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/scheduler/SchedulerService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/bridge/BridgeService.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/event/EventRegistrar.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/event/STListener.kt`
- `framework/api/src/main/kotlin/studio/singlethread/lib/framework/api/translation/CommandTranslationService.kt`

## Kernel (platform-agnostic engine)

- `framework/kernel/src/main/kotlin/studio/singlethread/lib/framework/core/kernel/DefaultSTKernel.kt`
- `framework/kernel/src/main/kotlin/studio/singlethread/lib/framework/core/kernel/ServiceContainer.kt`
- `framework/kernel/src/main/kotlin/studio/singlethread/lib/framework/core/capability/DefaultCapabilityRegistry.kt`
- `framework/kernel/src/main/kotlin/studio/singlethread/lib/framework/core/text/MiniMessageTextService.kt`

## STLib core ops runtime

- `framework/core/src/main/kotlin/studio/singlethread/lib/STLib.kt`
- `framework/core/src/main/kotlin/studio/singlethread/lib/command/StlibReloadCommand.kt`
- `framework/core/src/main/kotlin/studio/singlethread/lib/dashboard/StlibDashboardService.kt`
- `framework/core/src/main/kotlin/studio/singlethread/lib/dashboard/StlibDashboardRuntimeController.kt`
- `framework/core/src/main/kotlin/studio/singlethread/lib/health/StlibHealthSnapshotAssembler.kt`
- `framework/core/src/main/resources/plugin.yml`

## Bukkit runtime entry points

- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/lifecycle/STPlugin.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bootstrap/BukkitKernelBootstrapper.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/event/BukkitEventRegistrar.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/event/STEvent.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/command/SharedLifecycleGate.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/notifier/BukkitNotifierService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/scheduler/BukkitSchedulerService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/bridge/InMemoryBridgeService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/inventory/BukkitInventoryUiService.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/config/BukkitConfigRegistry.kt`
- `framework/bukkit/src/main/kotlin/studio/singlethread/lib/framework/bukkit/translation/BukkitCommandTranslationService.kt`

## Storage modules

- `storage/common/src/main/kotlin/studio/singlethread/lib/storage/api/StorageApi.kt`
- `storage/common/src/main/kotlin/studio/singlethread/lib/storage/api/Storage.kt`
- `storage/common/src/main/kotlin/studio/singlethread/lib/storage/api/CollectionStorage.kt`
- `storage/common/src/main/kotlin/studio/singlethread/lib/storage/api/config/StorageConfig.kt`
- `storage/common/src/main/kotlin/studio/singlethread/lib/storage/api/config/DatabaseConfig.kt`
- `storage/json/src/main/kotlin/studio/singlethread/lib/storage/json/JsonStorageFactory.kt`
- `storage/jdbc/src/main/kotlin/studio/singlethread/lib/storage/jdbc/JdbcStorageFactory.kt`

## Resource integration modules

- `registry/common/src/main/kotlin/studio/singlethread/lib/registry/common/service/ResourceService.kt`
- `registry/itemsadder/src/main/kotlin/studio/singlethread/lib/registry/itemsadder/ItemsAdderResourceProvider.kt`
- `registry/oraxen/src/main/kotlin/studio/singlethread/lib/registry/oraxen/OraxenResourceProvider.kt`
- `registry/nexo/src/main/kotlin/studio/singlethread/lib/registry/nexo/NexoResourceProvider.kt`
- `registry/mmoitems/src/main/kotlin/studio/singlethread/lib/registry/mmoitems/MMOItemsResourceProvider.kt`
- `registry/ecoitems/src/main/kotlin/studio/singlethread/lib/registry/ecoitems/EcoItemsResourceProvider.kt`

## Example consumer plugin

- `stlib-example-consumer/src/main/kotlin/studio/singlethread/lib/example/consumer/STLibExampleConsumer.kt`
- `stlib-example-consumer/src/main/resources/plugin.yml`

## Quick verification commands

```bash
./gradlew :configurate:test
./gradlew :framework:bukkit:test
./gradlew :framework:kernel:compileKotlin :framework:core:compileKotlin
./gradlew -PincludeExamples=true :stlib-example-consumer:build
```
