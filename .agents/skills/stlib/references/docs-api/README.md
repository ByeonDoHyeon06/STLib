# STLib API 문서

이 문서는 현재 저장소 코드 기준으로 STLib 공개 API를 빠르게 찾고 적용하기 위한 요약 가이드입니다.

## 대상

- Bukkit/Paper/Folia 플러그인 개발자
- `STPlugin` 기반 외부 플러그인 개발자
- STLib 서비스(`Text`, `Translation`, `Config`, `Storage`, `Resource`, `Event`, `Bridge`, `DI`) 사용자

## 모듈 구조

- `framework:api`: 플랫폼 비종속 계약/DSL/어노테이션
- `framework:kernel`: 플랫폼 비종속 엔진(서비스 컨테이너/capability/text baseline)
- `framework:core`: STLib 운영 계층(commands/gui/dashboard/health/runtime config)
- `framework:bukkit`: Bukkit/Paper/Folia 구현(STPlugin, bootstrap, runtime services)

## 문서 목록

- [STPlugin 시작하기](./stplugin.md)
- [서비스와 Capability](./services.md)
- [이벤트 API](./events.md)
- [스토리지 API](./storage.md)
- [리소스 통합 API](./resources.md)
- [번역/i18n API](./translation.md)
- [런타임 서비스 API](./runtime.md)
- [자동 생성 설정 파일](./config-files.md)

## 최신 포인트 (vNext)

- Command: CommandAPI thin-wrapper 트리 DSL + 클래스 기반 `STCommand`
- Event: plugin 주입형 `STListener` + `listen/unlisten/fire`
- DI: `@STComponent/@STInject` + 플러그인 패키지 루트 자동 스캔 + fail-fast 그래프 검증
- Scheduler: 기존 tick API 유지 + duration/unit + completion chain 하이브리드
- Bridge: typed Pub/Sub + target-node RPC + Redisson(옵션) + local fallback
- Config: `plugin.yml`, `storage.yml`, `depend.yml`, `bridge.yml`, `translation.yml` 자동 로드/보정

## 라이프사이클 요약

1. `onLoad()`
2. `initialize()`
3. `load()`
4. `onEnable()`
5. `enable()`
6. `onDisable()`
7. `disable()`

안정성 정책:

- onLoad 준비 단계 실패(CommandAPI/default service/kernel/DI scan) 시 fail-fast로 초기화 중단
- onDisable는 단계별 실패를 로깅하면서 나머지 종료 단계를 계속 수행
