# STLib API 문서

이 문서는 **현재 저장소 코드 기준**으로 STLib의 공개 API를 빠르게 사용할 수 있도록 정리한 가이드입니다.

## 대상

- Bukkit/Paper/Folia 플러그인 개발자
- `STPlugin` 기반으로 플러그인을 작성하려는 외부 개발자
- STLib 서비스(`Text`, `Translation`, `Config`, `Storage`, `Resource`, `Event`)를 사용하는 개발자
- STLib 런타임 서비스(`Notifier`, `Scheduler`, `Bridge`, `Inventory UI`)를 사용하는 개발자

## 모듈 구조 (현재 기준)

- `framework:kernel`: 플랫폼 비종속 엔진 계층 (STKernel 기본 구현, 서비스 컨테이너, capability registry, text baseline)
- `framework:core`: STLib 운영 계층 (STLib 명령/GUI/대시보드/health/runtime config)
- `framework:bukkit`: Bukkit/Paper/Folia 구현 계층 (`STPlugin`, CommandAPI wiring, translation/event/inventory runtime)

## 문서 목록

- [STPlugin 시작하기](./stplugin.md)
- [서비스와 Capability](./services.md)
- [이벤트 API](./events.md)
- [스토리지 API](./storage.md)
- [리소스 통합 API (ItemsAdder/Oraxen/Nexo/MMOItems/EcoItems)](./resources.md)
- [번역/i18n API](./translation.md)
- [런타임 서비스 API (Notifier/Scheduler/Bridge/UI)](./runtime.md)
- [자동 생성 설정 파일](./config-files.md)

구현 메모:

- `STPlugin`은 `VersionedConfig + configMigrationPlan` 기반 Config 마이그레이션을 지원합니다.
- Config I/O는 `ConfigurateConfigService` (`configurate-yaml`) 기반입니다.
- 텍스트 경로는 MiniMessage 기본 + PlaceholderAPI 옵션 통합(`integrations.placeholderApi`)을 지원합니다.
- 명령 경로는 CommandAPI thin-wrapper 트리 DSL(`literal/argument/executes`) 기반입니다.

## 실전 예제

- 소비자 플러그인 샘플 모듈: `stlib-example-consumer`
- 포함 빌드: `./gradlew -PincludeExamples=true :stlib-example-consumer:build`

## 라이프사이클 요약

`STPlugin`은 다음 순서로 훅을 보장합니다.

1. `onLoad()`
2. `initialize()`
3. `load()`
4. `onEnable()`
5. `enable()`
6. `onDisable()`
7. `disable()`

`onDisable()`에서는 `disable()` 실행 후 이벤트 해제(`unlistenAll`)와 커널/CommandAPI 종료가 자동 수행됩니다.

안정성 정책:

- `onLoad()` 준비 단계(CommandAPI load, 기본 서비스 등록, kernel bootstrap) 실패 시 `initialize()/load()`는 실행되지 않습니다.
- `onDisable()`는 단계별 실패를 로깅하면서도 나머지 종료 단계를 계속 실행합니다.
