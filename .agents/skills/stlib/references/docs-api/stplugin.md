# STPlugin 시작하기

`studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin`은 Bukkit/Paper/Folia 기준의 STLib 기본 베이스 클래스입니다.

구조 참고:

- `STPlugin` 자체는 `framework:bukkit`에 있습니다.
- STLib 중앙 운영 플러그인 구현(`STLib.kt`, 대시보드/헬스/명령)은 `framework:core`에 있습니다.
- 플랫폼 비종속 커널 구현은 `framework:kernel`에 있습니다.

## 최소 예제

```kotlin
package my.plugin

import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin

class MyPlugin : STPlugin() {
    override fun enable() {
        command(
            name = "myplugin",
            description = "MyPlugin status",
            permission = permission("command"),
        ) { ctx ->
            ctx.reply("Hello, ${ctx.senderName}")
            ctx.audience?.let { audience ->
                announce(audience, "<green>hello from notifier")
            }
        }
    }
}
```

## 내장 서비스 프로퍼티

`STPlugin`은 아래 서비스를 `protected`로 바로 제공합니다.

| 프로퍼티 | 타입 | 역할 |
| --- | --- | --- |
| `capabilityRegistry` | `CapabilityRegistry` | 기능 활성/비활성 상태 조회 |
| `text` | `TextService` | MiniMessage 파싱 |
| `translation` | `TranslationService` | 번역 키 조회/리로드 |
| `commandTranslation` | `CommandTranslationService` | 명령 설명/사용법 번역 키 조회 |
| `notifier` | `NotifierService` | 공통 메시징/액션바/타이틀 전송 |
| `scheduler` | `SchedulerService` | sync/async/delay/repeat 스케줄링 |
| `commandRegistrar` | `CommandRegistrar` | CommandAPI 기반 명령 등록 |
| `eventRegistrar` | `EventRegistrar` | STListener 등록/해제 |
| `configService` | `ConfigService` | Configurate 기반 파일 로드/저장 |
| `configRegistry` | `ConfigRegistry` | 등록형 config 관리/일괄 리로드 |
| `storageApi` | `StorageApi` | 저장소 인스턴스 생성 팩토리 API |
| `storage` | `Storage` | 기본 저장소 인스턴스 |
| `bridge` | `BridgeService` | 인메모리 메시지 브리지 |
| `inventoryUi` | `InventoryUiService` | Bukkit 인벤토리 UI 빌더 |
| `resource` | `ResourceService` | 통합 리소스 루트 서비스 (items/blocks/furnitures) |
| `pluginConfig` | `PluginFileConfig` | `config/storage.yml`, `config/depend.yml` 묶음 |
| `ui` | `InventoryUiToolkit` | 인벤토리 UI 파사드 (`ui.menu`, `ui.open`) |

## 자주 쓰는 헬퍼 메소드

| 메소드 | 설명 |
| --- | --- |
| `mini(message, placeholders)` | MiniMessage 문자열을 `Component`로 변환 |
| `mini(sender, message, placeholders, usePlaceholderApi)` | sender 컨텍스트 기준 텍스트 파싱 (PAPI 옵션 적용) |
| `translate(key, placeholders)` | 기본 locale 번역 후 `Component` 반환 |
| `translate(sender, key, placeholders)` | sender locale 우선 번역 후 `Component` 반환 |
| `sendTranslated(sender, key, placeholders)` | 번역 메시지 전송 |
| `reloadTranslations()` | 번역 번들 수동 리로드 |
| `commandDescription/commandUsage` | 명령 메타 번역 조회 |
| `commandTranslated(name, permission) { ... }` | `command.<name>.description` 기반 트리 DSL 명령 등록 |
| `tell/announce/tellTranslated` | Audience 대상 일반/접두어/번역 메시지 전송 |
| `actionBar/title/broadcast/broadcastTranslated` | 공통 알림 헬퍼 |
| `sync/async/later/timer` | 스케줄러 헬퍼 |
| `send(sender, message, placeholders)` | `CommandSender`로 sender-aware 텍스트 전송 (기본 PAPI ON) |
| `console(message, placeholders)` | 콘솔로 MiniMessage 전송 |
| `command(name) { ... }` | 트리 DSL 명령 등록(등록/실행 통계 자동 집계) |
| `permission(node)` | `pluginName.node` 형식 권한 문자열 생성 |
| `can(sender, node)` | `permission(node)` 기준 권한 체크 |
| `configPath(fileName)` | 데이터 폴더 기준 config 파일 경로 생성 |
| `loadConfig/reloadConfig/saveConfig` | 타입 안전 설정 파일 입출력 |
| `registerConfig/currentConfig/reloadAllConfigs` | 등록형 config 관리 |
| `registerConfig(..., migrationPlan)` | 버전형 config 자동 마이그레이션 등록 |
| `reloadConfig(..., migrationPlan)` | 버전형 config 리로드 + 마이그레이션 |
| `publish/subscribe/unsubscribe` | 브리지 메시지 송수신 |
| `ui.menu/ui.open` | 인벤토리 UI 생성/오픈 |
| `registerResourceProvider/unregisterResourceProvider` | 커스텀 리소스 provider 동적 등록 |
| `allPlugins()/findPlugin(name)` | STPlugin 레지스트리 조회 |

Command Tree DSL (Thin Wrapper) 예시:

```kotlin
command("demo") {
    description = "tree command demo"
    permission = permission("demo")
    aliases("d")

    literal("show") {
        string(name = "x")
        string(name = "y")
        executes { context ->
            val x = context.stringArgument("x") ?: return@executes
            val y = context.stringArgument("y") ?: return@executes
            context.reply("<green>show</green> x=$x y=$y")
        }
    }

    literal("admin") {
        permission = permission("demo.echo")
        sender(CommandSenderConstraint.PLAYER_ONLY)
        literal("echo") {
            greedyString(name = "message")
            executes { context ->
                val message = context.stringArgument("message") ?: return@executes
                context.reply("<green>echo=</green>$message")
            }
        }
    }

    literal("target") {
        player(name = "player")
        world(name = "world")
        location(name = "spawn")
        executes { context ->
            val player = context.playerArgument("player") ?: return@executes
            val world = context.worldArgument("world") ?: return@executes
            val location = context.locationArgument("spawn") ?: return@executes
            context.reply("player=${player.name}, world=${world.name}, loc=${location.blockX},${location.blockY},${location.blockZ}")
        }
    }

    executes { context ->
        context.reply("usage: /demo <show|admin|target>")
    }
}
```

지원 범위:

- nested literal 트리 (`literal { literal { ... } }`)
- node-level `permission`, `aliases`, `sender`, `requires`
- node-level static/dynamic suggestions
- typed helpers:
  - `string`, `greedyString`, `int`, `double`, `boolean`
  - `player`, `offlinePlayer`, `world`, `location`

`CommandContext.reply(String)` 동작:

- Bukkit/Paper 경로에서는 문자열 응답을 MiniMessage로 파싱해 전송합니다.
- MiniMessage 파싱 실패 시 원문 문자열로 fallback 전송합니다.

## 플러그인 레지스트리 조회

`STPlugin`은 로드된 STPlugin 정보를 관리합니다.

```kotlin
val snapshots = allPlugins()
val thisPlugin = findPlugin(name)
```

정적 접근도 가능합니다.

```kotlin
val snapshots = STPlugin.plugins()
val target = STPlugin.plugin("SomePlugin")
```

## 라이프사이클 안전성

- `onLoad`는 내부 준비 단계( CommandAPI load, 기본 서비스 등록, kernel bootstrap ) 중 하나라도 실패하면 `initialize()`/`load()`를 호출하지 않습니다.
- `onDisable`은 `disable -> unlistenAll -> cleanup -> kernel shutdown -> commandapi shutdown` 순서를 유지하며, 특정 단계가 실패해도 나머지 단계는 계속 실행됩니다.

## Config Versioning/Migration

- 버전형 config는 `VersionedConfig`를 구현하고 `version` 필드를 가집니다.
- 마이그레이션 플랜은 `configMigrationPlan(latestVersion) { step(from, to) { ... } }`으로 선언합니다.
- `registerConfig(..., migrationPlan)` 또는 `reloadConfig(..., migrationPlan)`을 사용하면 자동 승격됩니다.
- 마이그레이션이 실행되면 기존 파일은 `.backup/` 디렉터리에 백업 후 저장됩니다.
- 마이그레이션은 연속 step 체인이 필요합니다. (예: `1->2`, `2->3`)
- 현재 config `version`이 `latestVersion`보다 크면 실패합니다.

주의:

- `loadConfig(...)` / `configService.load(...)` 경로는 마이그레이션 플랜을 적용하지 않습니다.
- 버전 승격이 필요한 config는 `registerConfig(..., migrationPlan)` 기반으로 운용하세요.
