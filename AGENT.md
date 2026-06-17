# AGENTS.md

This file contains project-specific instructions for AI coding agents and contributors.

## 프로젝트 한 줄 요약

처음부터 완벽한 보안 콘텐츠 수집 플랫폼을 만들지 않는다.
RSS 기반 요약 서비스를 먼저 운영해보면서 실제로 부족한 부분만 점진적으로 보강한다.

---

## 프로젝트 목적

보안 분야 지식을 넓히기 위한 **개인 학습용** 서비스.
취업/면접 대비 콘텐츠가 아니다.

---

## 확정된 방향

- RSS 우선, 크롤링은 실제로 부족함이 확인된 소스에만 추가
- AI는 원문 기반 요약만. 원문에 없는 내용 생성 금지
- 면접 질문, 코드 예시 템플릿 제외
- 과설계 금지. 지금 필요한 것만 만든다
- 점진적 확장: V1 → 운영 → 부족한 부분 확인 → V2

## 아직 확정되지 않은 것 (운영 후 결정)

- rawHtml 저장 방식 (DB / S3)
- Markdown 변환 사용 여부
- SourceConfig 상세 구조
- summaryQuality DB 관리 여부 (현재는 노션/메모로 충분)
- S3 도입 여부
- 하루 N건 발송 여부 (V1은 1건, 운영 후 조정)

지금 단계에서 위 항목을 미리 설계하거나 구현하지 않는다.

---

## 기술 스택

| 항목 | 내용 |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.6 + Spring Framework 7 |
| Build | Gradle (Groovy DSL) |
| DB | MySQL 8.0 + Spring Data JPA |
| Migration | Flyway |
| HTTP Client | WebClient (spring-boot-starter-webflux) |
| RSS Parser | Rome 2.1.0 |
| Resilience | Spring Boot 4 내장 @Retryable |
| Test | JUnit 5 + Spring Boot Test + H2 |
| Container | Docker Compose (로컬 MySQL) |

---

## V1 구현 범위 (지금 만들 것)

```
RSS 수집
↓
Article 저장 (title, link, description)
↓
ArticleSelector로 오늘의 기사 1건 선정
↓
Claude Haiku 요약
↓
summary 저장
↓
Discord Webhook 발송
```

딱 여기까지다. 그 이상은 만들지 않는다.

---

## 패키지 구조

도메인 중심 패키징. 레이어드 구조(controller/service/repository) 사용 금지.

```
com.secdaily
├── BoAnGwanApplication.java
├── config/          # WebClient, Scheduling, 외부 API 설정
├── collector/       # RSS 수집 스케줄러
├── digest/          # ArticleSelector, DigestGenerator, DigestDeliveryService, DigestJob
├── delivery/        # Discord Webhook 발송
├── domain/          # JPA 엔티티
└── repository/      # Spring Data JPA 인터페이스
```

---

## 도메인 모델 (V1)

### Source

RSS 소스 메타데이터.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| name | 소스 이름 |
| rss_url | RSS 피드 URL |
| language | ko / en |
| encoding | UTF-8 / EUC-KR (보안뉴스) |
| guid_type | LINK_IDX / GUID_TAG |
| content_source_type | RSS_ONLY (V1 기본값) |
| priority | 소스 우선순위 (낮을수록 높음, 기본값 10) |
| active | false 시 수집 중단 |

`content_source_type`은 V1에서는 전부 `RSS_ONLY`.
나중에 크롤링이 필요한 소스가 생기면 `CRAWL` 또는 `RSS_PREFERRED`로 변경.

### RawArticle

RSS에서 수집한 원문 기사.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| source_id | FK → Source |
| guid | 소스별 고유 식별자 |
| title | 기사 제목 |
| link | 원문 URL |
| description | RSS 요약본 (Claude에 넘길 입력값) |
| published_at | 발행일 |
| fetched_at | 수집일 |
| status | COLLECTED / SELECTED / SUMMARIZED / FAILED / SKIPPED |

**상태 전이:**
```
COLLECTED  → RSS 수집 완료, 요약 대기 상태
SELECTED   → 오늘의 기사로 선정됨
SUMMARIZED → Claude 요약 완료
FAILED     → Claude 호출 실패 (재처리 가능)
SKIPPED    → 이번 선정에서 제외됨 (다음 날 재후보 가능)
```

`FAILED`는 Claude 호출 단계의 실패만 담당한다.
Discord 발송 실패는 `DeliveryLog.status`가 담당한다.
`FAILED` 기사는 다음 날 `ArticleSelector`가 재선정 가능하다.

`(source_id, guid)` 복합 유니크로 중복 수집 방지.
보안뉴스 guid는 link URL의 idx 파라미터값 (예: "144161").

### DailyDigest

Claude가 생성한 일일 요약.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| raw_article_id | FK → RawArticle |
| digest_date | 날짜 |
| domain | 보안 도메인 분류 |
| tags | JSON 키워드 배열 |
| one_liner | 한 줄 요약 |
| problem | 핵심 내용 |
| risk | 왜 중요한가 |
| impact_target | 영향 대상 |
| action | 대응 방안 (JSON 배열) |
| model_used | 사용 모델명 |
| input_tokens | 입력 토큰 수 |
| output_tokens | 출력 토큰 수 |
| generated_at | 생성일시 |

`digest_date`에 UNIQUE 제약을 걸지 않는다.
V1에서는 하루 1건이지만, 향후 N건으로 확장 가능하도록 열어둔다.
대신 `DigestJob`이 오늘 날짜로 이미 생성된 항목이 있으면 건너뛰는 멱등성 로직을 코드 레벨에서 보장한다.

### DeliveryLog

Discord 전송 이력.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| digest_id | FK → DailyDigest |
| channel | DISCORD |
| status | SUCCESS / FAILED / RETRYING |
| error_message | 실패 사유 |
| retry_count | 재시도 횟수 |
| sent_at | 전송일시 |

---

## DigestJob 내부 구조

`DigestJob`은 조율자 역할만 한다. 비즈니스 로직을 직접 구현하지 않는다.

```java
DigestJob              // @Scheduled 진입점. 세 클래스를 순서대로 호출
        ArticleSelector        // COLLECTED/FAILED 상태 기사 중 오늘의 기사 선정
DigestGenerator        // Claude API 호출 + JSON 파싱 + DailyDigest 저장
        DigestDeliveryService  // Discord Embed 발송 + DeliveryLog 기록
```

**ArticleSelector 선정 기준 (우선순위 순):**
1. status가 `COLLECTED` 또는 `FAILED`인 기사만 후보
2. `Source.priority` 낮은 소스 우선
3. 동일 priority면 `published_at` 최신순
4. 오늘 이미 선정된 기사(`SELECTED` 또는 `SUMMARIZED`)는 제외

이 순서대로 구현한다. 임의로 변경하지 않는다.

---

## Claude 프롬프트 템플릿

AI는 원문 요약만 한다. 원문에 없는 내용을 생성하거나 추론하지 않는다.

```
당신은 보안 기사를 읽고 핵심만 압축하는 요약 도구입니다.
원문에 없는 내용을 추가하거나 추론하지 마세요.
아래 JSON 형식으로만 응답하세요.

{
  "one_liner": "한 문장 요약 (40자 이내)",
  "domain": "보안 도메인 (예: WEB_APP, CRYPTO, CLOUD, NETWORK 등)",
  "tags": ["키워드1", "키워드2", "키워드3"],
  "problem": "무슨 문제인가 — 원문 기반으로만 (200자 이내)",
  "risk": "왜 중요한가 — 원문 기반으로만 (200자 이내)",
  "impact_target": "영향 대상 제품/서비스/사용자 (100자 이내)",
  "action": ["대응 방안 1", "대응 방안 2", "대응 방안 3"]
}

원문에 대응 방안이 없으면 action은 빈 배열로 두세요.
JSON 외 다른 출력 금지.
```

---

## Discord Embed 구조

```
제목: one_liner
색상: 도메인별 고정 색상

📋 핵심 내용: problem
⚠️  왜 중요한가: risk
🎯 영향 대상: impact_target
✅ 대응 방안: action 항목들
🔗 원문 링크: raw_article.link

Footer: domain | tags | model_used
```

---

## 스케줄

| 잡 | 주기 | 설명 |
|---|---|---|
| CollectJob | 매시간 | RSS 수집, 신규 기사만 저장 |
| DigestJob | 매일 08:00 KST | 기사 선정 → Claude 요약 → Discord 발송 |

V1에서는 하루 1건 정책을 적용한다.
DigestJob은 오늘 생성된 DailyDigest가 존재하면 즉시 종료한다.
향후 하루 N건으로 변경할 경우 이 멱등성 로직도 함께 수정해야 한다.

---

## 환경변수

| 변수명 | 설명 |
|---|---|
| ANTHROPIC_API_KEY | Anthropic Console 발급 |
| DISCORD_WEBHOOK_URL | Discord 채널 Webhook URL |
| DB_URL | JDBC URL |
| DB_USERNAME | DB 사용자명 |
| DB_PASSWORD | DB 비밀번호 |

코드에 시크릿 하드코딩 금지.
`application-local.yml`에 보관하고 `.gitignore`로 커밋 제외.

---

## Flyway 규칙

- 스키마 변경은 반드시 Flyway 마이그레이션 파일로 관리
- JPA `ddl-auto`는 `validate` 고정. `create` / `update` 사용 금지
- 새 파일 네이밍: `V{번호}__{설명}.sql` (언더바 두 개)
- 기존 마이그레이션 파일 수정 금지

---

## 구현 규칙

- Java 25 문법 적극 활용 (Record, Pattern Matching 등)
- `@ConfigurationProperties`는 Record로 바인딩
- 엔티티에 `@Setter` 사용 금지. 상태 변경은 도메인 메서드로
- `@Transactional`은 서비스/핵심 클래스에만 선언
- 외부 API 호출 실패는 `@Retryable`로 재시도 (최대 3회, 지수 백오프)
- 스케줄 잡 예외는 다음 실행에 영향 없도록 try-catch 격리

---

## 테스트 전략

**단위 테스트**
- 외부 의존성(WebClient, Claude API, Discord)은 Mock 처리
- 주요 대상: GuidExtractor, ArticleSelector, DigestPromptBuilder, DigestParser, DiscordEmbedBuilder

**통합 테스트**
- DB는 H2 인메모리 + Flyway 마이그레이션 실행
- DigestJob 멱등성 검증 필수 (같은 날 두 번 실행 → DailyDigest 1건)
- ArticleSelector 선정 기준 검증 (priority 순서, 최신순, FAILED 재선정)

```bash
./gradlew test                         # 전체
./gradlew test --tests "*.Digest*"     # 특정 패키지
```

---

## CI (GitHub Actions)

파일: `.github/workflows/ci.yml`
트리거: main push, PR

```yaml
name: CI
on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: gradle-${{ hashFiles('**/*.gradle*') }}
      - name: Build & Test
        run: ./gradlew build
      - name: Upload test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: build/reports/tests/
```

---

## CD (GitHub Actions)

파일: `.github/workflows/cd.yml`
트리거: main push

단일 인스턴스 배포. 스케줄 잡 중복 실행 방지를 위해 인스턴스는 항상 하나.

```yaml
name: CD
on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - name: Build jar
        run: ./gradlew bootJar
      - name: Build & Push Docker image
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker build -t ghcr.io/${{ github.repository }}:latest .
          docker push ghcr.io/${{ github.repository }}:latest
      - name: Deploy to server
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            docker pull ghcr.io/${{ github.repository }}:latest
            docker-compose up -d --no-deps app
```

필요한 GitHub Secrets: `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY`,
`ANTHROPIC_API_KEY`, `DISCORD_WEBHOOK_URL`, `DB_PASSWORD`

---

## 구현 순서

1. 엔티티 + Repository
2. RSS Collector (수집 + 저장)
3. Discord Webhook 발송
4. Claude API 요약 생성
5. DigestJob 통합 (ArticleSelector → DigestGenerator → DigestDeliveryService)
6. 테스트
7. CI/CD

---

## Git 컨벤션

### 브랜치 전략

GitHub Flow 사용. Git Flow(develop, release, hotfix)는 사용하지 않는다.

```
main           ← 항상 배포 가능한 상태. 직접 push 금지
 └─ feature/*  ← 기능 단위 작업 브랜치
```

브랜치명은 구현 순서와 1:1로 대응한다.

```
feature/entity-repository
feature/rss-collector
feature/discord-delivery
feature/claude-digest
feature/digest-job
feature/ci-cd
```

### 커밋 컨벤션

Conventional Commits 사용. 하나의 논리적 변경 = 하나의 커밋.

```
feat:      새 기능
fix:       버그 수정
refactor:  동작 변경 없는 구조 개선
test:      테스트 추가/수정
chore:     빌드, 설정, 의존성
docs:      문서
```

**좋은 예**
```
feat: Source, RawArticle 엔티티 추가
feat: Rome 기반 RSS 파서 구현
feat: 보안뉴스 EUC-KR 디코딩 처리
feat: 매시간 수집 스케줄러 추가
fix: RSS guid 중복 시 저장 실패 처리
test: DigestJob 멱등성 검증 테스트 추가
chore: Spring Boot 4.0.6 업그레이드
refactor: GuidExtractor를 guidType별 전략으로 분리
```

**나쁜 예** — 하나의 커밋에 너무 많은 변경
```
feat: RSS 수집 구현  ← 엔티티+파서+스케줄러+설정이 한 커밋에
wip
오타 수정
다시
```

### PR 단위

브랜치 하나 = PR 하나 = 독립적으로 검증 가능한 기능 한 덩어리.

기준: **이 PR만 머지해도 main이 깨지지 않는가.**

PR 설명에 반드시 포함할 것:
- 무엇을 구현했는가
- 왜 이 방식으로 구현했는가
- 어떻게 검증했는가 (테스트 or 직접 확인 방법)

PR이 파일 15개 이상 또는 변경 400줄 이상이면 분리를 고려한다.

### 머지 방식

**Merge commit 또는 Rebase 유지** — Squash 사용 금지.
커밋을 잘게 나눈 히스토리가 main에 남아야 포트폴리오에서 확인 가능하다.
Squash하면 PR 하나가 커밋 하나로 뭉쳐져 디테일이 사라진다.

---

## 금지 사항

- `application-local.yml` 커밋 금지
- 기존 Flyway 마이그레이션 파일 수정 금지
- JPA `ddl-auto`를 `validate` 외 값으로 변경 금지
- 코드에 API 키 / Webhook URL 하드코딩 금지
- 엔티티에 `@Setter` 사용 금지
- DigestJob 멱등성 체크 로직 제거 금지
- ArticleSelector 선정 기준 임의 변경 금지
- V1 범위를 벗어난 기능 선구현 금지
  (rawHtml 저장, Markdown 변환, S3, summaryQuality 컬럼, 하루 N건 발송 등)