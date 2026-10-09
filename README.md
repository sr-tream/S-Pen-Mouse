# S Pen Mouse

Use a Samsung S Pen as a mouse in selected Android apps through Shizuku, without root. Tested on a Galaxy S24 Ultra (SM-S928B) running Android 16.

[Download APKs and source code](https://github.com/sr-tream/S-Pen-Mouse/releases).

## Controls

| S Pen action | Mouse input |
| --- | --- |
| Hover above the screen | Move the pointer beneath the pen tip |
| Touch and hold the screen | Press and hold the left mouse button; lift to release |
| Press and hold the pen button | Press and hold the right mouse button; release the pen button to release |
| Touch the screen while holding the pen button | Hold both mouse buttons |

The pen button works as a mouse button within the screen digitizer's detection range. Bluetooth Air Actions outside that range do not provide mouse input.

## Getting started

Requirements: Android 13 or later, ARM64, a running Shizuku service, and permission to display over other apps. Compatibility has been verified only on the device and firmware listed above.

1. Install the APK, start Shizuku, and grant S Pen Mouse access to Shizuku.
2. Allow S Pen Mouse to display over other apps. This permission is needed for the transparent receiver that suppresses Air Actions while preserving the pen's Bluetooth connection. Use the Settings button beside each app to choose whether to draw an extra cursor: turn it off for games with their own cursor and on for apps such as browsers. Existing cursor preferences are preserved for selected apps when upgrading.
3. Select the apps in which you want mouse emulation, then enable it.
4. Open the built-in test to inspect input events, mouse buttons, and dragging.
5. Stop emulation with the main switch or the Stop action in the notification.

## Camera pad and finger input

Version 0.2.0 adds an optional eight-direction pad for games that support keyboard arrow keys. Open **Camera pad and finger touch settings** to enable it and adjust its opacity, horizontal position, vertical position, and size. These settings apply to the selected foreground apps; the pad and touch blocking are off by default.

Touch a direction to hold an arrow key. Diagonals hold two keys, and the center is a dead zone that releases them. Lift your finger to stop. The pad works independently of S Pen hover, so you can move the camera with a finger while using the pen as a mouse. At 0% opacity, the pad is invisible but its touch area remains active.

Enable **Block other finger touches in the game** to consume finger input outside the pad as well. This option also works with the pad disabled. Mouse events from the S Pen continue to reach the game.

Touches starting within 32 dp of the left/right edges, 40 dp of the top, or 48 dp of the bottom are forwarded for the entire gesture. These reserved strips also allow game touches through. Touches on system popups, including One Hand Operation+ controls, also pass through. Controls suspend when the notification shade or another window takes focus, with an ongoing forwarded gesture allowed to finish before capture is released. Arrow keys are released when the finger lifts, settings change, the app loses focus, or emulation stops.

On the tested S24 Ultra, bottom Home/Recents gestures work after the S Pen leaves hover range. They remain blocked while the pen is hovering. Move the pen away from the screen before swiping up to minimize the app or open Recents.

The pad's drawing window does not intercept input. The Shizuku engine captures the physical `sec_touchscreen` device, consumes pad touches, and forwards allowed touches through a virtual touchscreen. The S Pen uses its separate input device. This avoids a full-screen touchable overlay that would also intercept injected mouse events. Physical touchscreen capture is released on process exit; no touchscreen settings are changed. Android's software-injected touch events do not pass through this physical-device filter.

## Samsung gestures and connection handling

During emulation, the app temporarily disables the pen-button Air Command shortcut, double-tap actions, and Samsung hover previews. It preserves the main Air Actions setting, which controls the pen's Bluetooth connection. A transparent receiver forwards a synthetic hover session to Samsung's service while selected apps receive mouse events. When emulation ends, the hover session ends and the original settings are restored.

If delivery of the synthetic hover session cannot be confirmed, mouse emulation continues and the app displays a warning that Air Actions suppression is unavailable.

A separate recovery process monitors the Shizuku service and restores settings if it exits. A journal in `/data/local/tmp` also allows settings to be restored on the next emulation startup after a reboot. Recovery supports the journal format from v0.1.1, including restoring the main Air Actions setting that version disabled.

Version 0.1.6 was tested on the connected S24 Ultra: hover, left and right clicks, and dragging arrived as `SOURCE_MOUSE` / `TOOL_TYPE_MOUSE`, with no native pen events in the test. The user confirmed that holding the right mouse button did not open the assistant or S Pen menu, and that the pen remained connected after moving it away from the screen and exiting the test.

For version 0.2.0, the user confirmed camera movement and blocking of other finger taps in Company of Heroes on the same phone. Follow-up testing confirmed system popup taps work, Back works, and Home/Recents work after the pen leaves hover range; Home/Recents remain blocked during hover. The user also confirmed both built-in diagnostics passed and a five-second pen-button hold opened neither Samsung's menu nor the assistant. The Android/native builds, APK signature, eight-direction geometry, dead zone, and reserved-edge checks passed. Android recognized the relay as an orientation-aware touchscreen and the camera device as a keyboard. Other games and Samsung firmware versions still need separate testing.

## Building on Windows ARM64

You need JDK 17, Android SDK 36, and NDK r26d with a compiler that runs on Windows ARM64. Both build scripts read the NDK directory from the `ANDROID_NDK` environment variable. The SDK defaults to `%LOCALAPPDATA%\Android\Sdk`.

Run in PowerShell:

```powershell
$env:ANDROID_NDK = 'D:\android-ndk-r26d'
.\build.ps1
```

To override `ANDROID_NDK` or use a different SDK location:

```powershell
.\build.ps1 -SpenNdkPath 'D:\android-ndk' -SpenSdkPath 'D:\Android\Sdk'
```

If neither `ANDROID_NDK` nor `-SpenNdkPath` is set, the scripts stop with an error explaining how to provide the NDK directory.

The script first compiles the JNI library and recovery process, then uses Gradle to build a signed debug APK at `app/build/outputs/apk/debug/app-debug.apk`. If `debug.keystore` does not exist locally, the script creates it on the first build. Signing keys are excluded from Git and published source archives. Keep your local key for future updates; an APK signed with a different key cannot be installed as an update to the release APK.

You can also open the project in Android Studio. Prebuilt ARM64 native binaries are included in `app/src/main/jniLibs`. After changing the C code, run `build-native.ps1` before building the APK in the IDE. The native binaries use 16 KiB alignment.

## Project structure

- `MainActivity`: app selection, permissions, and pointer preferences.
- `MouseService`: foreground notification, Shizuku connection, visible pointer, and the transparent hover receiver.
- `CameraSettingsActivity` / `AppSettingsActivity`: camera controls and per-app cursor preferences.
- `PenUserService`: foreground app detection and injection of `SOURCE_MOUSE` / `TOOL_TYPE_MOUSE` events restricted to the selected app's UID.
- `CameraInput` / `CameraConfig`: finger routing, eight-direction geometry, keyboard holds and repeats, and system gesture handoff.
- `TouchWindows`: read-only window geometry used to pass finger touches to system popups.
- `input.c`: automatic discovery of `sec_e-pen`, exclusive evdev capture, and creation of a mouse device identity through uinput.
- `controls.c`: physical touchscreen capture, multitouch forwarding through uinput, and a keyboard device identity for camera arrow keys.
- `guard.c`: temporary suppression of Samsung actions and restoration of the original settings.
- `TestActivity`: manual and automatic mouse input testing.

## Limitations

The transparent receiver occupies 2 × 2 pixels at the center of the screen. Mouse events in that area are shifted by 3 pixels to avoid the receiver. Synthetic hover events contain no button presses; mouse clicks are restricted to the selected app's UID. The receiver acknowledges delivery through Binder, and Samsung's hover state expires automatically if updates stop. Apps that hide third-party overlay windows may prevent Air Actions suppression.

The app does not request network access, Accessibility access, or usage statistics. Other devices require shell access to evdev/uinput and a compatible pen driver; they have not been tested.

## Dependencies

- [Shizuku API 13.1.5](https://github.com/RikkaApps/Shizuku-API) — MIT.
- [AndroidX Annotation 1.3.0](https://developer.android.com/jetpack/androidx/releases/annotation) — Apache 2.0.
