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

DI 규칙(1차):
- 생성자 주입 기본
- `@STInject` 생성자 우선 선택
- `@STComponent(scope = SINGLETON|PROTOTYPE)` 지원
- `@STInject` 필드 주입 지원(`var`만 가능)

### 2) ST 커스텀 이벤트 발행

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
