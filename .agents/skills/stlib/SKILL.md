---
name: stlib
description: >
  STLib implementation standard. Use for API design, refactoring, and feature work with
  SOLID-first architecture, STPlugin slim-core orchestration, CommandAPI thin-wrapper DSL,
  STCommand/STListener plugin-injected classes, DI auto-scan, unified GUI DSL,
  scheduler hybrid API, Bridge v2 typed pub/sub + target-node RPC, and README/skill sync.
---

# STLib Skill (Execution Standard)

## Goal

Build STLib as a production-grade Bukkit/Paper/Folia framework with:
- strong separation of concerns
- explicit degrade behavior for optional systems
- predictable runtime contracts
- clean developer experience

Feature growth is secondary to internal quality.

## Mandatory Skills to Pair

For implementation/refactor tasks, apply together with:
- `solid`
- `kotlin-patterns`

## Architecture Rules

Respect this dependency flow:

`framework:api -> framework:kernel -> framework:bukkit -> framework:core`

- `framework:api`
  - contracts/models only
  - no Bukkit/Velocity/Bungee imports
- `framework:kernel`
  - platform-agnostic runtime engine (registry/capability)
- `framework:bukkit`
  - Bukkit/Paper/Folia implementations only
- `framework:core`
  - STLib operations plugin code (`/stlib*` commands, dashboard, doctor, reload)

## Naming Rules

- Use `ST` uppercase prefix for framework types.
  - Good: `STPlugin`, `STGuiService`, `STBridgeMetrics`
  - Avoid: `StPlugin`, `StGui...`
- Keep package/module names lowercase.

## STPlugin Contract (Slim Core)

`STPlugin` must remain an orchestrator.

### Keep in STPlugin

- lifecycle hooks: `initialize`, `load`, `enable`, `disable`
- command entry points: `command(name, tree)`, `command(STCommand)`, `command<T : STCommand<*>>()`
- event entry points: `listen`, `unlisten`, `unlistenAll`, `fire`
- DI entry point: `component<T>()`
- high-frequency messaging shortcuts: `send`, `console`
- service accessors (text/translation/scheduler/bridge/gui/resource/etc.)

### Keep out of STPlugin

- duplicated convenience wrappers for already-clear service APIs
- multi-path APIs for the same feature
- UI/business logic unrelated to lifecycle orchestration

Rule: **one feature, one primary path**.

## Capability + Degrade Policy

Optional subsystems must not crash core boot.

When optional setup fails:
- set capability OFF
- include explicit reason
- continue boot with safe fallback when possible

Examples:
- Redis bridge unavailable -> fallback local bridge + `bridge:redis` OFF
- PlaceholderAPI unavailable -> text integration OFF, core text still works
- JDBC driver missing -> specific storage capability OFF, JSON stays available

### Runtime dependency status contract

Use the dependency status model consistently:
- `LOADED`
- `PRESENT` (already on classpath)
- `SKIPPED_DISABLED`
- `FAILED`

Capability mapping must be unified:
- `LOADED` or `PRESENT` => capability ON
- `SKIPPED_DISABLED` or `FAILED` => capability OFF (reason required)

For `PRESENT` with version mismatch, policy is **Warn + Enable**.

## Commands (Thin Wrapper Over CommandAPI)

### Preferred styles

1. Inline tree DSL for small commands.
2. `STCommand<P>` class for larger/DI-enabled commands.

### Contract expectations

- support nested literals and typed arguments
- enforce validation (optional ordering, token collisions, executable endpoints)
- do not expose CommandAPI internals in public STLib API
- keep validation logic in a single shared validator component (no DSL/compiler drift)

### Public API safety

- avoid leaking internal mutable builder state in public types
- keep builder helpers internal/private when not intended as extension API

## Events + DI

- Use `STListener<P : STPlugin>` for typed listener classes.
- Use `listen<T : STListener<*>>()` for DI-backed listener registration.
- `STEvent` must remain Bukkit-compatible (`Event` subclass pattern).
- DI uses `@STComponent` + `@STInject` with package-root auto-scan.
- Fail fast on invalid dependency graphs (circular/missing/duplicate).

## GUI DSL

Use `gui.create(...)` as the canonical entry.
`gui.menu(...)` is an acceptable shorthand entry with the same behavior.

Core features:
- `set(...)` by slot/row+column/symbol/list
- `set(vararg slots, ...)` for concise slot mapping
- `setSymbols(...)` / `set(vararg symbols, ...)` for symbol batch mapping
- `pattern(...)` layouts
- global and item click hooks
- state machine via `state(key, value)`
- conditional rendering via `view(stateKey, stateValue)`
- multi-target view mapping via `view(stateKey, stateValues=...)`
- per-viewer isolated GUI sessions (no shared runtime state)
- context helpers: `show(...)`, `toggle(...)`, `cancel()`, `allow()`, `viewer`

Design goals:
- concise, readable DSL (Twilight-like ergonomics)
- strict validation for impossible layouts
- predictable behavior under state/view transitions

Open-path rule:
- use `stGui.open(player)`, `gui.open(player, stGui)`, or `player.openInventory(stGui)` (tracked path)
- `STGui` template must not expose raw `Inventory` as public API

## Scheduler

Maintain both:
- stable low-level APIs (`runSync`, `runAsync`, `runLater`, `runTimer`, `runDelayed`, `runRepeating`)
- high-level ergonomic APIs (`sync`, `async`, `delay`, `repeat`, fluent chain)
- chain aliases: `then(...)`, `thenSync(...)`, `thenAsync(...)`, `thenDelay(...)`
- repeat controls: `repeat(times=...)`, `repeat(task = STRepeatTask { ctx -> ... })`
- time DSL (`millis`, `ticks`, `seconds`, `minutes`, `hours`) for `java.time.Duration`

Required constraints:
- no public `kotlin.jvm.functions.*` in runtime-critical public signatures
- high-level callbacks should use STLib fun-interfaces (`STTask`, `STFailureTask`, `STCompletionTask`)
- callback failures logged with centralized policy
- identical semantics across Bukkit and Folia adapters

### Hard break migration map

- `gui.create(rows = 3, title = ...)` -> `gui.create(title = ..., size = 27)`
- `page(stateKey, stateValue)` -> `view(stateKey, stateValue)`
- `pageDefault(...)` -> root layout + `view(...)` overlays
- `click.event.isCancelled = true` -> `click.isCancelled = true`
- `player.openInventory(stGui.inventory)` -> `stGui.open(player)` or `player.openInventory(stGui)`
- `scheduler.delay(Duration.ofSeconds(2), task = Runnable { ... })` -> `scheduler.delay(2.seconds) { ... }`
- `.thenAsync(Runnable { ... })` -> `.thenAsync { ... }`
- `.thenSync { ... }` can be shortened to `.then { ... }`

## Bridge v2

Support:
- typed publish/subscribe
- target-node request/response RPC
- response status: `SUCCESS`, `TIMEOUT`, `NO_HANDLER`, `ERROR`
- runtime metrics snapshot for ops visibility

Backends:
- `LOCAL`
- `REDIS`
- `COMPOSITE`

Consistency rules:
- first compatible handler wins (registration order)
- missing compatible handler -> `NO_HANDLER`
- decode/handler failure -> `ERROR`
- backpressure limits must be enforced (`maxPendingRequests`)
- `close()` must complete pending in-flight local requests with `ERROR` exactly once

## Config + Translation

- `ConfigRegistry` is the canonical config management path.
- Versioned config migration must be explicit and test-covered.
- Translation fallback chain is mandatory and deterministic.

Default translation lookup order:
1. requested/sender locale
2. configured default locale
3. `en_us`
4. fallback marker `!key!`

Reload behavior:
- `translation.reload()` must clear missing-warning cache so warn-once policy restarts after reload.

## Resource API

Treat `ResourceService` as the canonical abstraction over providers:
- `items()`
- `blocks()`
- `furnitures()`

Providers are optional; availability must be capability-aware.

## Testing Gates

For meaningful framework changes, run at least:

```bash
./gradlew :framework:api:test
./gradlew :framework:bukkit:test
./gradlew :framework:core:test
```

For release-level confidence:

```bash
./gradlew verificationMatrix
./gradlew qualityGate
```

## Documentation Sync Policy

When public behavior changes, update:
- `README.md`
- this skill file (`.agents/skills/stlib/SKILL.md`)

Do not treat `docs/` as the canonical source for this repository workflow.

## PR / Review Checklist

- SRP: each class has one reason to change
- OCP: behavior extended through contracts, not edits everywhere
- LSP/ISP: small predictable interfaces
- DIP: high-level orchestration depends on abstractions
- capability degrade reason is explicit
- test coverage added for changed contract paths
- README and skill examples match real API usage
