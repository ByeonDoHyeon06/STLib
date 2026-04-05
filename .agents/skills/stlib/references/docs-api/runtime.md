# 런타임 서비스 API

STLib는 Bukkit 런타임에서 아래 공통 서비스를 기본 등록합니다.

- `NotifierService`
- `SchedulerService`
- `BridgeService` (mode: local/redis/composite)
- `StGuiService` (Unified GUI DSL)

추가로 `framework:bukkit:lifecycle/support`에는 STPlugin 보조 구성요소가 분리되어 있습니다.
- `CachedServicePropertyDelegate`
- `PluginCompatibilityVerifier`
- `PluginLoadRuntimeCoordinator`

## NotifierService

```kotlin
interface NotifierService {
    fun message(template: String, placeholders: Map<String, String> = emptyMap()): Component
    fun prefixed(template: String, placeholders: Map<String, String> = emptyMap()): Component
    fun send(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())
    fun sendPrefixed(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())
    fun actionBar(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())
    fun title(target: Audience, titleTemplate: String, subtitleTemplate: String = "", placeholders: Map<String, String> = emptyMap())
}
```

`STPlugin`에서는 기본적으로 아래 경로를 권장합니다.

- 간단 전송: `send(sender, ...)`, `console(...)`
- 확장 전송: `notifier.send(...)`, `notifier.sendPrefixed(...)`, `notifier.actionBar(...)`, `notifier.title(...)`
- 전체 전송: `broadcast(...)`, `broadcastTranslated(...)`

## SchedulerService (Hybrid)

기존 API + 고수준 스케줄 스펙을 함께 제공합니다.

```kotlin
interface SchedulerService {
    fun runSync(task: Runnable): ScheduledTask
    fun runAsync(task: Runnable): ScheduledTask
    fun runLater(delayTicks: Long, task: Runnable): ScheduledTask
    fun runTimer(delayTicks: Long, periodTicks: Long, task: Runnable): ScheduledTask

    fun runDelayed(schedule: DelaySchedule, task: Runnable): ChainedScheduledTask
    fun runRepeating(schedule: RepeatSchedule, task: Runnable): ChainedScheduledTask

    fun <T> callSync(task: Callable<T>): CompletableFuture<T>
}
```

`ChainedScheduledTask`:

- `cancel()`
- `onComplete(...)`
- `onCompleteSync(...)`
- `onCompleteAsync(...)`

`STPlugin` 헬퍼:

- 기존: `sync`, `async`, `later(ticks)`, `timer(ticks, ticks)`
- 확장: `later(delay, unit)`, `later(Duration)`, `asyncLater(...)`, `timer(delay, period, unit)`, `asyncTimer(...)`

## BridgeService v2

Bridge는 string 경로와 typed 경로를 모두 지원합니다.

핵심 타입:

- `BridgeChannel(namespace, key)`
- `BridgeNodeId`
- `BridgeCodec<T>`
- `BridgeResponseStatus` = `SUCCESS/TIMEOUT/NO_HANDLER/ERROR`

핵심 API:

```kotlin
interface BridgeService {
    fun publish(channel: String, payload: String)
    fun subscribe(channel: String, listener: BridgeListener): BridgeSubscription

    fun publish(channel: BridgeChannel, payload: String)
    fun subscribe(channel: BridgeChannel, listener: BridgeListener): BridgeSubscription

    fun <T : Any> publish(channel: BridgeChannel, payload: T, codec: BridgeCodec<T>)
    fun <T : Any> subscribe(channel: BridgeChannel, codec: BridgeCodec<T>, listener: BridgeTypedListener<T>): BridgeSubscription

    fun <Req : Any, Res : Any> respond(...): BridgeSubscription
    fun <Req : Any, Res : Any> request(...): CompletableFuture<BridgeResponse<Res>>
}
```

운영 모드:

- `LOCAL`: in-memory
- `REDIS`: Redisson 분산
- `COMPOSITE`: local + distributed

분산 실패 시 정책:

- capability OFF + reason 기록
- local 경로로 자동 degrade

## Unified GUI (`StGuiService`)

`StGuiService`는 통합 GUI DSL을 제공합니다.

- 생성: `gui(rows, title) { ... }`, `gui { ... }`, `gui(title, size, type) { ... }`
- 배치: `set(slot)`, `set(row, column)`, `set(symbol)`, `pattern(...)`, `fill`, `border`, `row`, `column`
- 상태: `state`, `page`, `pageDefault`, `refresh`, `reopen`

열기:

```kotlin
val menu = gui(rows = 3, title = "<gold>Menu</gold>") { ... }
player.openInventory(menu)
```

안정성 메모:

- GUI 이벤트는 plugin enable 이후에 활성화됩니다.
- disable 시 GUI 서비스 close + 리스너 정리가 자동 수행됩니다.
