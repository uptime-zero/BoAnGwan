# 운영 절차

## 계정 연결

배포 전 AWS CLI/SAM CLI/Docker를 설치하고 `aws sso login` 등으로 배포 계정에 로그인합니다. 비밀은 AWS 콘솔의 Secrets Manager에서 생성합니다.

```json
{
  "anthropic_api_key": "실제 API 키",
  "discord_webhook_url": "실제 전용 채널 웹훅 URL"
}
```

위 예시는 형식 설명입니다. 실제 값을 이 파일이나 Git에 기록하지 않습니다. 기본 AWS 관리형 암호화 키를 사용하는 비밀을 가정하며, 고객 관리 KMS 키라면 실행 역할에 해당 키의 복호화 권한을 추가해야 합니다.

`sam deploy --guided`에서 리전(예: ap-northeast-2), 스택 이름(예: boangwan), SecretArn을 설정합니다. 초기 스케줄은 DISABLED입니다. 템플릿은 IAM 역할 생성을 포함합니다. 스택 출력에서 함수명·테이블명·버킷·큐 URL을 확인합니다.

## 처음 실행

AWS Lambda 콘솔에서 Collector에 `{"action":"collect"}`를 실행하면 실제 공개 원문 수집과 SQS 소비가 시작됩니다. Processor는 API 키를 사용하므로 비용이 발생합니다.

1. DynamoDB `HEALTH/COLLECT`와 큐를 확인합니다. `HEALTH/INGEST`는 마지막 분석 성공을 나타냅니다.
2. DLQ에 메시지가 있으면 Processor 로그의 오류 종류를 확인하고 원인을 고친 뒤 SQS redrive로 재처리합니다.
3. Preparer에 `{"action":"prepare"}`를 실행하고 `DRAFT/YYYY-MM-DD`의 `parts`를 검토합니다. 검증된 사건이 없다면 상태 안내가 생성됩니다.
4. 실제 전송을 시작할 준비가 됐을 때 ScheduleState를 ENABLED로 배포합니다. 오전 9시 이후 Publisher에 `{"action":"publish"}`를 실행해 즉시 당일 전송할 수도 있습니다.

수집 테스트만 하려면 `scripts/check_sources.py`를 사용합니다. 실제 배포 리전에서도 출처별 접근 여부를 확인해야 합니다. 국가/IP별 차단 가능성이 있습니다.

## 전송 상태 확인·복구

로컬 운영 스크립트에는 AWS 자격 증명과 아래 환경 변수가 필요합니다.

```powershell
$env:PYTHONPATH = 'src'
$env:TABLE_NAME = '스택 출력 TableName'
$env:BODY_BUCKET = '스택 출력 BodyBucket'
uv run python scripts/delivery_status.py 2026-10-25
```

`sent`는 성공 응답을 받은 메시지, `retry`는 명확한 미전송, `unknown` 또는 이전 실행의 `sending`은 결과 불명입니다. `failed`는 인증 오류·잘못된 메시지 등 수정이 필요한 4xx 오류입니다. 로그에 비밀 URL을 출력하지 않습니다.

디스코드 채널에서 해당 메시지 존재를 직접 확인한 뒤 다음 중 하나를 실행합니다. 파트 번호는 0부터 시작합니다.

```powershell
# 이미 도착한 메시지를 직접 확인했고 그 ID를 아는 경우
uv run python scripts/delivery_status.py 2026-10-25 --part 1 --resolve sent --message-id 실제메시지ID --confirm-checked-channel

# 채널에서 미도착을 확인했거나 4xx 원인을 수정했고 재시도를 원하는 경우
uv run python scripts/delivery_status.py 2026-10-25 --part 1 --resolve retry --confirm-checked-channel
```

이 스크립트 자체는 Discord에 보내지 않습니다. 당일이라면 다음 Publisher 실행에서 이어갑니다. 날짜가 지난 요약은 자동 발송하지 않으며, 미해결 사건도 자동으로 새 요약에 넣지 않습니다.

지난 날짜의 초안은 채널 확인 후 명시적으로 닫습니다.

```powershell
# 모든 메시지가 실제 도착했음을 확인하고 각 파트를 sent로 복구한 경우
uv run python scripts/delivery_status.py 2026-10-25 --close complete --confirm-checked-channel

# 나머지 메시지는 발송하지 않고 초안을 취소하는 경우
uv run python scripts/delivery_status.py 2026-10-25 --close cancel --confirm-checked-channel
```

`complete`는 모든 파트가 `sent`여야 합니다. `cancel`은 `sending`/`unknown`이 남아 있으면 거부됩니다. 먼저 각 파트의 도착 여부를 확인해 `sent` 또는 `retry`로 해소하세요. 취소할 때 전혀 전송되지 않은 사건은 다시 후보가 됩니다. 개요나 본문에서 이미 노출된 사건은 같은 버전을 재발송하지 않으며, 새로 수집한 중요한 업데이트 버전은 유지합니다. 당일 초안의 수동 종료는 실행 중인 Publisher와 충돌할 수 있어 허용하지 않습니다.

## 출처 관리

`src/sources.json`에서 출처와 허용 호스트를 관리합니다. RSS는 기사 URL 발견에만 쓰고 기사 HTML을 따로 읽습니다. 원문 링크가 다른 도메인으로 이동하면 허용 목록을 검토해야 합니다. 목록에 없는 호스트나 사설 IP로는 요청하지 않습니다.

출처에는 GitHub Security, Google Security, AWS Security Blog, EDPB News, Anthropic Research와 국내 보안뉴스(사건·사고), 전자신문(보안), 해외 The Record, KrebsOnSecurity, BleepingComputer, SecurityWeek의 RSS가 포함됩니다. RSS에 발행일이 있으면 14일보다 오래된 항목을 링크 발견 단계에서 제외하고, 본문에서도 날짜 범위를 다시 확인합니다. HTML 목록 출처는 기사 링크를 찾은 뒤 본문 페이지에서 발행일을 확인합니다. FTC는 직접 HTTP 요청이 거부돼 기본 비활성화했습니다. Anthropic의 팀 소개 페이지는 목록에서 제외합니다. 출처 추가 시 `check_sources.py`로 최근 원문을 확인합니다. 최근 14일 이내 기사가 없으면 발견 수가 0일 수 있습니다.

본문은 400~120,000자 범위이며, 발표일을 확인할 수 없거나 범위를 벗어나면 제외합니다. 문서에서 본문을 추출하지 못하면 제목/미리보기로 대체하지 않습니다. 해당 출처가 기사 일부만 제공하면서 그 사실을 표시하지 않는 경우 자동 검출이 불완전할 수 있어 출처별 표본 검토가 필요합니다.

## 관측과 비용

- CloudWatch 로그는 14일 유지합니다. 요청 본문·API 키·웹훅을 로그에 기록하지 않습니다.
- DLQ 알람은 생성하지만 개인 SNS 주소가 없어 알람 전송 대상은 지정하지 않았습니다. AWS 콘솔에서 알람을 확인하거나 SNS 수신 대상을 연결하세요.
- `USAGE`의 `known`은 토큰 확인 완료, `pending`/`unknown`은 실제 비용이 불명확할 수 있는 호출입니다.
- 비용 보고가 끝난 뒤 완료된 AI 호출은 다음 보고에 포함합니다.
- 비용은 참고값입니다. 실제 청구서는 Anthropic과 AWS에서 확인합니다. 계정 결제 한도·크레딧 소진은 코드의 계속 발송 정책과 별개입니다.
- 발송 이력은 삭제하면 중복 방지 근거가 사라집니다. DynamoDB 복구와 스택 삭제 시 유의하세요.

테이블과 본문 버킷은 스택 삭제 시에도 보존하도록 설정했습니다. 보존 자원과 Secrets Manager 비밀의 비용은 계속 발생할 수 있습니다. 서비스 종료 시 실제 대상 리소스를 확인한 뒤 별도로 정리합니다.
