# 서비스와 Capability

## STKernel

```kotlin
interface STKernel {
    val capabilityRegistry: CapabilityRegistry
    fun <T : Any> registerService(type: KClass<T>, service: T)
    fun <T : Any> service(type: KClass<T>): T?
}
```

확장:

- `kernel.service<T>()`
- `kernel.requireService<T>()`

## CapabilityRegistry

```kotlin
interface CapabilityRegistry {
    fun enable(capability: String)
    fun disable(capability: String, reason: String)
    fun isEnabled(capability: String): Boolean
    fun reason(capability: String): String?
    fun snapshot(): Map<String, CapabilityState>
}
```

## 기본 Capability 키

`platform/common/.../CapabilityNames.kt` 기준:

| 그룹 | 키 |
| --- | --- |
| Platform | `platform:bukkit`, `platform:folia`, `platform:velocity`, `platform:bungee` |
| Config | `config:configurate`, `config:registry` |
| Text | `text:translation`, `text:notifier`, `text:placeholderapi` |
| Runtime | `runtime:scheduler`, `runtime:di` |
| Bridge | `bridge:local`, `bridge:distributed`, `bridge:rpc`, `bridge:codec`, `bridge:redis` |
| UI | `ui:inventory` |
| Storage | `storage:json`, `storage:jdbc`, `storage:sqlite`, `storage:mysql`, `storage:postgresql` |
| Resource | `resource:minecraft`, `resource:itemsadder`, `resource:oraxen`, `resource:nexo`, `resource:mmoitems`, `resource:ecoitems` |

## 주요 서비스 계약

- `ConfigRegistry`: 등록형 config + `reloadAll`
- `NotifierService`: 공통 메시지/액션바/타이틀
- `SchedulerService`: tick API + high-level delay/repeat spec
- `BridgeService`: string + typed pub/sub + request/respond
- `TranslationService`: key lookup/fallback/reload
- `EventRegistrar`: listen/unlisten/unlistenAll

## degrade 정책

STLib 기본 정책은 “코어 지속 + 기능 단위 OFF”입니다.

예시:

- DB 드라이버 로딩 실패 => 해당 DB capability OFF, 가능한 backend로 fallback
- PlaceholderAPI 미설치 => `text:placeholderapi` OFF
- Redis bridge 실패 => `bridge:redis`/`bridge:distributed` OFF, local bridge로 degrade
- 외부 resource plugin 미설치 => 해당 resource capability OFF
