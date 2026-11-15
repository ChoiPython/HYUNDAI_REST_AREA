# 쉼자리

개인별 휴식 프로필과 차량 환경을 연결해 정차한 차 안에서 환경 준비, 타이머, 기상 알림과 휴식 기록을 제공하는 Pleos Connect Android 앱입니다.

![휴식 준비 화면](docs/images/home.png)

![어두운 휴식 화면](docs/images/rest.png)

화면은 Pleos Connect 에뮬레이터에서 실행한 체험 모드입니다. 공조 값은 시뮬레이션이며 실제 차량 제어의 검증 결과와는 구분합니다.

## 실행

1. [제출 APK](apk/shimjari_release.apk)를 Android 9(API 28) 이상 기기 또는 Pleos Connect 에뮬레이터에 설치합니다.
2. 처음에는 **체험 모드**로 열립니다. 체험 모드의 공조 값은 앱 내부 시뮬레이션이며 실제 차량을 변경하지 않습니다.
3. 프로필의 별명·좌석·온도·풍량·휴식 시간·소리를 선택합니다.
4. `휴식 준비하기`에서 환경 적용 내역을 확인하고 시작하거나, `타이머만 시작`을 선택합니다.
5. `휴식 마치기`에서 앱이 변경한 설정의 복원 결과를 확인하고 이번 휴식의 느낌을 기록합니다.
6. Pleos 환경에서는 `설정 > Pleos 차량에 연결`을 선택해 실제 SDK 응답을 확인합니다. 연결 실패를 체험 값으로 대체하지 않습니다.

차량의 공용 공조와 스피커는 다른 탑승자에게도 영향을 줄 수 있습니다. 환경을 적용하기 전에 함께 확인해 주세요. 이 앱은 정차 휴식을 위한 프로토타입이며 운전 중 사용하지 않습니다.

## 제공 기능

- 기기 내부 프로필 최대 8개, 별명 변경·선택·삭제
- 좌석별 선호 온도·풍량과 1~180분 휴식 시간 저장
- 공개 Pleos Vehicle SDK 2.3.2의 HVAC capability / get / set 연동
- 공개 AAOS의 P단과 차량 속도 확인, 확인 불가 또는 주행 시 자동 제어 차단
- 시작 전 설정 저장, 명시적 시작 확인, 제어 후 read-back 확인
- 앱이 바꾼 값만 복원 요청; 사용자가 중간에 바꾼 값은 유지
- 단조 시계 기반 타이머, 5분 연장, 앱 재실행 후 세션 복원
- 알림 및 정확한 알람 권한 안내, 기상 알림과 앱 내 기상 화면
- 직접 생성한 잔잔한 선율·빗소리 또는 무음 선택
- 휴식 피드백과 최대 100개 기록, 기록 삭제
- 가로 차량 화면과 세로 화면에 대응하는 네이티브 UI

## 현재 범위

- 좌석 자동 이동, 습도 자동 유지, 도착 ETA 연동과 수면 단계 판정은 포함하지 않습니다.
- 화면에 보이는 선호 온도와 풍량은 사용자 목표값입니다. 실제 차량의 허용 범위와 다르면 적용하지 않습니다. 실내 온도가 목표에 도달했다는 의미도 아닙니다.
- 일부 차량의 공조 영역은 여러 좌석을 공유합니다. 선택 영역의 capability 결과를 확인하며 임의로 다른 영역으로 대체하지 않습니다.
- 차량 API 성공 콜백만으로 적용 성공을 표시하지 않고 다시 읽어 확인합니다. 통신 실패·미지원·복원 실패는 개별 표시합니다.
- 오디오는 앱이 화면에 보이는 동안만 재생하며 앱이 백그라운드로 가면 중지합니다. 안전 안내 등으로 오디오 포커스를 잃으면 중지합니다.
- 정확한 알람 권한이 없으면 절전 중 시스템 알림이 지연될 수 있습니다. 알림 권한이 없으면 백그라운드 알림을 보장하지 않습니다. 화면에는 이 조건을 안내합니다.
- 재부팅하면 OS 알람 예약은 사라집니다. 앱을 다시 열면 저장한 종료 시각으로 상태를 복구합니다. 재부팅 중 자동 알림은 지원하지 않습니다.
- 수면 개선의 의학적 효능과 운전 재개 가능 여부를 판정하지 않습니다. 기록은 주관적 휴식 느낌입니다.

## 개발 환경

- JDK 17 이상, Gradle 8.9
- Android SDK Platform 35 / Build Tools 35.0.0
- 최소 API 28, 대상 API 35
- Java 17, Android 네이티브 View, 공개 SDK의 typed API
- 의존성: `ai.pleos.playground:Vehicle:2.3.2`
- Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`
- AAOS 안전 상태 조회에는 Android 플랫폼에 포함된 `optional/android.car.jar`를 compile-only로 사용합니다.

실제 설치·실행 환경, 화면 규격, 통과한 검사와 미검증 범위는 [검증 기록](docs/VERIFICATION.md)에 기재합니다. 지원 상태는 실제 차량·서비스 버전에 따라 달라질 수 있습니다.

## 빌드

Android SDK를 설치하고 `ANDROID_HOME`을 설정한 뒤 실행합니다.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

macOS/Linux:

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows에서는 Android 도구의 경로 인코딩 문제를 피하도록 저장소와 SDK를 영문 경로에 두는 것을 권장합니다. `android.overridePathCheck`는 경고 우회일 뿐 모든 도구의 한글 경로 호환성을 보장하지 않습니다.

서명된 release APK:

```powershell
$env:SHIMJARI_KEYSTORE = 'C:\private\shimjari.jks'
$env:SHIMJARI_STORE_PASSWORD = '<keystore password>'
$env:SHIMJARI_KEY_PASSWORD = '<key password>'
$env:SHIMJARI_KEY_ALIAS = 'shimjari'
.\gradlew.bat assembleRelease
```

키와 비밀번호는 저장소에 넣지 않습니다. 서명 변수가 없으면 release 산출물은 서명되지 않으므로 설치·제출 전에 서명이 필요합니다. 저장소의 제출 APK는 별도 보관한 제출용 키로 서명합니다.

## 구조

```text
app/src/main/java/io/shimjari/rest/
  MainActivity.java         화면과 사용자 입력
  RestController.java       준비·휴식·기상·복원·평가 흐름
  Store.java                프로필·세션·기록의 기기 내부 저장
  AlarmScheduler.java       기상 알림 예약
  SoundPlayer.java          선택형 오디오와 오디오 포커스
  core/                     타이머·프로필·정차 조건·차량 설정 검증
  vehicle/                  체험 어댑터·공개 Pleos SDK·AAOS 정차 확인
app/src/test/               타이머·상태 불명·지원 범위·설정 복원 테스트
apk/shimjari_release.apk    제출용 APK
docs/VERIFICATION.md        실제 검증 결과와 한계
```

## 개인정보와 오디오

계정, 연락처, 위치와 마이크를 수집하지 않습니다. 앱에 인터넷 권한이 없으며 프로필과 기록은 기기에 저장합니다. 클라우드 백업은 비활성화했습니다. 연락처·서명 등이 있는 신청서는 저장소에 포함하지 않습니다.

차량 연결 시 요청하는 `CAR_SPEED`는 정차 확인을 위한 차량 속도 조회 권한입니다. Pleos의 시스템 권한창에서는 위치 권한으로 표시될 수 있으나 GPS 좌표를 조회하거나 저장하지 않습니다. 거부하면 자동 공조를 적용하지 않습니다.

오디오 두 개는 `tools/generate_sounds.py`로 직접 생성했습니다. 외부 음원이나 스트리밍 계정을 사용하지 않습니다. SDK와 전이 의존성은 각 제공자의 라이선스·약관을 따릅니다. SDK 원본 AAR과 시스템 이미지는 이 저장소에 배포하지 않습니다.

## 기획 근거

차량에서 편안하게 쉬었던 경험을 바탕으로, 여러 환경 설정을 반복하는 부담을 줄이고 개인의 선호를 기억하는 앱을 기획했습니다. 다음 연구는 문제 선택의 근거이며 이 앱의 효능을 입증한 연구가 아닙니다.

- [Ogata et al. 2022: 차량 좌석 각도와 수면](https://doi.org/10.3390/ijerph191912270)
- [Fan et al. 2023: 침실 환기량 개입과 수면](https://doi.org/10.1016/j.scitotenv.2023.163805)
- [Chang et al. 2015: 취침 전 발광 화면과 수면](https://doi.org/10.1073/pnas.1418490112)

초기 사업 가정과 개발 단계는 [기획 요약](docs/CONCEPT.md)에 정리했습니다.
