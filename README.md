# S Pen Mouse

Use a Samsung S Pen as a mouse in selected Android apps through Shizuku, without root. Tested on a Galaxy S24 Ultra (SM-S928B) running Android 16.

[Download the APK and source code for v0.1.6](https://github.com/sr-tream/S-Pen-Mouse/releases/tag/v0.1.6).

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
2. Allow S Pen Mouse to display over other apps. This permission is needed for the transparent receiver that suppresses Air Actions while preserving the pen's Bluetooth connection. The optional visible pointer can be turned off for games that draw their own cursor.
3. Select the apps in which you want mouse emulation, then enable it.
4. Open the built-in test to inspect input events, mouse buttons, and dragging.
5. Stop emulation with the main switch or the Stop action in the notification.

## Samsung gestures and connection handling

During emulation, the app temporarily disables the pen-button Air Command shortcut, double-tap actions, and Samsung hover previews. It preserves the main Air Actions setting, which controls the pen's Bluetooth connection. A transparent receiver forwards a synthetic hover session to Samsung's service while selected apps receive mouse events. When emulation ends, the hover session ends and the original settings are restored.

If delivery of the synthetic hover session cannot be confirmed, mouse emulation continues and the app displays a warning that Air Actions suppression is unavailable.

A separate recovery process monitors the Shizuku service and restores settings if it exits. A journal in `/data/local/tmp` also allows settings to be restored on the next emulation startup after a reboot. Recovery supports the journal format from v0.1.1, including restoring the main Air Actions setting that version disabled.

Version 0.1.6 was tested on the connected S24 Ultra: hover, left and right clicks, and dragging arrived as `SOURCE_MOUSE` / `TOOL_TYPE_MOUSE`, with no native pen events in the test. The user confirmed that holding the right mouse button did not open the assistant or S Pen menu, and that the pen remained connected after moving it away from the screen and exiting the test. Individual games and other Samsung firmware versions still need separate testing.

## Building on Windows ARM64

You need JDK 17, Android SDK 36, and NDK r26d with a compiler that runs on Windows ARM64. The scripts default to `C:\Users\SR_team\Projects\android-ndk-r26d` for the NDK and `%LOCALAPPDATA%\Android\Sdk` for the SDK.

Run in PowerShell:

```powershell
.\build.ps1
```

To use different tool locations:

```powershell
.\build.ps1 -SpenNdkPath 'D:\android-ndk' -SpenSdkPath 'D:\Android\Sdk'
```

The script first compiles the JNI library and recovery process, then uses Gradle to build a signed debug APK at `app/build/outputs/apk/debug/app-debug.apk`. If `debug.keystore` does not exist locally, the script creates it on the first build. Signing keys are excluded from Git and published source archives. Keep your local key for future updates; an APK signed with a different key cannot be installed as an update to the release APK.

You can also open the project in Android Studio. Prebuilt ARM64 native binaries are included in `app/src/main/jniLibs`. After changing the C code, run `build-native.ps1` before building the APK in the IDE. The native binaries use 16 KiB alignment.

## Project structure

- `MainActivity`: app selection, permissions, and pointer preferences.
- `MouseService`: foreground notification, Shizuku connection, visible pointer, and the transparent hover receiver.
- `PenUserService`: foreground app detection and injection of `SOURCE_MOUSE` / `TOOL_TYPE_MOUSE` events restricted to the selected app's UID.
- `input.c`: automatic discovery of `sec_e-pen`, exclusive evdev capture, and creation of a mouse device identity through uinput.
- `guard.c`: temporary suppression of Samsung actions and restoration of the original settings.
- `TestActivity`: manual and automatic mouse input testing.

## Limitations

The transparent receiver occupies 2 × 2 pixels at the center of the screen. Mouse events in that area are shifted by 3 pixels to avoid the receiver. Synthetic hover events contain no button presses; mouse clicks are restricted to the selected app's UID. The receiver acknowledges delivery through Binder, and Samsung's hover state expires automatically if updates stop. Apps that hide third-party overlay windows may prevent Air Actions suppression.

The app does not request network access, Accessibility access, or usage statistics. Other devices require shell access to evdev/uinput and a compatible pen driver; they have not been tested.

## Dependencies

- [Shizuku API 13.1.5](https://github.com/RikkaApps/Shizuku-API) — MIT.
- [AndroidX Annotation 1.3.0](https://developer.android.com/jetpack/androidx/releases/annotation) — Apache 2.0.
