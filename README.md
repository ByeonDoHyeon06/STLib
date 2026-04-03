# STLib

STLib는 Bukkit/Paper/Folia 기반 Kotlin 플러그인을 빠르게 개발하기 위한 프레임워크입니다.

핵심 목표:
- 일관된 라이프사이클 (`onLoad -> initialize -> load -> onEnable -> enable -> onDisable -> disable`)
- CommandAPI 기반 트리 DSL
- 통합 Resource API (Vanilla / ItemsAdder / Oraxen / Nexo / MMOItems / EcoItems)
- Storage 추상화 (json/sqlite/mysql/postgresql)
- MiniMessage + Translation + 선택적 PlaceholderAPI
- 운영 관제 (`/stlib`, `/stlibgui`)

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

### 2) 자동 생성되는 기본 설정 파일

플러그인 최초 실행 시 `plugins/<PluginName>/config/` 아래 파일이 자동 생성됩니다.

- `plugin.yml` (플러그인 운영 설정)
- `storage.yml` (스토리지 백엔드/연결 설정)
- `depend.yml` (외부 연동/런타임 의존 로딩 설정)
- `bridge.yml` (브리지 모드/노드/타임아웃/redis 설정)
- `translation.yml` + `translation/{locale}.yml` (번역)

예시: `config/plugin.yml`

```yml
version: "1.0.0"
debug: false
```

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

클래스형(플러그인 주입)도 지원합니다.

```kotlin
import studio.singlethread.lib.framework.api.command.STCommand

class TestCommand(plugin: ExamplePlugin) : STCommand<ExamplePlugin>(plugin) {
    override val name = "test"
    override val permission = "example.command.test"

    override fun build(builder: studio.singlethread.lib.framework.api.command.CommandDslBuilder) {
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

## Event 사용

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
    listen<JoinListener>() // reflection + constructor DI
    // or: listen(JoinListener(this))
}
```

### 2) DI 자동 스캔 (`@STComponent` / `@STInject`)

STLib는 플러그인 메인 클래스 패키지 루트를 자동 스캔해 `@STComponent`를 검증/조립합니다.
그래프가 깨져 있으면 onLoad에서 fail-fast 됩니다.

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
    listen<JoinListener>()          // DI 생성 + 등록
    val service = component<WelcomeService>() // 직접 resolve도 가능
}
```

DI 규칙:
- 생성자 주입 기본 (`@STInject` 생성자 우선)
- `@STInject` 필드 주입 지원 (`var`만 가능)
- 스코프: `SINGLETON` / `PROTOTYPE`
- 순환 의존/해결 불가 타입은 명확한 예외로 실패

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

// fire(ExampleEvent(...))
```

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

    // repeating task
    val repeating =
        asyncTimer(
            delay = 1,
            period = 5,
            unit = TimeUnit.SECONDS,
            task = Runnable { debug("repeat tick") },
        )

    // 원하는 시점에 취소
    later(Duration.ofSeconds(20), Runnable { repeating.cancel() })
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

    // typed subscribe
    subscribe(channel, stringCodec) { payload ->
        debug("received: $payload")
    }

    // typed publish
    publish(channel, "hello-bridge", stringCodec)

    // RPC responder
    respond(channel, stringCodec, stringCodec) { request ->
        if (request.payload == "ping") {
            BridgeRequestResult.success("pong")
        } else {
            BridgeRequestResult.error("unknown request")
        }
    }

    // RPC requester
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

## Text / Translation

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

기본 원칙:
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
- `create(...)`: 참조/ID -> 실제 아이템 생성
- `displayName(...)`, `icon(...)`, `ids()`, `exists(...)`

## 운영 명령

- `/stlib` : STLib 런타임 상태
- `/stlib reload` : 설정/번역/관제 리로드
- `/stlibgui` : STPlugin Core Ops 대시보드

## 참고

현재 저장소는 빠르게 진화 중이라, API 변경 시 README 예제가 먼저 갱신될 수 있습니다.
