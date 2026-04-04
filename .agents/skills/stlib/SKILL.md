---
name: stlib
description: >
  Use this skill for any STLib work: feature implementation, refactor, review, debugging,
  docs sync, and consumer plugin usage. Covers STPlugin lifecycle, CommandAPI thin-wrapper
  DSL, plugin-injected STCommand/STListener, reflective DI with package-root auto scan,
  Scheduler hybrid API (Runnable-safe + duration/unit + chained completion), Bridge v2
  (typed pub/sub + target-node RPC + Redisson fallback), storage/resource integrations,
  translation, capability gating, and config bootstrap/migration.
---

# STLib Skill

## Agent Quick Start

When this skill is active, execute in this order:

1. Read `references/docs-api/README.md` and `references/api-map.md`.
2. Identify layer first: `framework:api` vs `framework:bukkit` vs `framework:core`.
3. Preserve lifecycle/degrade defaults (fail-fast only for unsafe bootstrap states).
4. Implement minimal surface change, then wire through STPlugin helpers.
5. Update docs snapshot files in this skill if public behavior changed.
6. Run the smallest valid test matrix, then expand if needed.

## AI-Friendly Task Router

Use this routing table before coding:

- Command behavior issue:
  - Touch: `framework/api/command`, `framework/bukkit/command`, `framework/bukkit/lifecycle/STPlugin.kt`
  - Verify: `./gradlew :framework:api:test :framework:bukkit:test`

- Event/listener issue:
  - Touch: `framework/api/event` (contract), `framework/bukkit/event`, `STPlugin.listen/unlisten`
  - Verify: `./gradlew :framework:bukkit:test`

- DI/component issue:
  - Touch: `framework/api/di`, `framework/bukkit/di`
  - Verify: `./gradlew :framework:bukkit:test`

- Scheduler issue:
  - Touch: `framework/api/scheduler`, `framework/bukkit/scheduler`, `STPlugin` helpers
  - Verify: `./gradlew :framework:api:test :framework:bukkit:test`

- Bridge/Redis issue:
  - Touch: `framework/api/bridge`, `framework/bukkit/bridge`, bootstrap/config wiring
  - Verify: `./gradlew :framework:bukkit:test :framework:core:test`

- STLib ops command/gui/health:
  - Touch: `framework/core`
  - Verify: `./gradlew :framework:core:test`

- Config generation/migration issue:
  - Touch: `framework/bukkit/config`, `framework/api/config`, `framework/core/config`
  - Verify: `./gradlew :configurate:test :framework:bukkit:test :framework:core:test`

## Non-Negotiable Guardrails

1. Respect module boundaries
- `framework:api`: platform-agnostic contracts only.
- `framework:kernel`: platform-agnostic engine internals.
- `framework:core`: STLib ops runtime (commands/gui/dashboard/health).
- `framework:bukkit`: Bukkit/Paper/Folia implementations only.

2. Preserve lifecycle ordering
- `onLoad -> initialize -> load -> onEnable -> enable -> onDisable -> disable`
- Keep disable pipeline resilient (`disable`, `unlistenAll`, cleanup, kernel shutdown, command API shutdown).

3. Keep degrade semantics
- External dependency failure should disable capability, not crash full framework, unless core bootstrap is unsafe.

4. Keep classloader-safe public signatures
- Public runtime helper APIs should prefer `Runnable`/Java-friendly contracts.
- Do not expose `kotlin.jvm.functions.*` in critical STPlugin runtime signatures.

## Current Public Runtime Model (vNext)

### Commands

- DSL style:
  - `command("name") { literal(...); argument(...); executes { ... } }`
- Class style:
  - `class MyCmd(plugin: MyPlugin) : STCommand<MyPlugin>(plugin)`
  - `command<MyCmd>()`

### GUI (Unified)

- Single runtime entry:
  - `val gui = gui(rows, title) { ... }` then `player.openInventory(gui)`
  - quick default: `val gui = gui { ... }`
  - typed inventory: `gui(title, size, InventoryType.DROPPER) { ... }`
- Twilight-like builder primitives:
  - `set(slot)` / `set(row, column)`
  - `set(listOf(...), item)` multi-slot
  - `pattern("...")` then `set('#', item)` symbol mapping (`key(...)` alias 유지)
  - `slot`, `fill`, `border`, `row`, `column`
  - `onOpen`, `onClose`, `onClick`
  - `state`, `refresh`, `reopen`
  - conditional pages: `page(stateKey, stateValue) { ... }` (base layout is default), optional `pageDefault(stateKey) { ... }`
- Removed split path:
  - `InventoryUiService` / `InventoryUiToolkit` (legacy split removed)

### STPlugin Surface Policy

- Keep `STPlugin` focused on lifecycle + orchestration helpers.
- Do not add thin pass-through methods when the service is already exposed as property.
  - Example: use `notifier.send(...)` directly instead of adding `tell(...)` wrapper.
- Current notifier wrappers intentionally removed:
  - `tell`, `announce`, `actionBar`, `title`, `tellTranslated`
- Kept convenience parsers:
  - `send(sender, "<mini>...")`, `console("<mini>...")`

### Events

- Preferred:
  - `class JoinListener(plugin: MyPlugin) : STListener<MyPlugin>(plugin)`
  - `listen<JoinListener>()`
- Manual Bukkit listener path remains valid:
  - `listen(listener: Listener)` (`EventRegistrar<Listener>` 기반 compile-time type-safe)

### DI

- Resolver order:
  - owner plugin -> kernel service -> singleton cache -> reflective creation
- Supports `@STComponent(scope=SINGLETON|PROTOTYPE)` + `@STInject` constructor/field
- Auto scan default scope: plugin main package root
- Scan/graph validation is fail-fast during bootstrap

### Scheduler (Hybrid)

- Legacy-compatible:
  - `sync`, `async`, `later(ticks)`, `timer(ticks, ticks)`
- High-level:
  - `later(delay, unit)` / `later(Duration)`
  - `asyncLater(...)`
  - `timer(delay, period, unit)` / `timer(Duration, Duration)`
  - `asyncTimer(...)`
- Chained handle:
  - `onComplete`, `onCompleteSync`, `onCompleteAsync`

### Bridge v2

- Backward compatible string API:
  - `publish(channel: String, payload: String)`
  - `subscribe(channel: String, ...)`
- Typed/channel API:
  - `BridgeChannel(namespace, key)`
  - typed `publish/subscribe`
  - source-aware subscribe path: `subscribeWithSource(...)` / typed incoming `BridgeIncomingMessage<T>`
  - RPC `respond(...)`, `request(...)` with `BridgeResponseStatus`
- Target-node RPC supported
- Redis backend via Redisson (Libby runtime load), fallback to local mode on failure

### Resources

- Unified resource surface with provider-based degrade strategy
- Vanilla + ItemsAdder + Oraxen + Nexo + MMOItems + EcoItems
- Provider unavailable => capability OFF with reason

## Config File Expectations

Auto-generated/normalized under `plugins/<Plugin>/config/`:

- `plugin.yml` (plugin ops config: version/debug)
- `storage.yml`
- `depend.yml` (`loadDatabaseDrivers`, `loadRedisBridge`, integrations)
- `bridge.yml` (`mode`, `namespace`, `nodeId`, timeout, redis settings)
- `translation.yml`

And language bundles under `plugins/<Plugin>/translation/*.yml`.

## Capability Expectations (Important)

Expect and use these runtime keys:

- Runtime: `runtime:scheduler`, `runtime:di`
- Bridge: `bridge:local`, `bridge:distributed`, `bridge:rpc`, `bridge:codec`, `bridge:redis`
- Text: `text:translation`, `text:notifier`, `text:placeholderapi`
- Storage/Resource/Platform keys as defined in `CapabilityNames`

When adding feature flags, always set clear ON/OFF reasons.

## Documentation Sync Rules

If behavior changes, update skill-local snapshot docs:

- `references/docs-api/README.md`
- `references/docs-api/stplugin.md`
- `references/docs-api/services.md`
- `references/docs-api/runtime.md`
- `references/docs-api/config-files.md`
- `references/docs-api/events.md`
- `references/docs-api/storage.md`
- `references/docs-api/resources.md`
- `references/docs-api/translation.md`

## Verification Matrix

Run minimal-first, then expand:

```bash
./gradlew :framework:api:test
./gradlew :framework:bukkit:test
./gradlew :framework:core:test
./gradlew :framework:kernel:compileKotlin :framework:core:compileKotlin
```

For packaging smoke:

```bash
./gradlew build
```

For consumer sample:

```bash
./gradlew -PincludeExamples=true :stlib-example-consumer:build
```

## Implementation Do/Don't

Do:

- Add thin wrappers over existing contracts rather than parallel frameworks.
- Prefer deterministic migration paths for configs (`VersionedConfig + configMigrationPlan`).
- Keep STPlugin ergonomic for external plugin authors.

Don't:

- Put platform types into `framework:api`.
- Break existing STPlugin helper semantics without explicit migration.
- Add hidden side effects in lifecycle hooks.
- Skip capability updates when degrade/fallback happened.
