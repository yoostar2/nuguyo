# 누구요 (nuguyo) — 직원 발신자 팝업 앱 기획/기술 플랜

> 앱에서 직원 명부를 등록·편집해 두고, 그 직원에게서 **전화/문자**가 오면
> 통화 화면 위에 **"누구인지" 팝업**을 띄워 알려주는 앱.
> 타깃: **Galaxy S24 Ultra / Android 16 (API 36, One UI 8)**

---

## 1. 요구사항 정리

### 기능 요구사항
| # | 기능 | 설명 |
|---|---|---|
| F1 | 직원 명부 관리 | 등록 / 수정 / 삭제 / 검색. 이름, 전화번호(복수), 부서, 직급, 사번, 사진, 메모, 태그 |
| F2 | 수신 전화 팝업 | 전화가 울리는 순간, 통화 화면 **위에** 직원 정보 팝업 표시 |
| F3 | 수신 문자 팝업 | SMS/MMS 수신 시 발신자 정보 + 메시지 미리보기 팝업 |
| F4 | 미등록 번호 처리 | 모르는 번호는 팝업 없음(또는 "미등록" 최소 팝업) + 팝업에서 바로 등록 |
| F5 | 통화 이력 | 누가 언제 걸었는지 앱 내부 기록, 팝업에서 메모 즉시 추가 |
| F6 | 데이터 입출력 | CSV/엑셀 가져오기·내보내기, 백업/복원 |
| F7 | 권한 온보딩 | 필요한 권한을 순서대로 안내하는 설정 마법사 |

### 비기능 요구사항
- 팝업 표시 지연 **300ms 이내** (첫 벨소리 전에 떠야 의미가 있음)
- 앱을 안 켜둔 상태(콜드 스타트)에서도 동작
- 삼성 배터리 최적화에 죽지 않을 것
- 직원 개인정보를 다루므로 **로컬 암호화 + 앱 잠금**

---

## 2. 핵심 기술 검토 (Android 16 기준)

### 2-1. "전화 오는 순간 번호를 알아내는" 방법

| 방식 | 가능 여부 | 비고 |
|---|---|---|
| `PHONE_STATE` 브로드캐스트 + `EXTRA_INCOMING_NUMBER` | ❌ 사실상 불가 | Android 10부터 번호가 빠짐. 받으려면 `READ_CALL_LOG` 필요 = Play 정책상 거의 반려 |
| `TelephonyCallback.CallStateListener` | △ | 상태(RINGING/OFFHOOK/IDLE)만 옴, **번호 없음**. 팝업 "닫기" 트리거용으로만 사용 |
| **`CallScreeningService`** | ✅ **채택** | `onScreenCall(Call.Details)`에서 발신번호(`handle`)를 정상 획득. `READ_CALL_LOG` 불필요 |
| `InCallService` (기본 전화앱 되기) | ✅ (대안 B) | 통화 UI를 통째로 대체. 오버레이 권한 불필요·가장 안정적이지만 삼성 전화앱을 버려야 함 |
| `NotificationListenerService`로 전화앱 알림 파싱 | △ (폴백) | 비공식. 역할 권한을 못 받는 환경의 최후 수단 |

**결론:** 주 경로는 `CallScreeningService` + 오버레이 팝업.
단, 이건 **`RoleManager.ROLE_CALL_SCREENING`(발신자 표시 및 스팸 앱) 역할을 앱이 가져와야** 하고,
이 역할은 **기기당 딱 한 앱만** 보유 가능 → 삼성 "스팸 및 통화 차단"이나 후스콜/T전화와 **상호 배타적**.
사용자에게 이 트레이드오프를 온보딩에서 명시해야 함.

### 2-2. "통화 화면 위에 팝업 띄우기"

- **`SYSTEM_ALERT_WINDOW` + `TYPE_APPLICATION_OVERLAY`** 로 `WindowManager`에 뷰를 붙이는 방식. (트루콜러/후스콜 방식, One UI에서도 통화 화면 위에 정상 표시)
- ⚠️ **액티비티를 띄우면 안 됨**: Android 12+ 백그라운드 액티비티 실행(BAL) 제한에 막힘.
- ⚠️ **`USE_FULL_SCREEN_INTENT` 알림도 안 됨**: Android 14+부터 전화/알람 앱이 아니면 자동 부여 안 됨(별도 설정 화면 유도 필요). → 오버레이 실패 시 **헤드업 알림**을 폴백으로.
- 오버레이의 생명주기 홀더로 **포그라운드 서비스** 필요.
  - FGS type은 `phoneCall`을 쓸 수 없음(기본 전화앱/`MANAGE_OWN_CALLS` 필요) → **`specialUse`** + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 선언으로 진행. (Play 심사 시 사유 기재 필요, 사내 배포면 무관)
  - `CallScreeningService` 콜백 처리 중에는 FGS 시작이 일시 허용됨.

### 2-3. 문자(SMS) 수신
- `RECEIVE_SMS` 권한 + `SMS_RECEIVED_ACTION` 브로드캐스트 → PDU 파싱. **기본 문자앱이 될 필요 없음.**
- MMS까지 필요하면 `WAP_PUSH_DELIVER`가 필요한데 기본 문자앱이어야 함 → **1차 범위에서 제외 권장**.
- ⚠️ Play Store 배포 시 SMS 권한은 정책 심사 대상.

### 2-4. 이중 안전장치 — 연락처 동기화 (권장)
직원 명부를 **전용 계정 타입의 연락처**로 `ContactsContract`에 동기화해두면,
오버레이가 실패하거나 역할 권한이 없어도 **삼성 기본 통화 화면에 이름이 그대로 뜸**.
- `WRITE_CONTACTS` 필요, 사용자 개인 연락처와 섞이지 않게 별도 `ACCOUNT_TYPE` 사용
- 앱에서 직원 삭제 시 연락처도 같이 정리
- **팝업(고급 정보) + 연락처(기본 이름) 이중화**가 실전에서 가장 튼튼함

### 2-5. 번호 매칭
- `libphonenumber`로 **E.164 정규화**(region `KR`), DB에 정규화 컬럼 인덱싱
- 폴백: **끝 8자리 비교** (`+8210...` / `010...` / `0212345678` 혼재 대응)
- 발신번호표시제한·안심번호(050x)·대표번호 전환 케이스는 매칭 실패로 처리하고 "미등록" 흐름으로

### 2-6. 삼성/One UI 특유의 함정
1. **배터리 최적화** — 설정 > 배터리 > 앱별 사용량 > **"제한 없음"** 지정 유도 (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
2. **미사용 앱 절전 / 자동 실행 관리**에서 제외
3. **권한 자동 회수(App Hibernation)** 해제 유도 (`ACTION_APPLICATION_DETAILS_SETTINGS`)
4. **"다른 앱 위에 표시"** 권한 (`ACTION_MANAGE_OVERLAY_PERMISSION`)
5. 삼성 기본 스팸 차단이 역할을 잡고 있으면 우리 앱이 역할을 못 받음 → 설정 딥링크 안내

---

## 3. 아키텍처

### 기술 스택
- **Kotlin 2.x**, **Jetpack Compose + Material 3 Expressive**(Android 16 디자인), Coroutines/Flow
- **Room**(SQLCipher 옵션) / **Hilt** / **WorkManager**(백업·동기화)
- `minSdk 29` (역할 API 기준), `targetSdk 36`
- 테스트: JUnit5 + Turbine + Robolectric, 계측테스트는 Compose UI Test

### 모듈 구성
```
:app                     — 진입점, DI, 온보딩/권한 마법사
:core:database           — Room (Employee, PhoneNumber, ContactEvent)
:core:domain             — UseCase, 번호 정규화/매칭 엔진
:core:designsystem       — 공용 Compose 컴포넌트/테마
:feature:directory       — 직원 목록·상세·편집·CSV 입출력
:feature:history         — 수신 이력
:feature:settings        — 권한/동작 설정
:service:screening       — CallScreeningService, SmsReceiver, TelephonyCallback
:service:overlay         — OverlayService(FGS) + Compose 오버레이 렌더러
:service:contactsync     — ContactsContract 동기화
```

### 런타임 흐름 (전화 수신)
```
전화 착신
  └─> StaffCallScreeningService.onScreenCall(details)
        ├─ respondToCall(allow)            ← 절대 차단하지 않음, 즉시 응답
        ├─ handle → E.164 정규화
        ├─ LookupUseCase (메모리 캐시 Map<String, Employee>, 콜드스타트 시 Room 조회)
        └─ 매칭 성공 → startForegroundService(CallerOverlayService, employeeId)
                          └─ WindowManager.addView(ComposeView)  ← 팝업 표시
  TelephonyCallback: OFFHOOK/IDLE 감지 → OverlayService.stopSelf() → 팝업 제거
```
> 매칭 지연을 없애기 위해, 서비스 프로세스 기동 시 **직원 번호 인덱스를 메모리에 프리로드**.

### 오버레이에서 Compose 쓰기
`ComposeView`는 `LifecycleOwner` / `SavedStateRegistryOwner` / `ViewModelStoreOwner`가 붙어야
`WindowManager`에 직접 추가해도 동작 → 서비스에 이 3개를 구현한 경량 오너 클래스 필요. (기술 리스크 항목)

### 팝업 UI 내용
- 상단: 사진 · **이름** · 직급/부서 · 사번
- 중단: 메모(최근 3줄), 태그 뱃지, "최근 통화 N회 / 마지막 통화 O일 전"
- 하단 액션: `메모 추가` `상세 열기` `닫기`
- 문자일 때는 메시지 본문 미리보기 2줄 추가
- 자동 닫힘: 통화 연결/종료 시. 드래그 이동 + 위치 기억.

---

## 4. 데이터 모델 (초안)

```kotlin
@Entity data class Employee(
  @PrimaryKey val id: String,        // UUID
  val name: String,
  val department: String?, val title: String?, val employeeNo: String?,
  val photoUri: String?, val memo: String?,
  val tags: List<String>,            // TypeConverter
  val updatedAt: Long, val deletedAt: Long?   // soft delete (동기화 대비)
)

@Entity(indices=[Index("e164"), Index("tail8")])
data class PhoneNumber(
  @PrimaryKey val id: String, val employeeId: String,
  val raw: String, val e164: String, val tail8: String,
  val label: String?   // 휴대폰/사내/직통
)

@Entity data class ContactEvent(
  @PrimaryKey val id: String, val employeeId: String?, val number: String,
  val type: EventType,  // CALL_INCOMING / SMS_INCOMING / MISSED
  val at: Long, val preview: String?
)
```

---

## 5. 개발 단계 (마일스톤)

### Phase 0 — 스캐폴딩 (0.5일)
Gradle 버전 카탈로그, 모듈 골격, Hilt/Room/Compose 세팅, CI(빌드+유닛테스트)

### Phase 1 — 명부 CRUD (2~3일) ✅첫 실사용 가능 지점
- Room 스키마 + DAO + Repository
- 직원 목록/검색/상세/편집 화면, 사진 첨부, 번호 복수 입력
- 번호 정규화·매칭 엔진 + 유닛테스트(한국 번호 케이스 표)

### Phase 2 — 권한 온보딩 (1~2일)
- 오버레이 권한 / 통화 스크리닝 역할 / READ_PHONE_STATE / RECEIVE_SMS / POST_NOTIFICATIONS / 배터리 예외
- 각 권한 상태를 실시간 표시하는 체크리스트 화면 + 설정 딥링크
- **"테스트 팝업 띄우기"** 버튼 (기기 없이 UI 검증 가능하게)

### Phase 3 — 전화 팝업 (3~4일) ★핵심 리스크 구간
- `CallScreeningService` 구현 + 역할 요청
- `CallerOverlayService`(FGS `specialUse`) + Compose 오버레이 렌더러
- `TelephonyCallback`으로 자동 닫힘
- 헤드업 알림 폴백 경로
- **S24 Ultra 실기기 검증** (다른 전화기로 실제 발신)

### Phase 4 — 문자 팝업 (1~2일)
- `SmsReceiver`, 멀티파트 SMS 결합, 팝업 변형 UI

### Phase 5 — 이력·보조 기능 (2일)
- 수신 이력 화면, 팝업 내 메모 추가, 미등록 번호 즉시 등록

### Phase 6 — 데이터/운영 (2일)
- CSV·엑셀 가져오기/내보내기, 백업·복원
- **연락처 동기화(이중 안전장치)**
- 앱 잠금(BiometricPrompt), DB 암호화

### Phase 7 — 안정화 (2일)
- 콜드스타트 지연 측정, 배터리 영향 점검, 화면 회전/DND/잠금화면 시나리오
- 절전 모드에서 살아남는지 24시간 실사용 테스트

**총 예상: 약 3주 (1인 기준)**

---

## 6. 리스크 & 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 통화 스크리닝 역할을 다른 앱(삼성 스팸차단 등)이 점유 | 팝업 자체가 안 뜸 | 온보딩에서 상태 감지 후 설정 딥링크로 전환 안내. 안 되면 **연락처 동기화 폴백** |
| One UI 통화 화면이 오버레이를 가림 | 핵심 기능 실패 | Phase 3 착수 전 **1일 스파이크로 실기기 PoC 먼저**. 실패 시 **대안 B(기본 전화앱=InCallService)** 로 선회 |
| FGS `specialUse` / SMS / 오버레이 권한이 Play 정책 걸림 | 스토어 배포 불가 | 사내 배포(APK/MDM)면 무관. 스토어 배포 계획이면 Phase 0에서 정책 재검토 |
| 삼성 절전이 서비스를 죽임 | 간헐적 미동작 | 배터리 예외 유도 + 필요 시 상시 FGS 상주 옵션 제공 |
| Compose in WindowManager 오너 구현 이슈 | 팝업 크래시 | 초기엔 XML 뷰로 만들고 나중에 Compose 전환하는 것도 허용 |
| 개인정보(직원 명부) 유출 | 법적 리스크 | 로컬 저장 + DB 암호화 + 앱 잠금, 외부 전송 없음이 기본값 |

### 대안 B: 기본 전화앱(`InCallService`)로 만들기
- 장점: 오버레이 권한 불필요, 통화 화면을 100% 우리가 그림 → 가림/타이밍 문제 원천 제거, 가장 안정적
- 단점: 삼성 전화앱을 대체해야 함(통화 녹음·영상통화 등 삼성 기능 상실). 전화 UI 전체를 직접 구현해야 해서 공수 2~3배
- **사내 업무폰처럼 통제된 환경이면 오히려 대안 B가 정답**일 수 있음

---

## 7. 다음 스텝 (권장)

1. **결정 필요**: 배포 경로(Play Store vs 사내 APK/MDM) — 권한 전략이 갈림
2. **결정 필요**: 데이터 범위(기기 로컬 전용 vs 서버 동기화·다중 사용자)
3. **Phase 0 + 1 착수** (배포 경로와 무관하게 동일)
4. **오버레이 PoC 스파이크**를 Phase 1과 병행해서 S24 Ultra에서 먼저 검증
