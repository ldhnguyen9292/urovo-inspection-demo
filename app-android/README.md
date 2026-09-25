# 進貨檢驗 Incoming Inspection — Urovo DT630 demo

Scan a product's Serial Number with the DT630 scanner, take evidence photos,
and save them to a folder on the PDA:

```
<chosen folder>/            e.g. Documents/Inspection
└── SN12345/
    ├── SN12345_20260925_143012.jpg
    ├── SN12345_20260925_143045.jpg
    └── info.txt            SN, first inspected, last updated, photo list
```

Design: `../docs/superpowers/specs/2026-09-25-incoming-inspection-demo-design.md`

## Build

Requirements: JDK 17 and the Android SDK (platform 35). The easiest way to get
both is Android Studio: open this `app-android/` folder and press ▶ Run.

Command line (macOS):

```bash
export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` points at `~/Library/Android/sdk`; change `sdk.dir` if your SDK lives elsewhere.

## Install on the DT630

1. On the PDA: Settings → About phone → tap **Build number** 7 times →
   Settings → System → Developer options → enable **USB debugging**.
2. Connect USB, accept the debugging prompt, then:

```bash
~/Library/Android/sdk/platform-tools/adb devices
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy `app-debug.apk` to the PDA and open it with the file manager
(allow "Install unknown apps" when asked).

## Using the app

1. First launch opens the folder picker in **Documents**. Create a folder such as
   `Inspection` and tap **Use this folder** → **Allow**. (Android does not allow
   choosing the storage root or `Download/` itself.)
2. Tap **掃描** (Scan) or press the side scan key, aim the back of the device at the SN barcode — or type the SN.
3. Tap **拍照** (Take photo) → the camera app opens → take the photo → confirm.
   The photo is saved immediately and shown as a thumbnail. Repeat as needed.
4. Tap **完成 / 下一個** (Done / Next) for the next product.

Scanning an SN that already has a folder adds photos to it. The folder can be
changed any time with **變更** (Change). To copy results to a PC, connect USB,
choose "File transfer" on the PDA, and open the folder.

## How it works

| File | Role |
|---|---|
| `Naming.kt` | SN sanitizing, photo names, `info.txt` format (unit tested) |
| `storage/InspectionStorage.kt` | Writes into the chosen folder via the Storage Access Framework |
| `scanner/ScannerController.kt` | Urovo `ScanManager` in broadcast mode; restores the previous mode on pause |
| `MainViewModel.kt` | Screen state; SN and pending photo survive process death |
| `MainScreen.kt` / `MainActivity.kt` | Compose UI, folder picker, camera |

The Urovo SDK (`app/libs/platform_sdk_v4.1.0326.jar`, from
github.com/urovosamples/SDK_ReleaseforAndroid) is `compileOnly`: the real
implementation is in the device firmware. On a non-Urovo device or emulator the
app shows "scanner unavailable" and manual entry still works.

## On-device test checklist

1. First launch → folder picker opens in Documents → create/choose `Inspection` → location shows `Documents/Inspection`.
2. Press the scan trigger on a barcode → SN appears in the field.
   Also tap the on-screen **掃描** (Scan) button → the scanner light turns on → aim at a barcode → SN appears.
3. Take 2 photos → thumbnails appear; `Inspection/<SN>/` holds 2 JPGs + `info.txt`.
4. 完成 / 下一個 → scan the same SN → existing thumbnails shown; a 3rd photo appends; "First inspected" unchanged.
5. Type an SN by hand → Done on keyboard → take photo → saved.
6. Kill and relaunch the app → folder remembered.
7. 變更 → choose another folder → new photos go there.
8. Leave the app → another app (e.g. a notes app) still receives scans as before.
9. Connect the PDA to a PC with USB (File transfer) → folder and files visible; `info.txt` opens in Notepad.
10. Developer options → "Don't keep activities" ON → take a photo → on return the SN is still there and the photo is saved. Turn the option OFF afterwards.
11. Open the camera and press back without taking a photo → nothing saved, no message.
12. Tap 拍照 so the camera app opens → switch to a file manager (recent apps) and delete the `Inspection` folder → return to the camera and take the photo → the app shows "儲存資料夾無法使用" and a 重試 button; the SN field is locked and 拍照 is disabled. Tap 變更, re-create/choose `Inspection` → 重試 saves the photo into the original SN folder.
13. Repeat step 12 but tap 完成 / 下一個 instead of 重試 → a confirmation asks before the unsaved photo is deleted.

