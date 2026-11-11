# 차례로 | CHARERO

**경유지에서 할 일까지**

차례로는 반복 방문 경로와 장소별 할 일을 연결하는
Pleos Connect 차량용 Android 애플리케이션입니다.

## 주요 기능

- 반복 방문 루틴 3종 선택 및 재사용
- 경유지별 장소·할 일 수정과 로컬 저장
- 사용자 확인에 따른 현장 점검 및 다음 방문 진행
- 주행 UX 제한 시 상세 조작 차단
- Pleos NaviHelper 기반 내비게이션 상태·경로 정보 연동
- 방문 완료 이력 보관

## Android 프로젝트

이 저장소의 Android 소스코드는 루트의 `app/` 및
Gradle 프로젝트 파일에 포함되어 있습니다.

## 실행 파일

- [서명된 Release APK](apk/Jeongtae_Oh_CHARERO_release.apk)
- [Pleos 시연 영상](demo/CHARERO_Pleos_DEMO_FINAL.mp4)

## 시험 범위

Pleos Connect 에뮬레이터를 사용하여 기능을 점검했습니다.
루틴 선택, 정보 수정·저장, 수동 점검, 주행 UX 제한,
내비게이션 응답 및 도착 이벤트는 사용자 수동 QA에서
확인한 항목입니다.

실제 차량 도로시험 또는 별도의 안전 인증을
완료했다고 주장하지 않습니다.
