# Rokid YouTube

Rokid 안경용 유튜브 앱입니다. 화면 전체가 아래쪽 **480dp × 282dp 띠** 안에 그려지고, 띠는 바텀 마진 없이 화면 맨 아래에 붙어 있습니다. 띠 위쪽은 검은색이라 안경에서는 투명하게 보입니다.

유튜브 영상을 불러오고 재생하는 방식은 [RokidPipe](https://github.com/Anezium/RokidPipe)(NewPipe 포크)에서 그대로 가져왔고, 화면은 이 띠 크기에 맞춰 Kotlin으로 새로 만들었습니다. RokidPipe를 고친 버전은 `rokid-hud-band` 브랜치에 있습니다.

## 화면

| 홈 | 검색 키보드 | 플레이어 | 구글 로그인 |
|---|---|---|---|
| ![home](app/src/test/snapshots/images/com.chorockkim.rokidyoutube.ui_HudSnapshotTest_home_rowSelected.png) | ![keyboard](app/src/test/snapshots/images/com.chorockkim.rokidyoutube.ui_HudSnapshotTest_search_keyboard.png) | ![player](app/src/test/snapshots/images/com.chorockkim.rokidyoutube.ui_HudSnapshotTest_player_controls.png) | ![login](app/src/test/snapshots/images/com.chorockkim.rokidyoutube.ui_HudSnapshotTest_account_deviceCode.png) |

위 그림은 [Paparazzi](https://github.com/cashapp/paparazzi)가 실제 레이아웃 코드로 그린 480×640 화면입니다. 썸네일과 영상 화면은 그림 대신 색만 넣었습니다.

## 홈 탭

카테고리 대신 개인 피드입니다. 영상은 위아래로 넘기는 세로 목록이고, 한 줄에 카드 하나씩 한 화면에 3개가 보입니다.

| 탭 | 내용 | 로그인 |
|---|---|---|
| 검색 | 안경용 키보드(영어·숫자), 한국어는 **음성** 키 | 필요 없음 |
| 추천 | 좋아요한 영상의 관련 영상 + 구독 채널 새 영상 + 이 안경에서 본 영상의 관련 영상을 섞어서 보여 줌. 다시 불러올 때마다 순서와 구성이 바뀌고, 직전에 맨 위에 있던 영상은 뒤로 감. 아무것도 없으면 인기 영상 | 없어도 됨 (하면 더 정확) |
| 이어보기 | 보다 만 영상. 누르면 멈춘 곳부터 재생 | 필요 없음 |
| 구독 | 구독 채널의 최신 영상 | 필요 |
| 좋아요 | 좋아요한 영상 | 필요 |
| 로그인 / 계정 | 구글 계정 연결, 로그아웃, 시청 기록 지우기 | |

**추천은 유튜브 알고리즘 그 자체가 아닙니다.** 공식 API에는 "나를 위한 추천" 기능이 없습니다(유튜브 앱 내부 API는 구글 TV 앱을 사칭해야 해서 쓰지 않습니다). 대신 로그인하면 **좋아요한 영상 최근 8개의 관련 영상**과 **구독 채널의 새 영상**을 재료로 써서, 처음부터 내 취향에 맞춰 나옵니다. 여기에 이 안경에서 본 영상의 관련 영상이 더해지고, 이미 본 영상과 좋아요한 영상은 빠집니다. 계정 쪽 재료는 30분 동안 저장해 두고 다시 씁니다(API 사용량은 한 번에 2~5 단위). 시청 기록은 안경 안에만 저장됩니다.

## 조작

| 동작 | 목록·키보드 | 플레이어 (버튼 숨김) | 플레이어 (버튼 보임) |
|---|---|---|---|
| 앞/뒤로 쓸기 | 다음/이전 항목 | 볼륨 5% 올리기/내리기 (메뉴는 안 뜨고 "볼륨 45%"만 잠깐 표시) | 다음/이전 버튼 |
| 탭 | 선택 | 메뉴 보이기 (메뉴는 탭할 때만 뜸) | 버튼 누르기 |
| 뒤로 | 이전 화면 | 플레이어 닫기 | 버튼 숨기기 |

- **플레이어 메뉴:** [멈춤] [-10초] [+10초] [크기] [가림] [480p] [닫기] [종료]. 건너뛰기는 메뉴에서 하고, 메뉴가 닫혀 있을 때 쓸기는 볼륨입니다.
- **영상 크기·볼륨:** **[크기]**를 누르고 앞뒤로 쓸면 10%씩(20~100%) 바뀝니다. 볼륨은 메뉴가 닫힌 상태에서 앞뒤로 쓸거나 볼륨 키로 5%씩 움직입니다. 시스템 볼륨은 단계가 적고 단계마다 커지는 폭이 달라서 한 칸에 소리가 확 커질 수 있는데, 앱이 시스템이 알려 주는 단계별 dB와 플레이어 음량을 함께 써서 한 번 움직일 때마다 같은 크기(약 3dB)만 바뀌게 합니다.
- **가림 (프라이버시 필터):** 플레이어 메뉴의 **[가림]**에서 1~5단계로 켭니다. 단계가 올라갈수록 영상의 어두운 부분을 완전히 검게 만들고 밝은 부분도 어둡게 해서, 앞사람에게 새어 나가는 빛을 줄입니다. 영상에만 적용되고 메뉴 글씨는 그대로입니다. 설정은 다음 영상에도 유지됩니다.
- **화면 꺼짐:** 앱이 열려 있으면 화면이 꺼지지 않습니다. 단, 플레이어에서 **[멈춤]**을 눌렀거나 영상이 끝나면 평소처럼 자동으로 꺼집니다.
- **종료:** 플레이어 메뉴의 **[닫기]**는 목록으로 돌아가고, 빨간 **[종료]**는 앱을 완전히 끕니다.
- **상태 표시:** 띠 바로 위 오른쪽에 시각과 배터리, 인터넷이 끊기면 가운데에 "인터넷 연결 끊김"이 뜹니다. 띠 안은 가리지 않습니다.
- **안경 터치패드:** RokidPipe와 같은 키 매핑입니다. 쓸기는 DPAD 좌우·183/184, 탭은 ENTER·202이고, 한 번 쓸기에 두 번 들어오는 이벤트는 240ms 디바운스로 거릅니다.
- **R08 링 ([R08 Access Bridge](https://github.com/Anezium/R08-Access-Bridge)):** 링 앞/뒤는 다음/이전, 탭은 선택, 더블탭은 뒤로입니다. 브리지는 전용 연동이 없는 앱을 접근성 기능으로 조작합니다. 그래서 이 앱은 링 전용 노드(`RingTarget`) 하나만 노출해서, 브리지가 보내는 스크롤과 클릭을 위 동작으로 바꿉니다. 브리지가 꺼져 있을 때 들어오는 링 미디어 키도 같은 동작입니다.

## 구글 로그인 설정 (구독·좋아요 탭용)

Herald처럼 **구글 공식 로그인**(OAuth 기기 코드 방식)과 **YouTube Data API**(읽기 전용)를 씁니다. 키는 직접 만든 걸 써야 하고, 무료입니다.

1. [Google Cloud Console](https://console.cloud.google.com/)에서 새 프로젝트를 만듭니다.
2. **API 및 서비스 → 라이브러리**에서 **YouTube Data API v3**를 찾아 **사용**을 누릅니다.
3. **Google 인증 플랫폼(OAuth 동의 화면)**을 설정합니다.
   - 사용자 유형은 **외부**, 앱 이름과 이메일은 아무거나 넣으면 됩니다.
   - **대상(Audience)**에서 **앱 게시(프로덕션)**를 누릅니다. "테스트" 상태로 두면 7일마다 다시 로그인해야 합니다.
   - 프로덕션으로 바꾸면 처음 로그인할 때 "Google에서 확인하지 않은 앱" 경고가 뜹니다. **고급 → (앱 이름)(으)로 이동**을 누르면 됩니다. 본인이 만든 앱이라 괜찮습니다.
4. **사용자 인증 정보 → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**에서 애플리케이션 유형을 **TV 및 입력이 제한된 기기**로 고릅니다.
5. 나온 클라이언트 ID와 보안 비밀을 `local.properties`에 넣습니다. 이 파일은 git에 올라가지 않습니다.
   ```properties
   googleClientId=1234567890-xxxx.apps.googleusercontent.com
   googleClientSecret=GOCSPX-xxxx
   ```
   환경 변수 `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`로 넣어도 됩니다(클라우드 빌드 환경용).
6. 다시 빌드해서 설치합니다. 안경 홈 오른쪽 끝 **[로그인]**을 누르고, 폰에서 google.com/device를 열어 안경에 뜬 코드를 입력합니다.

**하루 사용량 한도:** YouTube Data API는 프로젝트당 하루 10,000 단위입니다. 구독 목록이나 좋아요를 한 번 불러오는 데 1~4 단위라 개인용으로는 충분합니다. 구독 채널의 새 영상 자체는 채널 공개 피드(RSS)에서 받아서 한도를 쓰지 않습니다. 한도를 넘으면 태평양 시간 자정에 초기화됩니다.

## RokidPipe에서 가져온 것

| 이 프로젝트 | RokidPipe 원본 |
|---|---|
| `net/OkHttpDownloader.kt` | `DownloaderImpl.java` |
| `potoken/*` (유튜브 봇 확인 토큰, WebView) | `util/potoken/*` |
| `player/YoutubeHttpDataSource.java` | `player/datasource/YoutubeHttpDataSource.java` |
| `player/YoutubeMediaSources.kt` | `player/resolver/PlaybackResolver.java`, `player/helper/PlayerDataSource.java` |
| `player/StreamSelector.kt` | `util/ListHelper.java`, `player/resolver/VideoPlaybackResolver.java` |
| `ui/GlassesInput.kt` | `rokid/RokidKeyMapper.java` |

라이브러리: NewPipeExtractor v0.26.3, ExoPlayer 2.19.1 (RokidPipe와 같은 버전).

## 빌드와 설치

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

디버그 APK는 디버그 키로 서명돼서 OTA 업데이트가 덮어 설치되지 않습니다. 안경에 계속 쓸 앱은 아래 "업데이트 (OTA)"의 `scripts/release.sh`로 만드세요.

## 테스트

```bash
./gradlew :app:testDebugUnitTest          # 입력 매핑, 화질 선택, 시청 기록·추천, 로그인 응답 파싱, R08 링
./gradlew :app:recordPaparazziDebug       # 화면 스냅샷 → app/src/test/snapshots/images
./gradlew :app:testDebugUnitTest -PnetworkTests=true --tests '*YouTubeNetworkTest*'  # 실제 유튜브 접속
```

## 확인한 것과 안 한 것

- **확인함:** 실제 유튜브를 상대로 검색, 인기 영상, 관련 영상(추천 재료), 채널 새 영상 피드(구독 탭 재료), 재생할 스트림 고르기. 구글 기기 코드 서버 응답.
- **안경에서 확인해야 함:** 실제 재생, 터치패드와 R08 링 조작, 음성 인식, 구글 로그인과 구독·좋아요 탭(본인 계정과 키 필요), 글자 가독성.
- PoToken은 안경의 WebView에서 만들어집니다. 안경 WebView가 오래되었으면 재생이 막힐 수 있습니다.

## 업데이트 (OTA)

rokid-conversate 서버(Railway)의 OTA를 같이 씁니다. 이 앱의 APK는 `/data/ota/rokid-youtube/`에 따로 보관되고, 서명 키도 rokid-conversate와 같은 고정 키를 씁니다.

**처음 한 번 (케이블로).** 지금 설치된 앱이 다른 키로 서명돼 있으면 먼저 지워야 합니다(로그인·시청 기록 삭제).
```bash
export RELEASE_KEYSTORE=~/.config/rokid-conversate/release.keystore   # rokid-conversate와 같은 키
export RELEASE_KEYSTORE_PASS=...
scripts/release.sh 0.2.0 --upload        # OTA_UPLOAD_TOKEN 필요 (Railway 변수)
PKG=com.chorockkim.rokidyoutube
adb uninstall $PKG                                     # 키가 바뀌는 처음 한 번만
adb install -r -i $PKG build/rokid-youtube-0.2.0.apk   # -i: 이 앱이 자기 설치자 → 업데이트에 확인 창 없음
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow   # 업데이트 설치 허용
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow        # 업데이트 뒤 앱 다시 띄우기
```

**그 다음부터.**
```bash
scripts/release.sh 0.2.1 --upload        # 버전은 매번 올린다 (versionCode = X*10000 + Y*100 + Z)
```
- 안경은 앱을 켤 때(6시간에 한 번) `GET /apps/rokid-youtube/latest`로 확인합니다. 새 버전이 있으면 띠 위 왼쪽에 초록 "새 버전 vX.Y.Z", 홈 탭 줄 맨 끝 칩이 **[새 버전]**이 됩니다. 칩 → **[받아서 설치]**.
- 받은 뒤 SHA-256, 패키지 이름, 버전, 서명 키가 설치된 앱과 같은지 확인하고 설치합니다. 위처럼 처음에 `-i`로 설치했으면 확인 창 없이 설치되고 앱이 다시 켜집니다. 아니면 시스템 설치 확인 창이 뜹니다.
- 서버: `GET /apps/rokid-youtube/latest`·`/download`는 토큰 없이 공개(이 APK에는 서버 토큰이 없음), `PUT /apps/rokid-youtube/upload`는 `OTA_UPLOAD_TOKEN`.
- **릴리스 키를 잃어버리면 OTA로는 더 이상 업데이트할 수 없습니다** (지우고 케이블로 다시 설치).
- 실기 미확인: 이 앱에서의 무확인 설치와 업데이트 뒤 자동 실행 (rokid-conversate에서는 확인됨).

## 앞으로 할 일

### 그 밖에

- **즉시 화면 끄기:** 동작 하나로 화면만 검게 끄고 소리는 계속 나오게 하기(대화할 때용).
- `hud-fit-and-list` 브랜치를 `main`에 합치기.

## 라이선스

GPL-3.0. NewPipeExtractor와 RokidPipe/NewPipe 코드가 GPL-3.0이기 때문입니다. `YoutubeHttpDataSource`는 ExoPlayer(Apache 2.0) 코드를 기반으로 합니다.
