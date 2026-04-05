# STLib

STLib는 Bukkit/Paper/Folia 기반 Kotlin 플러그인을 빠르게 개발하기 위한 프레임워크입니다.

핵심 목표:
- 일관된 라이프사이클 (`onLoad -> initialize -> load -> onEnable -> enable -> onDisable -> disable`)
- CommandAPI Thin Wrapper 기반 트리 DSL
- 통합 Resource API (Vanilla / ItemsAdder / Oraxen / Nexo / MMOItems / EcoItems)
- Storage 추상화 (json/sqlite/mysql/postgresql)
- MiniMessage + Translation + 선택적 PlaceholderAPI
- 운영 관제 (`/stlib`, `/stlib reload`, `/stlibgui`)

## 모듈 구조

- `framework:api`: 플랫폼 비종속 계약(명령/이벤트/스케줄러/브리지/DI 등)
- `framework:kernel`: 순수 엔진/서비스 레지스트리/Capability 코어
- `framework:bukkit`: Bukkit/Paper/Folia 구현체
- `framework:core`: STLib 운영 플러그인 코어(명령/대시보드/헬스)

## 빠른 시작

### 1) 기본 플러그인 클래스

```kotlin
import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin

class ExamplePlugin : STPlugin(version = "1.0.0") {
    override fun initialize() {
        // config register, initial wiring
    }

    override fun load() {
        // pre-enable phase
    }

    override fun enable() {
        // command/event/scheduler
    }

    override fun disable() {
        // plugin custom shutdown
    }
}
```

`version`은 `config/plugin.yml`의 `version`이 비어 있을 때 fallback으로 사용됩니다.

### 1-1) 로드 배너(ASCII Art) 커스터마이징

배너는 공통 `STPlugin`이 아니라 `STLib`에서만 출력됩니다.
즉, STLib 운영 플러그인의 타이틀 아트만 수정하면 됩니다.

```kotlin
class STLib : STPlugin() {
    protected fun stlibTitleAsciiArt(): List<String> {
        return listOf(/* your title art lines */)
    }
}
```

`framework/core/src/main/kotlin/studio/singlethread/lib/STLib.kt`의 `stlibTitleAsciiArt()`만 수정하면 됩니다.

### 2) 자동 생성되는 기본 설정 파일

플러그인 최초 실행 시 `plugins/<PluginName>/config/` 아래 파일이 자동 생성됩니다.

- `plugin.yml` (플러그인 운영 설정: 버전/디버그)
- `storage.yml` (스토리지 백엔드/연결 설정)
- `depend.yml` (외부 연동/런타임 의존 로딩 설정)
- `bridge.yml` (브리지 모드/노드/타임아웃/redis 설정)
- `translation.yml` + `translation/{locale}.yml` (번역)

예시: `config/plugin.yml`

```yml
version: "1.0.0"
debug: false
```

참고:
- 설정 클래스에는 `@Comment` 기반 주석이 포함되어 있습니다.
- 기존 파일에 주석이 없는 경우, 과거에 생성된 파일일 수 있습니다(필요 시 백업 후 재생성).

## Command DSL (v3)

STLib은 `plugin.yml` 커맨드 선언 없이 CommandAPI로 등록합니다.

```kotlin
import studio.singlethread.lib.framework.api.command.CommandSenderConstraint

override fun enable() {
    command("test") {
        description = "Example command"
        permission = "example.command.test"
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

        literal("hide") {
            sender(CommandSenderConstraint.PLAYER_ONLY)
            executes { ctx ->
                ctx.reply("<yellow>hidden</yellow>")
            }
        }

        literal("clear") {
            executes { ctx ->
                ctx.reply("<red>cleared</red>")
            }
        }
    }
}
```

동적 탭완성 예시:

```kotlin
command("warp") {
    literal("go") {
        string(
            name = "name",
            dynamicSuggestions = { _ -> listOf("spawn", "shop", "pvp") },
        )
        executes { ctx ->
            ctx.reply("warp: ${ctx.stringArgument("name")}")
        }
    }
}
```

클래스형(플러그인 주입)도 지원합니다.

```kotlin
import studio.singlethread.lib.framework.api.command.CommandDslBuilder
import studio.singlethread.lib.framework.api.command.STCommand

class TestCommand(plugin: ExamplePlugin) : STCommand<ExamplePlugin>(plugin) {
    override val name = "test"
    override val permission = "example.command.test"

    override fun build(builder: CommandDslBuilder) {
        builder.literal("show") {
            string("x")
            string("y")
            executes { ctx ->
                ctx.reply("<green>show</green> ${ctx.stringArgument("x")} ${ctx.stringArgument("y")}")
            }
        }
        builder.literal("hide") { executes { it.reply("<yellow>hide</yellow>") } }
        builder.literal("clear") { executes { it.reply("<red>clear</red>") } }
    }
}

override fun enable() {
    command<TestCommand>() // reflection + DI
}
```

Bukkit 타입 인자 헬퍼는 확장 함수로 제공합니다.

```kotlin
import studio.singlethread.lib.framework.bukkit.command.playerArgument
import studio.singlethread.lib.framework.bukkit.command.worldArgument

command("tpwhere") {
    player("target")
    world("world")
    executes { ctx ->
        val target = ctx.playerArgument("target") ?: return@executes
        val world = ctx.worldArgument("world") ?: return@executes
        ctx.reply("target=${target.name}, world=${world.name}")
    }
}
```

## Event + DI

### 1) STListener(플러그인 주입형) 등록/해제

```kotlin
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import studio.singlethread.lib.framework.bukkit.event.STListener

class JoinListener(plugin: ExamplePlugin) : STListener<ExamplePlugin>(plugin) {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        event.player.sendMessage("welcome from ${plugin.name}")
    }
}

override fun enable() {
    listen<JoinListener>()
    // 또는 listen(JoinListener(this))
}
```

### 2) DI 자동 스캔 (`@STComponent` / `@STInject`)

```kotlin
import studio.singlethread.lib.framework.api.di.STComponent
import studio.singlethread.lib.framework.api.di.STInject
import studio.singlethread.lib.framework.api.di.STScope
import studio.singlethread.lib.framework.bukkit.event.STListener

@STComponent(scope = STScope.SINGLETON)
class WelcomeService {
    fun line(name: String) = "Welcome, $name"
}

@STComponent
class JoinListener @STInject constructor(
    plugin: ExamplePlugin,
    private val welcomeService: WelcomeService,
) : STListener<ExamplePlugin>(plugin) {
    @org.bukkit.event.EventHandler
    fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
        event.player.sendMessage(welcomeService.line(event.player.name))
    }
}

override fun enable() {
    listen<JoinListener>()
    val service = component<WelcomeService>()
}
```

DI 규칙:
- 생성자 주입 기본 (`@STInject` 생성자 우선)
- `@STInject` 필드 주입 지원 (`var`만 가능)
- 스코프: `SINGLETON` / `PROTOTYPE`
- 순환 의존/해결 불가 타입은 onLoad에서 fail-fast

### 3) ST 커스텀 이벤트 발행

```kotlin
import org.bukkit.event.HandlerList
import studio.singlethread.lib.framework.bukkit.event.STEvent

class ExampleEvent(val value: String) : STEvent() {
    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}

// fire(ExampleEvent("hello"))
```

## GUI DSL (Unified)

STLib GUI는 `gui { ... }`로 만들고 `player.openInventory(gui)`로 엽니다.

```kotlin
val basic = gui(rows = 1, title = "<gold>Basic</gold>") {
    set(4, ItemStack(Material.APPLE)) { click ->
        click.event.isCancelled = true
        click.player.sendMessage("apple")
    }
}
player.openInventory(basic)
```

```kotlin
import org.bukkit.event.inventory.InventoryType

val complex = gui(rows = 3, title = "<gray>Complex</gray>") {
    pattern(
        "#########",
        "#   S   #",
        "#########",
    )

    set('#', ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)) { it.event.isCancelled = true }
    set('S', ItemStack(Material.PLAYER_HEAD)) { it.event.isCancelled = true }
}
player.openInventory(complex)

val dropper = gui(title = mini("<gray>Dropper</gray>"), size = 9, type = InventoryType.DROPPER) {
    set(listOf(1, 3, 4, 5, 7), ItemStack(Material.GRAY_STAINED_GLASS_PANE))
    onClick { it.event.isCancelled = true }
}
player.openInventory(dropper)
```

상태 기반 뷰 전환(`page`) 예시:

```kotlin
val routed = gui(rows = 6, title = "<gold>Plugins</gold>") {
    state("view", "list")

    pattern(
        "#########",
        "#       #",
        "#       #",
        "#       #",
        "#       #",
        "#########",
    )
    set('#', ItemStack(Material.GRAY_STAINED_GLASS_PANE))

    page(stateKey = "view", stateValue = "list") {
        set(10, ItemStack(Material.PAPER))
        set(49, ItemStack(Material.BOOK)) { click ->
            click.state("view", "detail")
            click.refresh()
        }
    }

    page(stateKey = "view", stateValue = "detail") {
        set(22, ItemStack(Material.BOOK))
        set(49, ItemStack(Material.ARROW)) { click ->
            click.state("view", "list")
            click.refresh()
        }
    }
}
player.openInventory(routed)
```

`page`는 “페이지네이션” 전용이 아니라, 상태값(`state`)에 따라 활성 블루프린트를 전환하는 조건부 레이어입니다.

## Scheduler (Hybrid)

기존 tick 기반 API + duration/unit 기반 API + completion chain을 함께 제공합니다.

```kotlin
import java.time.Duration
import java.util.concurrent.TimeUnit

override fun enable() {
    // 기존 호환 API
    later(20L, Runnable { debug("after 1s in ticks") })
    timer(20L, 20L, Runnable { debug("every 1s in ticks") })

    // high-level delay
    later(3, TimeUnit.SECONDS, Runnable { debug("3s delayed") })
        .onComplete { result -> debug("delay result=${result.status}") }

    // async delay
    asyncLater(Duration.ofSeconds(2), Runnable { debug("async delayed") })
        .onCompleteAsync { result -> debug("async complete=${result.status}") }
}
```

completion status:
- `SUCCESS`
- `CANCELLED`
- `FAILED`

## Bridge v2 (Typed Pub/Sub + RPC)

```kotlin
import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeRequestResult
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus

private val stringCodec =
    object : BridgeCodec<String> {
        override fun encode(value: String): String = value
        override fun decode(payload: String): String = payload
    }

override fun enable() {
    val channel = bridgeChannel("example", "chat")

    subscribe(channel, stringCodec) { payload ->
        debug("received: $payload")
    }

    publish(channel, "hello-bridge", stringCodec)

    respond(channel, stringCodec, stringCodec) { request ->
        if (request.payload == "ping") BridgeRequestResult.success("pong")
        else BridgeRequestResult.error("unknown request")
    }

    request(channel, "ping", stringCodec, stringCodec).thenAccept { response ->
        when (response.status) {
            BridgeResponseStatus.SUCCESS -> debug("rpc=${response.payload}")
            BridgeResponseStatus.TIMEOUT -> debug("rpc timeout")
            BridgeResponseStatus.NO_HANDLER -> debug("rpc no-handler")
            BridgeResponseStatus.ERROR -> debug("rpc error=${response.message}")
        }
    }
}
```

운영 모드(`config/bridge.yml`):
- `LOCAL`
- `REDIS`
- `COMPOSITE`

Redis 연결 실패 시 local로 자동 degrade됩니다.

## Text / Translation / PlaceholderAPI

```kotlin
override fun enable() {
    command("hello") {
        executes { ctx ->
            val sender = ctx.sender as? org.bukkit.command.CommandSender ?: return@executes
            sender.sendMessage(mini(sender, "<gold>Hello <player></gold>"))
            sendTranslated(sender, "example.welcome", mapOf("player" to sender.name))
        }
    }
}
```

메시징 가이드:
- 간단 사용: `send(sender, "<mini>...")`, `console("<mini>...")`
- 고급 사용: `notifier.send`, `notifier.sendPrefixed`, `notifier.actionBar`, `notifier.title`

`depend.yml`에서 PlaceholderAPI 연동이 활성화되어 있고 플러그인이 설치된 경우, `mini(sender, ...)` 경로에서 플레이스홀더를 사용할 수 있습니다.

## Storage

```kotlin
import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.nio.charset.StandardCharsets

data class UserData(val points: Int)

private val userCodec =
    object : StorageCodec<UserData> {
        override fun encode(value: UserData): ByteArray {
            val raw = """{"points":${value.points}}"""
            return raw.toByteArray(StandardCharsets.UTF_8)
        }

        override fun decode(bytes: ByteArray): UserData {
            val raw = bytes.toString(StandardCharsets.UTF_8)
            return UserData(raw.substringAfter(":").substringBefore("}").toInt())
        }
    }

override fun enable() {
    val users = storage.collection("users")

    users.set("steve", UserData(10), userCodec).thenAccept {
        debug("saved: $it")
    }

    users.get("steve", userCodec).thenAccept { loaded ->
        debug("loaded: $loaded")
    }
}
```

원칙:
- 비동기 API 우선 사용
- 메인 스레드에서 sync 호출 남용 금지

## Resource API

```kotlin
override fun enable() {
    val items = resource.items()
    val blocks = resource.blocks()
    val furnitures = resource.furnitures()

    val swordRef = items.from("minecraft:diamond_sword")
    val sword = items.create("minecraft:diamond_sword")
    val swordName = items.displayName("minecraft:diamond_sword")

    debug("item ref=$swordRef, display=$swordName, stack=${sword != null}")
}
```

대표 메서드:
- `from(...)`: ID 또는 게임 오브젝트 -> 참조 객체 역매핑
- `create(...)`: 참조/ID -> 실제 오브젝트 생성
- `displayName(...)`, `icon(...)`, `ids()`, `exists(...)`

## 운영 명령

- `/stlib`: STLib 런타임 상태
- `/stlib reload`: 설정/번역/관제 리로드
- `/stlibgui`: STPlugin Core Ops 대시보드

## 참고

빠르게 진화하는 저장소라 API 변경 시 README 예제가 먼저 갱신될 수 있습니다.
실사용 전에는 각 모듈 테스트(`:framework:api`, `:framework:bukkit`, `:framework:core`)를 같이 확인하는 것을 권장합니다.
