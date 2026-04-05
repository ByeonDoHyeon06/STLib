---
name: stlib
description: >
  Use this skill for STLib implementation/review/docs sync. Covers STPlugin lifecycle,
  CommandAPI thin-wrapper DSL, plugin-injected STCommand/STListener, reflective DI
  auto-scan, unified GUI DSL, scheduler hybrid API, Bridge v2 (typed pub/sub + target-node
  RPC + Redisson fallback), storage/resource/translation integrations, capability gating,
  and config bootstrap/migration.
---

# STLib Skill

## 목적

이 스킬은 "현재 STLib 구현과 문서를 정확히 일치"시키는 데 초점을 둡니다.
코드 수정 시 항상 `framework:api` 계약 -> `framework:bukkit` 구현 -> `framework:core` 운영 플러그인 순서로 검증합니다.

## 빠른 실행 순서

1. 레이어를 먼저 고릅니다: `api` / `bukkit` / `core` / `kernel`.
2. 공개 API 변경 여부를 판단합니다.
3. 변경 시 Capability degrade 정책(OFF + reason)을 함께 반영합니다.
4. `README.md`와 이 스킬 문서를 같이 갱신합니다.
5. 최소 검증(`:framework:api:test`, `:framework:bukkit:test`, `:framework:core:test`)을 수행합니다.

## 모듈 경계 (필수)

- `framework:api`
  - 플랫폼 타입 금지(Bukkit/Paper 클래스 참조 금지)
  - 계약/모델/DSL만 정의
- `framework:kernel`
  - 플랫폼 비종속 엔진/레지스트리/서비스 wiring 코어
- `framework:bukkit`
  - Bukkit/Paper/Folia 구현, CommandAPI 바인딩, 이벤트/GUI/DI/브리지 런타임
  - `lifecycle/support`에 보조 오케스트레이션/호환성/캐시 유틸 배치
- `framework:core`
  - STLib 운영 플러그인(`/stlib`, `/stlibgui`, health/dashboard/reload/banner)

## 현재 STPlugin 표면 (정확 기준)

### 서비스 프로퍼티

`STPlugin`은 아래 서비스를 protected property로 제공합니다:

- `text: TextService`
- `translation: TranslationService`
- `notifier: NotifierService`
- `scheduler: SchedulerService`
- `commandRegistrar: CommandRegistrar`
- `eventRegistrar: EventRegistrar<Listener>`
- `configService: ConfigService`
- `configRegistry: ConfigRegistry`
- `storageApi: StorageApi`
- `storage: Storage`
- `bridge: BridgeService`
- `guiService: StGuiService`
- `resource: ResourceService`
- `pluginConfig: PluginFileConfig`

### 핵심 헬퍼

- text/translation
  - `mini(message, placeholders)`
  - `mini(sender, message, placeholders, usePlaceholderApi=true)`
  - `translate(...)`, `sendTranslated(...)`, `reloadTranslations()`
  - `send(sender, ...)`, `console(...)` (간단 경로)
- scheduler
  - legacy: `sync`, `async`, `later(ticks)`, `timer(ticks,ticks)`
  - high-level: `later(delay, unit)`, `asyncLater(...)`, `timer(delay, period, unit)`, `asyncTimer(...)`
- command/event/di
  - `command("name") { ... }`, `command<MyCommand>()`
  - `listen(listener)` / `listen<MyListener>()`
  - `component<T>()`
- bridge
  - `bridgeChannel(...)`, `publish(...)`, `subscribe(...)`, `respond(...)`, `request(...)`

## Lifecycle / 안정성 규칙

순서 고정:

`onLoad -> initialize -> load -> onEnable -> enable -> onDisable -> disable`

중요:
- `onLoad`에서 CommandAPI load + core services + kernel bootstrap + DI scan 수행
- `onEnable`에서 CommandAPI enable 후 실제 기능 활성화
- `onDisable`은 disable pipeline으로 정리(리스너 해제/리소스 정리/커맨드 API 종료)
- 외부 연동 실패는 가능하면 capability degrade로 처리

STLib 배너 정책:
- 배너는 `STPlugin` 공통이 아니라 `framework:core:STLib`에서만 출력
- 타이틀 아트 수정 포인트는 `STLib.stlibTitleAsciiArt()`

## Command DSL (v3, Thin Wrapper)

### 권장 사용

```kotlin
command("test") {
    permission = "example.test"

    literal("show") {
        string("x")
        string("y")
        executes { ctx -> ctx.reply("x=${ctx.stringArgument("x")}, y=${ctx.stringArgument("y")}") }
    }

    literal("clear") {
        executes { ctx -> ctx.reply("cleared") }
    }
}
```

### 클래스형

```kotlin
class TestCommand(plugin: MyPlugin) : STCommand<MyPlugin>(plugin) {
    override val name = "test"

    override fun build(builder: CommandDslBuilder) {
        builder.literal("run") { executes { it.reply("ok") } }
    }
}

command<TestCommand>()
```

### 주의점

- leaf는 반드시 `executes {}` 또는 child literal이 있어야 함
- optional argument 뒤에 required argument 금지
- `CommandTree`는 receiver 스타일 (`fun CommandDslBuilder.define()`)
  - `with(root) { ... }` 패턴을 새 코드에 쓰지 않음

## GUI DSL (Unified)

지원 패턴:
- `set(slot)`, `set(row, column)`, `set(listOf(...))`
- `pattern(...)` + `set(symbol, ...)`
- `fill`, `border`, `row`, `column`
- `onOpen`, `onClose`, `onClick`
- `state(key, value)`, `page(stateKey, stateValue)`, `pageDefault(stateKey)`

핵심 개념:
- `state`: GUI 상태 저장소
- `page`: state 값이 일치할 때만 렌더되는 조건부 레이어
- `pageDefault`: 어떤 page도 매칭되지 않을 때 fallback 레이어

## Event + DI

### STListener

```kotlin
class JoinListener(plugin: MyPlugin) : STListener<MyPlugin>(plugin) {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) { }
}

listen<JoinListener>()
```

### STEvent

`STEvent`는 Bukkit `Event` 상속 커스텀 이벤트 베이스입니다.
`HandlerList` companion 패턴을 반드시 지킵니다.

### DI 자동 스캔

- 스캔 루트: 플러그인 메인 클래스 패키지 루트
- 대상: `@STComponent`
- 주입: `@STInject` 생성자/필드
- 실패 정책: 순환 의존/해결 불가 타입은 fail-fast

## Scheduler (Hybrid)

원칙:
- 기존 Runnable 기반 API는 유지
- 고수준 duration/unit API 추가
- 결과 체인: `onComplete`, `onCompleteSync`, `onCompleteAsync`

클래스로더 안정성 때문에, 런타임 핵심 시그니처에서 Kotlin 함수 타입 직접 노출을 피합니다.

## Bridge v2

기능:
- string pub/sub (호환)
- typed pub/sub (`BridgeCodec<T>`)
- target-node RPC (`request/respond`)
- 응답 상태: `SUCCESS`, `TIMEOUT`, `NO_HANDLER`, `ERROR`

백엔드:
- `LOCAL`, `REDIS`, `COMPOSITE`
- Redisson 연결 실패 시 local fallback + capability reason 기록

## Resource API

통합 대상:
- Vanilla, ItemsAdder, Oraxen, Nexo, MMOItems, EcoItems

원칙:
- provider 미설치/비활성 시 capability OFF + reason
- API는 `resource.items()/blocks()/furnitures()` 공통 경로 우선

## Translation / Text

- `TextService`: MiniMessage 렌더링
- `TranslationService`: locale fallback/키 조회/리로드
- fallback 체인 기본: sender locale -> default locale -> en_us -> `!key!`
- PlaceholderAPI는 선택 의존성

## Config 파일 기대값

기본 위치: `plugins/<Plugin>/config/`

- `plugin.yml`
- `storage.yml`
- `depend.yml`
- `bridge.yml`
- `translation.yml`

번역 번들은 `plugins/<Plugin>/translation/{locale}.yml`.

STLib 운영 플러그인(`framework:core`)은 추가로 `config/stlib.yml`을 사용합니다.

## Capability 키 (자주 보는 것)

- runtime: `runtime:scheduler`, `runtime:di`
- bridge: `bridge:local`, `bridge:distributed`, `bridge:rpc`, `bridge:codec`, `bridge:redis`
- text: `text:translation`, `text:notifier`, `text:placeholderapi`
- ui: `ui:inventory`

기능 degrade 시 항상 OFF reason을 함께 남깁니다.

## 리뷰 체크리스트 (SOLID 실무형)

- SRP: `STPlugin` helper를 무분별하게 늘리지 않았는가
- OCP/DIP: 구현 상세(CommandAPI/Bukkit)를 `api`에 새지 않았는가
- ISP: 외부 개발자가 쓰는 표면이 최소/직관적인가
- 복잡도: 대체 가능한 pass-through 메서드를 중복 추가하지 않았는가
- 안전성: onLoad/onEnable/onDisable 경계에서 side effect가 분리됐는가

## 문서 동기화 규칙

공개 동작이 바뀌면 최소 아래 2개는 반드시 갱신:

- `README.md`
- `.agents/skills/stlib/SKILL.md`

필요 시 함께 갱신:
- `.agents/skills/stlib/references/docs-api/*.md`

## 최소 검증 매트릭스

```bash
./gradlew :framework:api:test
./gradlew :framework:bukkit:test
./gradlew :framework:core:test
```

패키징 스모크:

```bash
./gradlew build
```
