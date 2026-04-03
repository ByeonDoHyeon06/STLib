# 서비스와 Capability

## STKernel

핵심 서비스 컨테이너는 `STKernel`입니다.
기본 구현(`DefaultSTKernel`)은 `framework:kernel` 모듈에 위치합니다.

```kotlin
interface STKernel {
    val capabilityRegistry: CapabilityRegistry
    fun <T : Any> registerService(type: KClass<T>, service: T)
    fun <T : Any> service(type: KClass<T>): T?
}
```

확장 함수:

- `kernel.service<T>()`: nullable 조회
- `kernel.requireService<T>()`: 없으면 예외

## CapabilityRegistry

기능 상태를 문자열 키로 관리합니다.

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

플랫폼 공통 상수는 `platform:common` 모듈의 `CapabilityNames`에 정의되어 있습니다.

| 그룹 | 키 |
| --- | --- |
| Platform | `platform:bukkit`, `platform:folia`, `platform:velocity`, `platform:bungee` |
| Config | `config:configurate`, `config:registry` |
| Text | `text:translation`, `text:command-translation`, `text:notifier`, `text:placeholderapi` |
| Runtime | `runtime:scheduler`, `bridge:local`, `bridge:distributed`, `ui:inventory` |
| Storage | `storage:json`, `storage:jdbc`, `storage:sqlite`, `storage:mysql`, `storage:postgresql` |
| Resource | `resource:minecraft`, `resource:itemsadder`, `resource:oraxen`, `resource:nexo`, `resource:mmoitems`, `resource:ecoitems` |

## 추가 서비스 계약

- `ConfigRegistry`: 등록형 config 관리 + `reloadAll()`
- `NotifierService`: MiniMessage 기반 공통 알림 전송
- `SchedulerService`: sync/async/delay/repeat 스케줄링
- `BridgeService`: publish/subscribe 브리지 계약
- `CommandTranslationService`: `command.<name>.description|usage` 조회

## degrade 전략

STLib는 외부 의존성/플러그인이 없을 때 **전체 부팅을 중단하지 않고** capability를 OFF로 두는 방식을 기본으로 사용합니다.

예시:

- DB 드라이버 로딩 실패: 해당 DB capability OFF, 가능한 백엔드로 fallback
- ItemsAdder 미설치: `resource:itemsadder` OFF, 나머지 provider는 계속 사용
