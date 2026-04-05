# STPlugin 시작하기

`studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin`은 Bukkit/Paper/Folia용 STLib 베이스 클래스입니다.

구조 참고:

- `STPlugin` 구현은 `framework:bukkit`
- STLib 중앙 운영 플러그인(`STLib`)은 `framework:core`
- 플랫폼 비종속 엔진/커널은 `framework:kernel`

## 최소 예제

```kotlin
package my.plugin

import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin

class MyPlugin : STPlugin(version = "1.0.0") {
    override fun enable() {
        command("myplugin") {
            description = "MyPlugin status"
            permission = permission("command")
            executes { ctx ->
                ctx.reply("Hello, ${ctx.senderName}")
            }
        }
    }
}
```

## 내장 서비스 프로퍼티

`STPlugin`은 아래 서비스를 `protected`로 제공합니다.

| 프로퍼티 | 타입 | 역할 |
| --- | --- | --- |
| `capabilityRegistry` | `CapabilityRegistry` | 기능 활성/비활성 조회 |
| `text` | `TextService` | MiniMessage 파싱 |
| `translation` | `TranslationService` | 번역 키 조회/리로드 |
| `notifier` | `NotifierService` | 공통 메시징/액션바/타이틀 |
| `scheduler` | `SchedulerService` | sync/async/delay/repeat 스케줄링 |
| `commandRegistrar` | `CommandRegistrar` | CommandAPI 기반 명령 등록 |
| `eventRegistrar` | `EventRegistrar<Listener>` | Bukkit Listener 등록/해제 |
| `configService` | `ConfigService` | Configurate 파일 로드/저장 |
| `configRegistry` | `ConfigRegistry` | 등록형 config 관리/일괄 리로드 |
| `storageApi` | `StorageApi` | 저장소 팩토리 API |
| `storage` | `Storage` | 기본 저장소 인스턴스 |
| `bridge` | `BridgeService` | 브리지(pub/sub, RPC) |
| `guiService` | `StGuiService` | 통합 GUI 생성/오픈 |
| `resource` | `ResourceService` | 통합 리소스(items/blocks/furnitures) |
| `pluginConfig` | `PluginFileConfig` | `plugin/storage/depend/bridge/translation` 설정 접근 |

## 자주 쓰는 헬퍼 메소드

| 메소드 | 설명 |
| --- | --- |
| `mini(message, placeholders)` | MiniMessage 문자열을 `Component`로 변환 |
| `mini(sender, message, placeholders, usePlaceholderApi)` | sender 컨텍스트 파싱 |
| `translate(key, placeholders)` | 기본 locale 번역 후 `Component` 반환 |
| `translate(sender, key, placeholders)` | sender locale 우선 번역 후 `Component` 반환 |
| `sendTranslated(sender, key, placeholders)` | 번역 메시지 전송 |
| `reloadTranslations()` | 번역 번들 수동 리로드 |
| `send(sender, message, placeholders)` | `CommandSender`로 sender-aware 텍스트 전송 |
| `console(message, placeholders)` | 콘솔로 MiniMessage 전송 |
| `broadcast/broadcastTranslated` | 전체 브로드캐스트 |
| `sync/async/later/timer` | 스케줄러 헬퍼 |
| `command(name) { ... }` | 트리 DSL 명령 등록(통계 계측 포함) |
| `command<T : STCommand<*>>()` | STCommand 클래스형 등록(DI 생성) |
| `listen(listener)` | Bukkit `Listener` 등록 |
| `listen<T : STListener<*>>()` | STListener 클래스형 등록(DI 생성) |
| `unlisten(listener)` / `unlistenAll()` | 리스너 해제 |
| `fire(event)` | STEvent 발행 |
| `component<T>()` | DI 컨테이너에서 컴포넌트 resolve |
| `permission(node)` / `can(sender,node)` | 권한 문자열 생성/검증 |
| `loadConfig/reloadConfig/saveConfig` | 타입 안전 설정 파일 입출력 |
| `registerConfig/currentConfig/reloadAllConfigs` | 등록형 config 관리 |
| `bridgeChannel/publish/subscribe/respond/request` | 브리지 송수신/RPC |
| `registerResourceProvider/unregisterResourceProvider` | 커스텀 리소스 provider 동적 등록 |
| `allPlugins()/findPlugin(name)` | STPlugin 레지스트리 조회 |

## Command Tree DSL 예시

```kotlin
command("demo") {
    description = "tree command demo"
    permission = permission("demo")
    aliases("d")

    literal("show") {
        string("x")
        string("y")
        executes { context ->
            val x = context.stringArgument("x") ?: return@executes
            val y = context.stringArgument("y") ?: return@executes
            context.reply("<green>show</green> x=$x y=$y")
        }
    }

    literal("target") {
        player("player")
        world("world")
        location("spawn")
        executes { context ->
            val player = context.playerArgument("player") ?: return@executes
            val world = context.worldArgument("world") ?: return@executes
            val location = context.locationArgument("spawn") ?: return@executes
            context.reply("player=${player.name}, world=${world.name}, loc=${location.blockX},${location.blockY},${location.blockZ}")
        }
    }

    executes { context ->
        context.reply("usage: /demo <show|target>")
    }
}
```

지원 범위:

- nested literal 트리 (`literal { literal { ... } }`)
- node-level `permission`, `aliases`, `sender`, `requires`
- static/dynamic suggestions
- typed helpers: `string`, `greedyString`, `int`, `double`, `boolean`, `player`, `offlinePlayer`, `world`, `location`

## 플러그인 레지스트리 조회

```kotlin
val snapshots = allPlugins()
val thisPlugin = findPlugin(name)
```

정적 접근:

```kotlin
val snapshots = STPlugin.plugins()
val target = STPlugin.plugin("SomePlugin")
```

## 라이프사이클 안전성

- `onLoad`: CommandAPI load -> 기본 서비스 등록 -> kernel bootstrap -> DI scan
- 준비 단계 실패 시 `initialize()/load()`를 진행하지 않음
- `onDisable`: `disable -> unlistenAll -> cleanup -> kernel shutdown -> commandapi shutdown` 순서
- disable 중 일부 단계 실패해도 나머지 단계는 계속 수행

메모:
- STLib 배너 출력은 `STPlugin` 공통 기능이 아니라 `framework:core:STLib`에서만 처리합니다.

## Config Versioning/Migration

- 버전형 config는 `VersionedConfig` + `version` 필드
- 마이그레이션은 `configMigrationPlan(latestVersion) { step(from, to) { ... } }`
- `registerConfig(..., migrationPlan)` 또는 `reloadConfig(..., migrationPlan)` 경로에서 자동 적용
- 실행 시 기존 파일은 `.backup/`에 백업

주의:

- `loadConfig(...)` / `configService.load(...)`는 마이그레이션 플랜을 적용하지 않음
- 버전 승격이 필요한 config는 등록형 경로를 사용
