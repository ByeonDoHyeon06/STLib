# STLib

STLib is a Kotlin-first framework for Bukkit/Paper/Folia plugin development.
The current priority is **code quality and runtime safety** (SOLID, clear boundaries, testability), not feature-count parity.

## What STLib Focuses On

- Predictable lifecycle orchestration
- CommandAPI-based thin command wrapper (tree DSL)
- Unified resource API (Vanilla + major resource plugins)
- Storage abstraction (`json`, `sqlite`, `mysql`, `postgresql`)
- MiniMessage + translation pipeline + optional PlaceholderAPI
- Core operations commands (`/stlib`, `/stlib reload`, `/stlib doctor`, `/stlib gui`)

## Module Layout

- `framework:api`: platform-agnostic contracts
- `framework:kernel`: platform-agnostic runtime kernel (registry/capability)
- `framework:bukkit`: Bukkit/Paper/Folia implementations
- `framework:core`: STLib operations plugin runtime

## Dependency Status Model

Optional runtime dependencies are reported with explicit status values:

- `LOADED`: downloaded/loaded by STLib runtime loader
- `PRESENT`: already available on server/plugin classpath (no download)
- `SKIPPED_DISABLED`: runtime loading disabled by config
- `FAILED`: load attempt failed

Capability policy is unified:
- `LOADED`, `PRESENT` => capability ON
- `SKIPPED_DISABLED`, `FAILED` => capability OFF (with reason)

When `PRESENT` version differs from requested version, STLib uses **Warn + Enable**
(logs mismatch, keeps feature enabled to avoid hard downtime).

## Lifecycle Model

STPlugin lifecycle is fixed:

`onLoad -> initialize -> load -> onEnable -> enable -> onDisable -> disable`

This gives a stable place for bootstrap, registration, and teardown.

## STPlugin (Slim Core)

`STPlugin` is an orchestrator, not a large utility bucket.

### Stable entry points

- lifecycle hooks: `initialize`, `load`, `enable`, `disable`
- commands: `command("name") { ... }`, `command<MyCommand>()`
- events: `listen`, `unlisten`, `unlistenAll`, `fire`
- DI: `component<T>()`
- messaging: `send`, `console`

### Service accessors

- `text`, `translation`, `notifier`
- `scheduler`, `commandRegistrar`, `eventRegistrar`
- `configService`, `configRegistry`
- `storageApi`, `storage`
- `bridge`, `gui`, `resource`
- `pluginConfig`, `capabilityRegistry`

## Quick Start

```kotlin
import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin

class ExamplePlugin : STPlugin(version = "1.0.0") {
    override fun initialize() {
        // config + bootstrap preparation
    }

    override fun enable() {
        // register commands/listeners/services
    }

    override fun disable() {
        // custom shutdown
    }
}
```

## Command DSL (Thin Wrapper)

### Inline DSL

```kotlin
override fun enable() {
    command("test") {
        description = "Example command"
        permission = "example.test"
        aliases("t")

        literal("show") {
            string("x")
            string("y")
            executes { ctx ->
                val x = ctx.stringArgument("x") ?: return@executes
                val y = ctx.stringArgument("y") ?: return@executes
                ctx.reply("<green>show:</green> $x, $y")
            }
        }

        literal("hide") { executes { it.reply("<yellow>hidden</yellow>") } }
        literal("clear") { executes { it.reply("<red>cleared</red>") } }
    }
}
```

### Class-based command (recommended for larger plugins)

```kotlin
import studio.singlethread.lib.framework.api.command.CommandDslBuilder
import studio.singlethread.lib.framework.api.command.STCommand

class TestCommand(plugin: ExamplePlugin) : STCommand<ExamplePlugin>(plugin) {
    override val name = "test"

    override fun build(builder: CommandDslBuilder) {
        builder.literal("ping") { executes { it.reply("pong") } }
    }
}

override fun enable() {
    command<TestCommand>()
}
```

## Listener + DI

### STListener

```kotlin
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import studio.singlethread.lib.framework.bukkit.event.STListener

class JoinListener(plugin: ExamplePlugin) : STListener<ExamplePlugin>(plugin) {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        send(event.player, "<green>Welcome</green>")
    }
}

override fun enable() {
    listen<JoinListener>()
}
```

### DI with `@STComponent` and `@STInject`

```kotlin
import studio.singlethread.lib.framework.api.di.STComponent
import studio.singlethread.lib.framework.api.di.STInject

@STComponent
class WelcomeService {
    fun line(name: String) = "Welcome, $name"
}

@STComponent
class JoinListener @STInject constructor(
    plugin: ExamplePlugin,
    private val welcome: WelcomeService,
) : STListener<ExamplePlugin>(plugin) {
    @org.bukkit.event.EventHandler
    fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
        event.player.sendMessage(welcome.line(event.player.name))
    }
}
```

## GUI DSL (Unified)

```kotlin
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.framework.bukkit.gui.STGuiDefinition

val menu =
    gui.menu(
        title = text.parse("<gold>Menu</gold>"),
        size = 27,
        type = InventoryType.CHEST,
        definition = STGuiDefinition {
            pattern(
                "#########",
                "#   S   #",
                "#########",
            )

            setSymbols(symbols = listOf('#'), item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)) {
                it.cancel()
            }
            set('S', ItemStack(Material.EMERALD)) {
                it.cancel()
                val next = it.toggle("view", first = "list", second = "detail")
                it.player.sendMessage("view=$next")
            }

            state("view", "list")
            view("view", "list", "summary", define = STGuiDefinition {
                set(13, ItemStack(Material.BOOK))
            })
            view("view", "detail", define = STGuiDefinition {
                set(13, ItemStack(Material.DIAMOND))
            })
        },
    )
```

Context helpers:
- `click.show(key, value)` updates state + refresh in one step
- `click.toggle(key, first, second)` toggles state + refresh
- `click.cancel()` / `click.allow()`
- `click.viewer` alias for clearer code

Tracked open paths:

```kotlin
menu.open(player)               // canonical template path
gui.open(player, menu)          // canonical service path
player.openInventory(menu)      // extension path (internally tracked)
```

Alternative direct create is still available:

```kotlin
val created =
    gui.create(
        title = text.parse("<gold>Menu</gold>"),
        size = 27,
        type = InventoryType.CHEST,
    ) {
        set(13, ItemStack(Material.BOOK))
    }
```

## Scheduler (Stable + DX)

```kotlin
import studio.singlethread.lib.framework.api.scheduler.seconds
import studio.singlethread.lib.framework.api.scheduler.millis
import studio.singlethread.lib.framework.api.scheduler.STTask
import studio.singlethread.lib.framework.api.scheduler.STRepeatTask

scheduler
    .delay(2.seconds) { logger.info("delayed") }
    .then { logger.info("follow-up sync") }
    .thenDelay(250.millis, task = STTask { logger.info("after short delay") })
    .thenAsync { logger.info("follow-up async") }
    .onFailure { error -> logger.warning("failed: ${error?.message}") }

scheduler.repeat(
    every = 5.seconds,
    delay = 1.seconds,
) { logger.info("tick") }

scheduler.repeat(
    times = 3,
    every = 1.seconds,
) { logger.info("run exactly 3 times") }

scheduler.repeat(
    every = 1.seconds,
    task = STRepeatTask { ctx ->
        logger.info("iteration=${ctx.iteration}")
        if (ctx.iteration >= 10) ctx.stop()
    },
)
```

Low-level APIs (`runSync`, `runAsync`, `runLater`, `runTimer`, `runDelayed`, `runRepeating`) remain supported.
Time DSL supports `millis`, `ticks`, `seconds`, `minutes`, `hours`.

## Hard Break Migration (GUI + Scheduler)

| Before | After |
| --- | --- |
| `gui.create(rows = 3, title = ...) { ... }` | `gui.create(title = ..., size = 27) { ... }` |
| `page("view", "list") { ... }` | `view("view", "list") { ... }` |
| `pageDefault("view") { ... }` | base layout in root builder + `view(...)` overlays |
| `it.event.isCancelled = true` | `it.isCancelled = true` |
| `player.openInventory(stGui.inventory)` | `stGui.open(player)` or `player.openInventory(stGui)` |
| `scheduler.delay(Duration.ofSeconds(2), task = Runnable { ... })` | `scheduler.delay(2.seconds) { ... }` |
| `.thenAsync(Runnable { ... })` | `.thenAsync { ... }` |

## Bridge v2 (Typed Pub/Sub + Target RPC)

```kotlin
import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeRequestResult

val codec = object : BridgeCodec<String> {
    override fun encode(value: String): String = value
    override fun decode(payload: String): String = payload
}

val channel = BridgeChannel.of("example", "chat")

bridge.subscribe(channel, codec) { incoming ->
    logger.info("from=${incoming.sourceNode.value}, payload=${incoming.payload}")
}

bridge.publish(channel, "hello", codec)

bridge.respond(channel, codec, codec) { request ->
    if (request.payload == "ping") BridgeRequestResult.success("pong")
    else BridgeRequestResult.error("unknown")
}
```

Modes in `config/bridge.yml`:
- `LOCAL`
- `REDIS`
- `COMPOSITE`

If Redis is unavailable, STLib degrades to local mode with capability reason logging.

In-memory bridge also guarantees request finalization on shutdown:
- pending requests are completed as `ERROR` during `close()`
- timeout/close races are guarded against duplicate completion

## Text + Translation + PlaceholderAPI

```kotlin
val line = translation.translate(
    key = "example.welcome",
    placeholders = mapOf("player" to player.name),
)
player.sendMessage(text.parse(line))
```

Translation fallback order:
1. requested/sender locale
2. default locale (`config/translation.yml`)
3. `en_us`
4. fallback marker: `!key!`

`translation.reload()` resets missing-key warning cache, so post-reload missing keys can warn once again.

## Storage

```kotlin
val users = storage.collection("users")
// Use async storage operations for heavy I/O.
```

Default storage backend is selected from `config/storage.yml` with capability-aware fallback.

## Unified Resource API

```kotlin
val items = resource.items()
val id = "minecraft:diamond_sword"

val exists = items.exists(id)
val icon = items.icon(id)
val displayName = items.displayName(id)
val stack = items.create(id)
```

Providers are optional. Unavailable providers are degraded by capability instead of hard-failing boot.

## Generated Config Files

On first run, STLib-based plugins generate:

- `plugins/<Plugin>/config/plugin.yml`
- `plugins/<Plugin>/config/storage.yml`
- `plugins/<Plugin>/config/depend.yml`
- `plugins/<Plugin>/config/bridge.yml`
- `plugins/<Plugin>/config/translation.yml`
- `plugins/<Plugin>/translation/{locale}.yml`

## STLib Operations Commands

- `/stlib`: runtime and capability summary
- `/stlib reload`: reload runtime config/translation/dashboard settings
- `/stlib doctor`: health diagnostics summary
- `/stlib gui`: STPlugin operations dashboard

## Quality Gates

```bash
./gradlew :framework:api:test
./gradlew :framework:bukkit:test
./gradlew verificationMatrix
./gradlew qualityGate
```

## Canonical Agent Docs

Project guidance for AI-assisted implementation is maintained in:

- `/Users/byeondohyeon/Develop/Kotlin/STLib/.agents/skills/stlib/SKILL.md`
