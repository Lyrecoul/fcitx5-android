# fcitx5-android

[Fcitx5](https://github.com/fcitx/fcitx5) input method framework and engines ported to Android.

## Download

[<img src="https://github.com/rubenpgrady/get-it-on-github/raw/refs/heads/main/get-it-on-github.png" alt="Git it on GitHub" width="207" height="80">](https://github.com/fcitx5-android/fcitx5-android/releases/latest)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" width="207" height="80">](https://f-droid.org/packages/org.fcitx.fcitx5.android)
[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="207" height="80">](https://play.google.com/store/apps/details?id=org.fcitx.fcitx5.android)

You can also download the **latest CI build** on our Jeninks server: [![build status](https://img.shields.io/jenkins/build.svg?jobUrl=https://jenkins.fcitx-im.org/job/android/job/fcitx5-android/)](https://jenkins.fcitx-im.org/job/android/job/fcitx5-android/)

> [!NOTE]
> APKs downloaded from GitHub Release/F-Droid/Jenkins have the same signature, which means they're compatible when upgrading, but Google Play's do not.
> <details>
> <summary>(click here for detailed signature info)</summary>
> <ul>
> <li>Package Name: <code>org.fcitx.fcitx5.android</code></li>
> <li>Certificate SHA-256 fingerprint:</li>
> <ul>
> <li>GitHub Release/Jenkins/F-Droid</li>
> <code>E4:DB:1E:9E:DF:F1:36:29:D0:7D:E4:BB:F8:16:5F:E9:BD:85:57:AB:55:09:26:72:DA:8E:40:DB:E4:84:EC:D7</code>
> <li>Google Play</li>
> <code>06:53:6F:F6:E8:76:C0:14:E1:4B:44:6F:61:FA:2B:80:9E:06:67:39:A1:D1:17:0D:0A:7A:89:88:4C:48:00:33</code>
> </ul>
> </ul>
> </details>

In case you want Fcitx5 on other platforms: [macOS](https://github.com/fcitx-contrib/fcitx5-macos), [iOS](https://github.com/fcitx-contrib/fcitx5-ios), [HarmonyOS](https://github.com/fcitx-contrib/fcitx5-harmony), [ChromeOS](https://github.com/fcitx-contrib/fcitx5-chrome), [Windows](https://github.com/fcitx-contrib/fcitx5-windows); or [try Fcitx5 in the browser](https://fcitx-contrib.github.io/online/index.html)

## Project status

### Supported Languages

- English (with spell check)
- Chinese
  - Pinyin, Shuangpin, Wubi, Cangjie and custom tables (built-in, powered by [fcitx5-chinese-addons](https://github.com/fcitx/fcitx5-chinese-addons))
  - Zhuyin/Bopomofo (via [Chewing Plugin](./plugin/chewing))
  - Jyutping (via [Jyutping Plugin](./plugin/jyutping/), powered by [libime-jyutping](https://github.com/fcitx/libime-jyutping))
- Vietnamese (via [UniKey Plugin](./plugin/unikey), supports Telex, VNI and VIQR)
- Japanese (via [Anthy Plugin](./plugin/anthy))
- Korean (via [Hangul Plugin](./plugin/hangul))
- Sinhala (via [Sayura Plugin](./plugin/sayura))
- Thai (via [Thai Plugin](./plugin/thai))
- Generic (via [RIME Plugin](./plugin/rime), supports importing custom schemas)

### Implemented Features

- Virtual Keyboard (layout not customizable yet)
- Expandable candidate view
- Clipboard management (plain text only)
- Theming (custom color scheme, background image and dynamic color aka monet color after Android 12)
- Popup preview on key press
- Long press popup keyboard for convenient symbol input
- Symbol and Emoji picker
- Plugin System for loading addons from other installed apk
- Floating candidates panel when using physical keyboard
- Candidate and non-rounded preedit text use short opacity-only transitions (120 ms in, 80 ms out); ongoing typing updates immediately without sliding or scaling.
- Compact floating keyboard in landscape, with configurable size and left/right placement.

### Planned Features

- Customizable keyboard layout
- More input methods (via plugin)

## Screenshots

|拼音, Material Light theme, key border enabled|自然码双拼, Pixel Dark theme, key border disabled|
|:-:|:-:|
|<img src="https://github.com/fcitx5-android/fcitx5-android/assets/13914967/bd429247-62d9-4c78-bab8-70ef3ce47588" width="360px">|<img src="https://github.com/fcitx5-android/fcitx5-android/assets/13914967/3ae969c1-7ed0-4f92-a5df-19dc8c90a8c3" width="360px">|

|Emoji picker, Pixel Light theme, key border enabled|Symbol picker, Material Dark theme, key border disabled|
|:-:|:-:|
|<img src="https://user-images.githubusercontent.com/13914967/202181845-6a5f6bb2-a877-468c-851a-fd7e66e64ed4.png" width="360px">|<img src="https://user-images.githubusercontent.com/13914967/202181861-dd253439-1d5e-4f5f-9535-934f28796a6b.png" width="360px">|

## Built-in Fcitx5 Enhanced

Open **Settings → Theme → Configuration → Fcitx5 Enhanced (built in)**
(Chinese: **设置 → 主题 → 配置 → Fcitx5 增强（内置）**). The switch is enabled
by default and applies to the selected theme, including custom image themes.
The adjacent controls adjust blur radius, background opacity, key opacity,
keyboard corner radius and glass key reflections. Defaults match the original
module: 100px blur, background alpha 60/255, key alpha 140/255 and 20dp corners.

This ports the skin effects from [rebron1900/fcitx5-enhanced](https://github.com/rebron1900/fcitx5-enhanced)
at commit `acce4a8ab48cf231afe5c2df85a7ea3625424bf2`, including its original
`GlassBorderDrawable` renderer and theme-derived background tint. It needs no
Root or LSPosed. The ported code is licensed under GPL-3.0; see
[LICENSE.GPL-3.0](LICENSE.GPL-3.0) and the source file notices.

Key radius, margins, key backgrounds and press feedback retain the keyboard's
existing settings, as in the module. Enable **Key border** for filled keys;
leave it disabled for glass outlines without key backgrounds. The earlier
`EnhancedGlassLight` / `EnhancedGlassDark` selections remain available as
Pixel-based palettes, but selecting them is not required to enable the effects.

Toolbar panels (clipboard, input-method actions and expanded candidates) share
the keyboard's translucent background in enhanced mode, including with **Key
border** disabled. Switching to the English keyboard preserves key opacity
and glass reflections.

Toolbar button icons retain the theme color when it provides at least 4.5:1
contrast against the toolbar surface; otherwise they use a readable theme text
color or black/white. On image or translucent glass backgrounds, icons use a
thin opposite-color outline instead of a filled backing. The toolbar and button
padding stay transparent, with the original icon size and touch targets.

The floating preedit uses one theme-colored rounded background, without
rectangular fills behind individual text lines. It follows content width,
aligns 12dp inside the keyboard's side padding and sits 3dp above the keyboard.
Its existing 0.8 opacity is retained. The capsule background and outline resize
continuously over 160 ms, including appearance and dismissal, without scaling
the glyphs. Text keeps its unchanged prefix and updates the changed suffix in
40 ms batches of at least two letters (or the remaining letters); fast typing
retargets the pending batch instead of queuing old preedit. Setting the enhanced
corner radius to zero, or disabling the enhancement, restores the original
preedit styling.

The preedit is a noninteractive overlay: its appearance, wrapping and resize
animation do not change the application's resize/pan insets or consume touches
above the keyboard. Both application insets follow the keyboard itself.

Android 12 and later use system background blur confined to the keyboard.
The background tint remains translucent when blur is unavailable, matching
the module. The settings preview shows tint, reflections and corners; actual
cross-window blur is visible when the input method is open. Voice input and
the source module's extra shortcut toolbar are not part of this skin port.

### Landscape compact keyboard

**Settings → Virtual Keyboard → Compact keyboard in landscape** (Chinese:
**设置 → 虚拟键盘 → 横屏小键盘**) is enabled by default. In landscape the
keyboard floats at the bottom right, initially 460dp wide and 40% of the
available height (at least 200dp including the toolbar and drag handle). Width
is clamped to the window; system bars and display cutouts are avoided. Size
and left-side placement are configurable in the same settings section.

Expand the keyboard toolbar and tap the keyboard icon to switch between
compact and full-width layouts. The choice is remembered. Portrait keeps its
normal keyboard size and padding. Compact mode does not reserve space or pan
the app, and touches outside the keyboard reach the app, including beside the
preedit. Candidates, symbols, emoji and clipboard panels use the compact width.

Hold anywhere in the full-width, 24dp-high strip at the top of the compact
keyboard to move it. Its centered rounded handle stays 56×4dp. The keyboard, preedit and touchable area move
together, staying within the usable window
with room above for preedit. The relative position is saved across keyboard
reopening and rotation. Changing left-side placement resets the position to
the selected bottom corner. Full-width and portrait layouts hide the handle.

Key previews use current window coordinates on every press, including after
dragging, and place their labels immediately above the pressed key using its
actual row height rather than a fixed-height offset.

### Physical keyboards

With **Candidates window → Show candidates window → Follow input device**
(the default), an available physical keyboard hides the software keyboard panel
as soon as an editor opens. Clicking the editor or using a finger/stylus keeps
the large panel hidden; floating candidates remain available without reserving
keyboard space in the app. Disconnecting or folding away the physical keyboard
restores the software panel. **System default** follows Android's keyboard
visibility setting; **Disabled** retains the software panel.

The device regression requires an available QWERTY hardware keyboard (the local
emulator provides one):

```sh
adb shell am instrument --no-hidden-api-checks -w \
  -e class org.fcitx.fcitx5.android.HardwareKeyboardPanelTest \
  org.fcitx.fcitx5.android.debug.test/androidx.test.runner.AndroidJUnitRunner
```

### Automatic Chinese/Latin spacing

**Settings → Advanced → Automatic Chinese/Latin spacing** applies to candidate
commits and English direct typing. Spaces are added within newly committed
text, between uninterrupted commits, and when appending at the document end
after moving the cursor or editing earlier text. In the middle of a document,
the first commit after moving the cursor or replacing a selection leaves old
boundaries untouched. Deleting, moving the cursor or pressing Enter ends the
previous typing run. Existing text to the right is left untouched; password,
URL and email fields are excluded.

Backspace also removes a space inserted by automatic spacing when deleting
the last character on its right would leave that space unnecessary. For example,
`中文 ab` becomes `中文 a`, then `中文`. Manually entered spaces are retained.
Cleanup is limited to spaces owned by the current typing/deletion run; cursor
movement, selection, editor changes and disabling the feature discard ownership.
Spaces still needed by existing text to the right are preserved.

The Android regressions exercise panel rendering and real service commits with
an Android input connection. Run them on a test device/emulator with
`adb shell am instrument --no-hidden-api-checks -w -e class org.fcitx.fcitx5.android.GlassPanelTest,org.fcitx.fcitx5.android.PanguInputConnectionTest org.fcitx.fcitx5.android.debug.test/androidx.test.runner.AndroidJUnitRunner`.
The flag is needed only for the test harness to inject the editor connection;
the keyboard itself does not require hidden-API access.

## Get involved

Trello kanban: https://trello.com/b/gftk6ZdV/kanban

Matrix Room: https://matrix.to/#/#fcitx5-android:mozilla.org

Discuss on Telegram: [@fcitx5_android_group](https://t.me/fcitx5_android_group) ([@fcitx5_android](https://t.me/fcitx5_android) originally)

## Build

### Workstation development profile

This checkout's `gradle.properties` is configured for local Linux development:

| Setting | Value |
| --- | --- |
| Gradle daemon JDK | Android Studio JBR 25 at `/opt/android-studio/jbr` |
| Android SDK | `/home/lyrecoul/Android/Sdk` in `local.properties` |
| Compile / target SDK | 36 |
| NDK | 29.0.14206865 |
| CMake / Build Tools | 3.31.6 / 36.1.0 |
| Default ABI | x86_64 (local emulator) |
| Gradle worker limit | 8 |
| Host ECM | `/home/lyrecoul/.local/share/ECM/cmake` |

Build without exporting toolchain variables on every invocation:

```sh
./gradlew :app:assembleDebug
./gradlew :app:installDebug
# Physical ARM64 devices:
./gradlew :app:assembleDebug -PbuildABI=arm64-v8a
```

Debug APKs are written under `app/build/outputs/apk/debug/`. Installation needs
an authorized device/emulator matching the selected ABI. Java/Kotlin Android
bytecode still targets Java 11; the Gradle daemon runtime is separate. Set
Android Studio's Gradle JDK to the same JBR. The shell launcher can use the
system Java; `org.gradle.java.home` pins the daemon used for compilation.

ECM 6.31.0 was extracted from the signed Arch `extra-cmake-modules` package into
`~/.local/share/ECM`, without root or system package changes. `ecmDir` forwards
its location to CMake; `ECM_DIR` in the environment takes precedence. If ECM is
installed system-wide instead, remove `ecmDir` to use `/usr/share/ECM/cmake`.
GNU gettext (`msgfmt`) must also be on `PATH`. An incomplete NDK 28 directory
is not usable; this profile explicitly selects the installed NDK 29 instead.

These are workstation-specific defaults, not CI requirements. Change the paths
when moving this checkout. Existing `BUILD_ABI`, `NDK_VERSION`, `CMAKE_VERSION`
and `BUILD_TOOLS_VERSION` environment overrides still take precedence over
project properties. No signing identity or release configuration is changed.


### Dependencies

- Android SDK Platform 36 and Build Tools 36.1.0.
- Android NDK 29.0.14206865 and CMake 3.31.6 for the local profile above; shared fallback versions are in `build-logic/convention/src/main/kotlin/Versions.kt`.
- [KDE/extra-cmake-modules](https://github.com/KDE/extra-cmake-modules)
- GNU Gettext >= 0.20 (for `msgfmt` binary; or install `appstream` if you really have to use gettext <= 0.19.)

### How to set up development environment

<details>
<summary>Prerequisites for Windows</summary>

- Enable [Developer Mode](https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) so that symlinks can be created without administrator privilege.

- Enable symlink support for `git`:

    ```shell
    git config --global core.symlinks true
    ```

</details>

First, clone this repository and fetch all submodules:

```shell
git clone git@github.com:fcitx5-android/fcitx5-android.git
git submodule update --init --recursive
```

Install `extra-cmake-modules` and `gettext` with your system package manager:

```shell
# For Arch Linux (Arch has gettext in it's base meta package)
sudo pacman -S extra-cmake-modules

# For Debian/Ubuntu
sudo apt install extra-cmake-modules gettext

# For macOS
brew install extra-cmake-modules gettext

# For Windows, install MSYS2 and execute in its shell (UCRT64)
pacman -S mingw-w64-ucrt-x86_64-extra-cmake-modules mingw-w64-ucrt-x86_64-gettext
# then add C:\msys64\ucrt64\bin to PATH
```

Install Android SDK Platform, Android SDK Build-Tools, Android NDK and cmake via SDK Manager in Android Studio:

<details>
<summary>Detailed steps (screenshots)</summary>

**Note:** These screenshots are for references and the versions in them may be out of date.
The current recommended versions are recorded in [Versions.kt](build-logic/convention/src/main/kotlin/Versions.kt) file.

![Open SDK Manager](https://user-images.githubusercontent.com/13914967/202184493-3ee1546b-0a83-4cc9-9e41-d20b0904a0cf.png)

![Install SDK Platform](https://user-images.githubusercontent.com/13914967/202184534-340a9e7c-7c42-49bd-9cf5-1ec9dcafcf32.png)

![Install SDK Build-Tools](https://user-images.githubusercontent.com/13914967/202185945-0c7a9f39-1fcc-4018-9c81-b3d2bf1c2d3f.png)

![Install NDK](https://user-images.githubusercontent.com/13914967/202185601-0cf877ea-e148-4b88-bd2f-70533189b3d4.png)

![Install CMake](https://user-images.githubusercontent.com/13914967/202184655-3c1ab47c-432f-4bd7-a508-92096482de50.png)

</details>

### Trouble-shooting

- Android Studio indexing takes forever to complete and cosumes a lot of memory.

    Switch to "Project" view in the "Project" tool window (namely the file tree side bar), right click `lib/fcitx5/src/main/cpp/prebuilt` directory, then select "Mark Directory as > Excluded". You may also need to restart the IDE to interrupt ongoing indexing process.

- Gradle error: "No variants found for ':app'. Check build files to ensure at least one variant exists." or "[CXX1210] <whatever>/CMakeLists.txt debug|arm64-v8a : No compatible library found"

    Examine if there are environment variables set such as `_JAVA_OPTIONS` or `JAVA_TOOL_OPTIONS`. You might want to clear them (maybe in the startup script `studio.sh` of Android Studio), as some gradle plugin treats anything in stderr as errors and aborts.

## Nix

Appropriate Android SDK with NDK is available in the development shell.  The `gradlew` should work out-of-the-box, so you can install the app to your phone with `./gradlew installDebug` after applying the patch mentioned above. For development, you may want to install the unstable version of Android Studio, and point the project SDK path to `$ANDROID_SDK_ROOT` defined in the shell. Notice that Android Studio may generate wrong `local.properties` which sets the SDK location to `~/Android/SDK` (installed by SDK Manager). In such case, you need specify `sdk.dir` as the project SDK in that file manually, in case Android Studio sticks to the wrong global SDK.
