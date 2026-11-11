# 차례로 · CHARERO

**경유지에서 할 일까지**  
차례로는 반복 방문 경로와 장소별 할 일을 연결해, 차량 이동과 정차 후 확인을 하나의 흐름으로 관리하는 Pleos Connect용 Android 앱입니다.

## 사용 방법

1. 정차 상태에서 **퇴근 경유·가족 픽업·업무 방문** 중 반복 방문 루틴을 선택합니다.
2. 경유지 이름과 장소별 할 일을 확인하고, 필요한 경우 수정·저장합니다.
3. 차량 이동 중에는 다음 방문 정보를 간결하게 확인하며 상세 편집·완료 조작은 주행 UX 제한을 따릅니다.
4. 정차 후 점검 항목 3개를 사용자가 직접 확인합니다. 미확인·문제 있음 항목은 완료로 간주하지 않습니다.
5. 확인을 마치면 다음 경유지로 진행하고, 방문 기록은 기기 내에 보존해 이후 운행에 참고합니다.

## 주요 기능과 내비게이션 연동

- 반복 방문 루틴 선택, 경유지·할 일 수정, 로컬 상태 및 방문 이력 저장
- 정차 후 수동 확인과 미완료·문제 항목의 진행 제한
- Pleos 차량 UX 제한에 따른 상세 조작 제어
- **Pleos NaviHelper SDK 3.0.5**를 활용한 내비 상태·경유지·목적지 정보 조회 및 이벤트 수신 코드
- 도착 이벤트와 **수령·인계 업무의 실제 완료는 별도로 관리**합니다. 내비 도착만으로 업무가 자동 완료되지는 않습니다.

내비게이션 전체 경로를 독자적으로 생성·최적화하는 앱이 아닙니다. NaviHelper 연동 범위는 소스의 구현을 기준으로 판단해 주세요.

## 실행 파일

- [서명된 Release APK](apk/Jeongtae_Oh_CHARERO_release.apk)
- [Pleos 시연 영상](demo/CHARERO_Pleos_DEMO_FINAL.mp4)

### Pleos 에뮬레이터에서 설치

Android SDK의 `adb`를 사용하며, 에뮬레이터가 연결되어 있어야 합니다.

```powershell
adb devices
adb -s emulator-5554 install -r "apk/Jeongtae_Oh_CHARERO_release.apk"
adb -s emulator-5554 shell am start -n "io.evidock.app.pleosdev/io.evidock.app.MainActivity"
```

동일한 Android 패키지 ID로 설치된 **다른 서명의 Debug APK**가 있다면 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`이 발생할 수 있습니다. 기존 앱을 삭제하면 내부 저장 기록도 삭제되므로, 데이터를 먼저 보존해야 합니다.

### 소스 빌드

- Android Studio 또는 Windows PowerShell
- JDK 17, Android SDK(compileSdk 34), Gradle Wrapper
- Pleos Playground Maven 저장소 접근 및 관련 SDK 의존성 필요
- `NaviHelper 3.0.5`, `Vehicle 2.3.2` 의존성은 `app/build.gradle.kts`에 선언

```powershell
.\gradlew.bat :app:assembleDebug
```

서명된 Release APK 생성에는 별도로 관리하는 서명 키가 필요합니다. **서명 키와 비밀번호는 저장소에 포함하지 않습니다.**

## 시험 환경과 범위

- 개발·검증 장치: **Pleos Connect 에뮬레이터 `Pleos_Connect_v2.1.0`**
- 공유된 검증 화면 캡처 규격: **2560 × 1440 px**(에뮬레이터 전체 화면; 앱 콘텐츠 영역은 이보다 작음)
- 검증 시 주차 상태: `Current Driving State: 0`
- 사용자 수동 QA 기록: 루틴 선택, 편집, 재시작 후 데이터 유지, 수동 확인, 주행 UX 제한, 내비 응답·도착 이벤트, 화면 가독성 등 8개 항목 통과 보고

QA 판정은 사용자가 Pleos 에뮬레이터에서 직접 확인한 기록입니다. 실제 도착 콜백은 해당 QA에서 확인됐다고 보고됐지만, 공개 저장소에 별도의 재현 로그 파일을 제공하지 않으므로 독립 검증 자료로 간주해서는 안 됩니다. **실차 도로시험이나 별도 안전 인증은 수행했다고 주장하지 않습니다.**

## 제출 정보

현대자동차그룹 차량용 앱 개발 공모전 제출용 Android 프로젝트입니다. 소스 코드는 `app/`, 실행 파일은 `apk/`, 시연 영상은 `demo/`에 있습니다. 제출 시에는 **저장소 URL과 최종 `main` 커밋 SHA**를 함께 기재합니다.

> 앱의 내부 Android applicationId는 기존 개발 이력에 따른 `io.evidock.app.pleosdev`입니다. 이용자에게 표시되는 제품명과 제출 저장소명은 **차례로(CHARERO)**입니다.
