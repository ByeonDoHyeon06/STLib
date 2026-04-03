---
name: stlib
description: >
  Use this skill when implementing, reviewing, debugging, or documenting STLib-based plugins and framework modules in this repository. Apply it for STPlugin lifecycle hooks, service wiring, CommandAPI registration, event integration (`listen/unlisten/fire` with plugin-injected `STListener`), command integration (DSL + plugin-injected `STCommand` via `command<T>()`), reflective component DI (`@STComponent/@STInject`, `component<T>()`), notifier/scheduler/bridge/inventory runtime services, storage backends (json/sqlite/mysql/postgresql), resource providers (Vanilla/ItemsAdder/Oraxen/Nexo/MMOItems/EcoItems), text integrations (MiniMessage + optional PlaceholderAPI), capability gating, plugin config bootstrap (`config/plugin.yml`, `config/storage.yml`, `config/depend.yml`, `config/translation.yml`), and resource readiness wiring (`ItemsAdderLoadDataEvent`, `NexoItemsLoadedEvent`, `BukkitResourceIntegrationRuntime`). Respect current module split: `framework:kernel` (platform-agnostic engine/capability/service container) and `framework:core` (STLib ops runtime: commands, GUI, dashboard, health, plugin metadata). Include runtime hardening rules: onLoad fail-fast, disable-pipeline step logging, and inventory menu mapping safety.
---

# STLib Framework

## Overview

Implement STLib features using existing API contracts first, then platform modules.
Preserve module boundaries, graceful degrade behavior, and predictable lifecycle ordering.

## Workflow

1. Identify scope.
- If task is for external plugin usage, work in consumer module or docs first.
- If task is framework internals, keep API/platform separation strict.

2. Anchor on current public API.
- Read `references/docs-api/README.md` first (skill-embedded snapshot).
- If repository docs are available, cross-check with `docs/api/README.md`.
- Confirm target surface in `framework:api`, `framework:bukkit`, `storage:common`, `registry:common`.

3. Apply lifecycle and degrade defaults.
- Preserve `onLoad -> initialize -> load -> onEnable -> enable -> onDisable -> disable`.
- Keep external dependency failures non-fatal; disable capability and continue boot.
- Avoid hard fail unless core bootstrap cannot continue safely.
- Enforce onLoad fail-fast: if CommandAPI load, default service registration, or kernel bootstrap fails, skip `initialize/load`.
- Keep disable pipeline resilient: execute all shutdown steps and log step-level failures (`disable`, `unlistenAll`, `cleanup`, `kernelShutdown`, `commandApiShutdown`).

4. Choose correct abstraction level.
- `framework:api`: platform-agnostic contracts + shared annotations (`STComponent`, `STInject`, `STScope`) + command/event specs.
- `framework:kernel`: platform-agnostic engine implementations (kernel service container, capability registry, text parsing baseline).
- `framework:core`: STLib 운영 계층 (ops/runtime) 구현.
- `framework:bukkit` and `platform:*`: platform implementation only.
- `storage:common`: interfaces/contracts and shared helpers.

5. Validate with targeted Gradle tasks.
- For framework changes: `./gradlew :framework:bukkit:test`
- For kernel/core compile boundary: `./gradlew :framework:kernel:compileKotlin :framework:core:compileKotlin`
- For configurate/config bootstrap: `./gradlew :configurate:test`
- For resource integration changes: `./gradlew :registry:common:test :registry:itemsadder:test :registry:oraxen:test :registry:nexo:test :framework:bukkit:compileKotlin`
- For example consumer: `./gradlew -PincludeExamples=true :stlib-example-consumer:build`
- For full packaging regression: `./gradlew build`

## Repository Rules

- Keep module and folder names lowercase.
- Keep STLib entry file naming consistent with `STLib.kt`.
- Keep module responsibilities explicit:
  - `framework:kernel` => pure engine/capability/service container
  - `framework:core` => STLib ops/runtime features
- Prefer compatibility helpers in `STPlugin` for external developer ergonomics.
- Favor non-breaking additions over API signature churn (unless user explicitly asks for breaking changes).

## Implementation Conventions

1. Commands
- Register commands via CommandAPI through STLib helpers; do not declare STPlugin commands in `plugin.yml`.
- Two approved styles:
  - DSL: `command("name") { literal(...); executes { ... } }`
  - Class-based: `class MyCmd(plugin: MyPlugin) : STCommand<MyPlugin>(plugin)` + `command<MyCmd>()`
- `STCommand` must define `name` and `build(builder)`; optional metadata can be overridden (`description`, `permission`, `senderConstraint`, `aliases`, `requirement`).
- Keep wrapper thin: avoid custom command framework logic that diverges from CommandAPI semantics.

2. Events
- Preferred listener style:
  - `class JoinListener(plugin: MyPlugin) : STListener<MyPlugin>(plugin)`
  - register with `listen<JoinListener>()` (reflection + DI)
- `listen(listener: org.bukkit.event.Listener)` is valid for manual instances.
- Use `unlisten(listener)` or `unlistenAll()` for teardown and dynamic listener lifecycle.
- Use `fire(event: STEvent)` for custom event dispatch.

3. Reflective DI (Bukkit)
- Use `component<T>()` / `component(Class<T>)` from `STPlugin` for component construction.
- `listen<T : STListener<*>>()` and `command<T : STCommand<*>>()` both rely on this same resolver path.
- Resolution order:
  - owner plugin instance (`this`),
  - kernel registered service,
  - singleton cache,
  - reflective creation.
- Constructor rules:
  - one `@STInject` constructor => selected,
  - else single constructor => selected,
  - else no-arg constructor fallback,
  - otherwise fail with clear message.
- Field injection:
  - `@STInject` supported,
  - target field must be mutable (`var`/non-final).
- Scope:
  - `@STComponent(STScope.SINGLETON)` => cached,
  - `@STComponent(STScope.PROTOTYPE)` or no annotation => new instance each resolve.
- Circular dependency must fail fast with explicit chain info.

4. Storage
- Use async operations by default.
- Treat sync operations on main thread as invalid.
- Keep `storageApi` and `storage` semantics distinct.

5. Resources
- Integrate providers behind `ResourceService` and typed facades (`ResourceItems`, `ResourceBlocks`, `ResourceFurnitures`).
- Handle missing plugins via capability OFF, not crashes.
- For external plugin providers, prefer `ExternalResourceProvider` and expose precise `unavailableReason()`.
- Distinguish "plugin installed" from "resource registry ready".
- Follow provider readiness signals:
  - ItemsAdder: wait for `ItemsAdderLoadDataEvent` (or API probe fallback)
  - Nexo: wait for `NexoItemsLoadedEvent` (or API probe fallback)
  - Oraxen: probe API readiness and support loaded event bridge when present
- Use `BukkitResourceIntegrationRuntime` to sync capability states on plugin enable/disable and load/reload signals.
- Prefer `from(...)` APIs for reverse mapping (ex: `items().from(itemStack)`) before ad-hoc string parsing.

6. Runtime Services
- Prefer `STPlugin` helpers for `tell/announce`, `sync/async/later/timer`, `publish/subscribe`, and `ui.menu/ui.open`.
- Keep distributed bridge backends optional; local bridge should keep boot non-fatal.
- For inventory UI internals, keep menu mapping stable across reopen cycles (`open` should rebind), and only remove mapping when inventory has no viewers.

7. Management/Stats
- Keep registry updates atomic (`ConcurrentHashMap.compute`-style read-modify-write).
- Command registration/execution counters should stay instrumented through `STPlugin.command(...)`.
- Respect Core Ops profile defaults and avoid collecting low-value metrics unless explicitly enabled.

8. Config
- Config backend is `ConfigurateConfigService` with `YamlConfigurationLoader` (BLOCK style + copy defaults).
- Load from `config/` paths via `loadConfig/reloadConfig/saveConfig`.
- `config/plugin.yml` is the plugin ops metadata file (e.g. `version`, `debug`).
- Prefer `STPlugin(version = "x.y.z")` to provide a version fallback when `config/plugin.yml` is empty.
- Ensure default files are materialized when absent.
- For registry-style config flows, use `ConfigRegistry` (`registerConfig/currentConfig/reloadAllConfigs`).
- For schema evolution, prefer `VersionedConfig` + `configMigrationPlan` and register/reload through migration-aware overloads.
- Migration flow should be deterministic: apply step chain to `latestVersion`, backup previous file in `.backup/`, then save upgraded config.
- Important boundary:
  - `registerConfig(..., migrationPlan)` / `reloadConfig(..., migrationPlan)` => migration applied.
  - `loadConfig(...)` / `frameworkConfigService.load(...)` => raw load only (no migration plan).
- If current config `version` is newer than framework-supported latest version, migration should fail fast with clear error.

## Documentation Updates

When adding or changing behavior, update relevant docs in `docs/api/`:
- `stplugin.md` for developer-facing helpers.
- `commands.md` for DSL + `STCommand` usage and migration guidance.
- `events.md` for listener/event semantics.
- `storage.md` for storage behavior and exceptions.
- `config-files.md` for generated file behavior.
- `resources.md` for provider readiness/capability sync behavior.
- `di.md` (if present) for `@STComponent/@STInject` semantics and examples.
- If API behavior changes, also sync skill snapshot docs under `references/docs-api/`.

## References

- Use [`references/api-map.md`](references/api-map.md) for file locations and quick commands.
- Use `references/docs-api/*.md` for embedded API documentation when repository docs are missing.
