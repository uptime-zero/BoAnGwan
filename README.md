# 보안관 · BoAnGwan

보안 원문을 매시간 수집하고, 매일 한국 시간 오전 9시에 한 분야의 주요 사건을 디스코드로 보내는 개인용 서비스입니다. 해외 자료를 한국어로 요약하며 원문 링크와 AI 사용량을 첨부합니다.

[아키텍처 다이어그램](docs/architecture.html) · [아키텍처 결정 기록](docs/decisions/001-scheduled-briefing.md)

## 구현한 동작

- 클라우드·인프라 → 애플리케이션·공급망 → 단말·계정 → 데이터·개인정보 → AI 보안 순환
- 본문 확보 → 인용 근거 확인 → 사건 통합 → 중요도 선정 → 한국어 요약 → 현재 원문 대조
- 같은 URL·본문·사건·이미 전달한 사실을 단계별로 중복 검사
- 사건 버전 단위로 처리 완료를 기록해 요약에서 생략한 기존 사실도 재발송 후보에서 제외
- 미발송 사건은 발표 후 14일간 후보 유지. 최신 소식과 함께 최대 1건 보충
- 최신 소식이 없는 분야는 미발송 사건만으로 구성. 검증된 사건이 없으면 다음 분야로 이동
- 긴급 사건은 분야와 관계없이 먼저 포함. 일반 사건 수를 조정해 최대 5건
- 기본 3~5분 분량을 목표로 하되, 근거가 적으면 짧게 발송
- 원문 전체 텍스트는 S3에서 30일 만료. 사건·짧은 근거 인용·출처·발송·비용 이력은 유지
- Discord 2,000자 제한에 여유를 두어 분할. 후속 메시지는 알림 억제
- 사용량·캐시 토큰·추정 API 비용·월 누계 표시. 예산 초과 경고 후에도 계속 운영

## 로컬 준비

Python 3.12 이상과 [uv](https://docs.astral.sh/uv/)가 필요합니다.

```powershell
uv sync
uv run pytest -q
uv run ruff check src tests scripts
uv run cfn-lint template.yaml
$env:PYTHONPATH = 'src'
uv run python scripts/check_sources.py
```

테스트는 AWS 모의 서비스와 가짜 HTTP/AI 응답을 사용합니다. 실제 Discord 전송이나 유료 AI 호출은 하지 않습니다. `check_sources.py`는 공개 웹사이트만 읽습니다.

## AWS 배포

[상세 운영 절차](docs/operations.md)를 먼저 확인합니다. AWS CLI, SAM CLI, Docker와 배포 권한이 필요합니다. Python 라이브러리에 네이티브 확장이 있어 Windows에서 생성한 패키지를 그대로 Lambda에 올리지 않고 Linux 컨테이너에서 빌드합니다.

1. AWS Secrets Manager에 JSON 비밀을 생성합니다. 키는 `anthropic_api_key`, `discord_webhook_url`입니다. 값을 코드·명령 기록·Git에 저장하지 마세요.
2. 의존성을 내보내고 빌드합니다.

```powershell
./scripts/export_requirements.ps1
sam build --use-container
sam deploy --guided
```

3. `SecretArn`에 기존 비밀 ARN을 지정합니다. 처음에는 `ScheduleState=DISABLED`로 배포합니다.
4. 배포 환경에서 출처 접근과 모델 사용 권한을 확인하고 수집·초안 생성을 실행합니다. DynamoDB `DRAFT`에서 내용과 원문을 검토합니다.
5. `ScheduleState=ENABLED`로 업데이트하면 실제 자동 수집·유료 AI 처리·Discord 발송이 시작됩니다.

현재 저장소에는 실제 계정 자격 증명이나 웹훅이 포함돼 있지 않습니다. `samconfig.toml`은 로컬 설정이며 Git에서 제외됩니다.

## 구조

```text
매시간 Scheduler → Collector → FIFO SQS → Processor
                                      ├─ 원문 추출 → S3 (30일)
                                      └─ Haiku 추출·중복 판별 → DynamoDB

08시부터 Scheduler → Preparer → 원문 재확인·요약·검증 → 저장된 일일 초안
09시부터 Scheduler → Publisher → 메시지별 상태 기록 → Discord webhook
```

예약 실행은 `Asia/Seoul` 기준입니다. Preparer는 08:00부터 10분마다 실패를 복구하며, 초안이 있으면 추가 AI 호출을 하지 않습니다. Publisher는 09:00부터 10분마다 당일 미완료 발송을 이어갑니다. 23:50 이후에는 그날 요약을 다음 날 밀어 보내지 않습니다. 예약·네트워크 지연 때문에 정확히 09:00:00 도착을 보장하지 않습니다.

오전 9시 전 일시적인 원문·AI 호출 실패는 초안을 확정하지 않고 재시도합니다. 오전 9시 이후에도 실패하면 검증 가능한 다른 사건으로 대체하고, 모두 실패하면 상태 안내를 확정합니다. 한 번 발송을 시작한 초안은 변경하지 않습니다.

| 파일 | 역할 |
|---|---|
| `src/boangwan/sources.py` | 허용한 출처의 URL 발견·본문 추출·발표일 검사 |
| `src/boangwan/domain.py` | 분야 순환·선정·메시지 길이·비용 계산 |
| `src/boangwan/ai.py` | Haiku 호출, 응답 스키마 검사, 토큰·단가 기록 |
| `src/boangwan/pipeline.py` | 수집·사건 통합·초안 생성·발송 |
| `src/boangwan/indexes.py` | 14일 미발송 후보와 최근 중복 비교 인덱스 조회 |
| `src/boangwan/matching.py` | 식별자 기반 후보 검색, 분할된 과거 사실 비교 |
| `src/boangwan/recovery.py` | 결과 불명 전송 확인, 지난 초안 완료·취소 |
| `src/boangwan/storage.py` | DynamoDB/S3 접근, 원자적 전송 상태 선점 |
| `src/sources.json` | 출처와 허용 도메인 목록 |
| `template.yaml` | AWS SAM 인프라 |

## 비용 설정

기본 모델은 `claude-haiku-5-5`입니다. 기본 단가는 2026-10-25 확인한 [공식 모델 문서](https://platform.claude.com/docs/en/models/haiku-5-5/overview)를 기준으로 입력/출력/캐시 생성/캐시 읽기 100만 토큰당 각각 $0.10/$0.50/$0.125/$0.01입니다. 입력 문맥이 10만 토큰을 넘으면 5배 요금을 적용합니다. 현재 요청은 캐시 TTL을 지정하지 않습니다.

모델과 단가는 `AiModel`, `AiRates`를 함께 변경합니다. 응답의 실제 토큰 수와 당시 단가를 기록하며, 캐시 토큰은 일반 입력에 중복 합산하지 않습니다. 응답을 받지 못한 호출은 비용 미확인으로 표시합니다. 확인 가능한 비용은 최종 요약뿐 아니라 분류·사건 통합·검증 호출까지 포함합니다.

월 총액 목표는 1만 원입니다. 초기 AI 경고 예산은 8천 원, 나머지는 인프라 여유분으로 가정했습니다. `UsdKrw=1400`은 **설정된 참고 환율**이며 실시간 시세가 아닙니다. 실제 인프라 비용은 AWS 청구서에서 확인해야 합니다. S3·DynamoDB 백업·Secrets Manager·로그·알람 등 비용이 별도로 발생합니다.

## 정확성과 운영 한계

원문 인용의 실제 존재를 코드로 검사하고, 요약의 각 문장을 AI로 별도 검증합니다. 같은 모델의 검증도 틀릴 수 있으므로 환각을 완전히 제거한다고 보장하지 않습니다. 추출기는 짧은 미리보기·명시된 유료 기사·날짜 없는 자료를 거부하지만, 웹페이지 구조 변경으로 일부 본문을 놓칠 가능성은 있습니다. `check_sources.py`와 원문 대조로 출처를 관리해야 합니다.

사건의 의미상 동일성은 모델 판단을 포함합니다. 영구 식별자 인덱스와 보낸 사실 이력을 사용하지만 완전한 의미 중복 탐지는 보장하지 않습니다. 정확한 사건 키·CVE·제품 식별자·최근 사건 순으로 최대 24개 후보를 고릅니다. 선택된 사건의 과거 사실은 버리지 않고 여러 요청으로 나눠 비교합니다. 식별자 표기가 달라 검색 후보에 들지 않은 사건은 중복을 놓칠 수 있습니다.

웹훅 요청 후 응답이 유실된 경우에는 성공 여부를 알 수 없어 자동 재전송하지 않습니다. [운영 절차](docs/operations.md)에 따라 채널에서 직접 확인한 뒤 해제합니다. 첫 메시지의 실제 알림 발생 여부는 사용자의 Discord 알림 설정에도 따릅니다.

설계 근거와 합의는 [ADR-001](docs/decisions/001-scheduled-briefing.md)에 기록했습니다.

