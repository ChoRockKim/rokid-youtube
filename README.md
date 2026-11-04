# Rokid YouTube

Rokid 안경용 유튜브 앱입니다. 화면 전체가 아래쪽 **480dp × 282dp 띠** 안에 그려지고, 띠는 바텀 마진 없이 화면 맨 아래에 붙어 있습니다. 띠 위쪽은 검은색이라 안경에서는 투명하게 보입니다.

유튜브 영상을 불러오고 재생하는 방식은 [RokidPipe](https://github.com/Anezium/RokidPipe)(NewPipe 포크)에서 그대로 가져왔고, 화면은 이 띠 크기에 맞춰 Kotlin으로 새로 만들었습니다. RokidPipe를 고친 버전은 `rokid-hud-band` 브랜치에 있습니다.

## 화면

| 홈 | 검색 키보드 | 플레이어 |
|---|---|---|
| ![home](app/src/test/snapshots/images/com.chorockkim.rokidyoutube.ui_HudSnapshotTest_home_rowSelected.png) | ![keyboard](app/src/test/snapshots/images/com.chorockkim.rokidyoutube.ui_HudSnapshotTest_search_keyboard.png) | ![player](app/src/test/snapshots/images/com.chorockkim.rokidyoutube.ui_HudSnapshotTest_player_controls.png) |

위 그림은 [Paparazzi](https://github.com/cashapp/paparazzi)가 실제 레이아웃 코드로 그린 480×640 화면입니다. 썸네일과 영상 화면은 그림 대신 색만 넣었습니다.

- **홈**: [검색] + 유튜브 카테고리(게임·음악·영화·팟캐스트·라이브) 탭, 그 아래 영상 목록
- **검색**: 안경용 키보드(영어·숫자). 한국어는 **음성** 키로 입력
- **플레이어**: 한 줄 버튼 [일시정지][-10초][+10초][화질][닫기] + 진행 바

## 조작

| 동작 | 목록·키보드 | 플레이어 (버튼 숨김) | 플레이어 (버튼 보임) |
|---|---|---|---|
| 앞/뒤로 쓸기 | 다음/이전 항목 | 10초 앞/뒤로 | 다음/이전 버튼 |
| 탭 | 선택 | 버튼 보이기 | 버튼 누르기 |
| 뒤로 | 이전 화면 | 플레이어 닫기 | 버튼 숨기기 |

키 매핑은 RokidPipe와 같습니다(쓸기 = DPAD 좌우·183/184, 탭 = ENTER·202, 한 번 쓸기에 두 번 들어오는 이벤트는 240ms 디바운스).

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

## 테스트

```bash
./gradlew :app:testDebugUnitTest          # 입력 매핑, 화질 선택
./gradlew :app:recordPaparazziDebug       # 화면 스냅샷 → app/src/test/snapshots/images
./gradlew :app:testDebugUnitTest -PnetworkTests=true --tests '*YouTubeNetworkTest*'  # 실제 유튜브 접속
```

## 아직 확인 안 된 것

- **안경 실기 테스트를 하지 않았습니다.** 실제 재생, 터치패드 입력, 음성 인식, 화면 가독성은 안경에서 확인해야 합니다.
- 실제 유튜브 접속 테스트(`YouTubeNetworkTest`)는 개발 환경에서 youtube.com 접속이 막혀 돌려 보지 못했습니다.
- PoToken은 안경의 WebView에서 만들어집니다. 안경 WebView가 오래되었으면 재생이 막힐 수 있습니다.

## 라이선스

GPL-3.0. NewPipeExtractor와 RokidPipe/NewPipe 코드가 GPL-3.0이기 때문입니다. `YoutubeHttpDataSource`는 ExoPlayer(Apache 2.0) 코드를 기반으로 합니다.
