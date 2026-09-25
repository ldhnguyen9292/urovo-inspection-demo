# Incoming Inspection Demo App (Urovo DT630) — Design

Date: 2026-09-25
Status: Draft for review

## 1. Goal

Customer requirement (original):

> PDA有掃描頭也有拍照功能。要開發一個APP，用在進貨檢驗，可以用掃描頭掃產品上面的Serial Number 條碼，也可以照相把進貨檢驗的產品做一個拍照存證；然後這一個Serial Number與照片要儲存到指定的資料夾路徑。請針對以上的需求，開發一個小程式DEMO。

A demo Android app for the Urovo DT630 used during incoming goods inspection:

1. Scan a product's Serial Number (SN) barcode with the built-in scanner.
2. Take one or more evidence photos of that product.
3. Save the SN and photos into a user-specified folder on the PDA.

**Success criteria:** on a real DT630, an operator can scan an SN, take photos,
and then find a folder named after the SN containing the photos and an
`info.txt`, inside the folder they chose — browsable on a PC over USB.

## 2. Scope

**In scope**
- Barcode scanning via the Urovo scanner (hardware trigger), with manual SN entry fallback.
- Photo capture via the device's built-in camera app.
- User-selectable destination folder on the PDA, remembered across launches.
- One subfolder per SN containing photos and `info.txt`.
- UI strings in Traditional Chinese (zh-TW) and English, chosen by device language.

**Out of scope (possible later additions)**
- Pass/Fail result, notes, inspector/supplier/PO fields.
- Backend, database server, web dashboard, network share (SMB) saving.
- Master CSV log.
- In-app camera preview (CameraX), image resizing/compression.
- Login / multi-user.

## 3. Platform & Tooling

| Item | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, single Activity |
| Target device OS | Android 15 (API 35) — confirmed for the customer's DT630 |
| minSdk / targetSdk / compileSdk | 26 (Android 8.0) / 35 / 35 |
| Build | Android Studio (current stable), Gradle wrapper, AGP 8.x |
| Urovo SDK | `platform_sdk_v4.1.0326.jar` from `github.com/urovosamples/SDK_ReleaseforAndroid`, added as `compileOnly` (real implementation is in the device firmware) |
| Package name | `com.nxsys.inspectiondemo` (changeable) |

**Developer machine prerequisite:** install Android Studio (bundles JDK, Android
SDK and `adb`). The Mac currently has only Java 8 and no Android SDK.

## 4. User Flow

```
Launch
  │
  ├─ No folder chosen yet? → system folder picker → remember folder
  │
  ▼
Main screen
  ┌───────────────────────────────────────┐
  │ 儲存位置: Documents/Inspection  [變更]  │
  │                                       │
  │ Serial Number: [ SN12345          ]   │ ← filled by scan, or typed
  │                                       │
  │ [ 📷 拍照 ]   (enabled when SN present) │
  │                                       │
  │ 已拍照片 (2):  [thumb] [thumb]           │ ← photos for current SN
  │                                       │
  │ [ 完成 / 下一個 ]                        │ ← clears SN for next product
  └───────────────────────────────────────┘
```

- Pressing the scan trigger fills the SN field (replacing any current SN — i.e. starts a new product).
- 拍照 opens the system camera; on confirm, the photo is saved immediately into the SN folder and its thumbnail appears.
- 完成 / 下一個 clears the SN and thumbnails. Files are already saved; this is only a UI reset.
- If the scanned SN's folder already exists, new photos are added to it and `info.txt` is updated (no duplicate folder). Existing photos of that SN are shown as thumbnails.

## 5. Output Format

Chosen folder (example `Documents/Inspection`):

```
Inspection/
├── SN12345/
│   ├── SN12345_20260925_143012.jpg
│   ├── SN12345_20260925_143045.jpg
│   └── info.txt
└── SN67890/
    ├── SN67890_20260925_150101.jpg
    └── info.txt
```

- **Photo name:** `<SN>_<yyyyMMdd>_<HHmmss>.jpg`. If a name already exists (two photos in the same second), append `_2`, `_3`, …
- **Photos are saved at original camera resolution** (evidence; no recompression).
- **`info.txt`** (UTF-8), rewritten after each photo:

```
Serial Number: SN12345
First inspected: 2026-09-25 14:30:12
Last updated: 2026-09-25 14:30:45
Photo count: 2
Photos:
SN12345_20260925_143012.jpg
SN12345_20260925_143045.jpg
```

"First inspected" is preserved from the existing `info.txt` when present; the photo list is built from the `.jpg` files actually in the folder.

- **SN sanitizing for folder/file names:** trim whitespace; replace any of `/ \ : * ? " < > |` and control characters with `_`. The raw scanned value is written in `info.txt`. An SN that is empty after trimming is rejected.

## 6. Components

Each unit has one job and can be understood without reading the others.

### 6.1 `ScannerController`
Wraps Urovo `android.device.ScanManager`.
- `start(onScan: (String) -> Unit)` — called in `onResume`:
  1. `openScanner()` if not already powered on.
  2. Remember current output mode (`getOutputMode()`), then `switchOutputMode(0)` (intent/broadcast mode).
  3. Read the broadcast action and data tag from the device (`getParameterString` with `PropertyID.WEDGE_INTENT_ACTION_NAME` / `WEDGE_INTENT_DATA_STRING_TAG`), falling back to `ScanManager.ACTION_DECODE` / `BARCODE_STRING_TAG`.
  4. Register a receiver; each broadcast delivers the barcode string to `onScan` on the main thread. Because the broadcast comes from the system scanner service (another process), the receiver must be registered with `RECEIVER_EXPORTED` (required flag on Android 14+; use `ContextCompat.registerReceiver`).
- `stop()` — called in `onPause`: unregister receiver, `stopDecode()`, restore the remembered output mode.
- If `ScanManager` is not present (emulator / non-Urovo phone → `NoClassDefFoundError` or exception), `start` reports "scanner unavailable"; the app continues with manual entry only and shows a small notice.

### 6.2 `InspectionStorage`
All file I/O against the chosen folder, using the Storage Access Framework (`DocumentFile` on a persisted tree URI).
- `hasValidFolder(): Boolean` — persisted URI exists and permission is still held.
- `setFolder(treeUri)` — take persistable read/write permission, save URI in SharedPreferences.
- `folderDisplayName(): String`
- `savePhoto(rawSn, sourceFile): SavedPhoto` — get-or-create SN subfolder, pick a unique name, copy bytes, rewrite `info.txt`.
- `listPhotos(rawSn): List<Uri>` — existing photos for thumbnails.

### 6.3 `Naming` (pure Kotlin, no Android dependencies)
- `sanitizeSn(raw): String?`
- `photoFileName(sn, timestamp, existingNames): String`
- `buildInfoTxt(rawSn, firstInspected, lastUpdated, photoNames): String`
- `parseFirstInspected(infoTxt): String?`

### 6.4 `MainActivity` + `MainScreen` (Compose) + `MainViewModel`
- Android 15 enforces edge-to-edge for targetSdk 35: the Compose screen uses `Scaffold` / window insets so content is not hidden under the status or navigation bar.
- The app does **not** declare the `CAMERA` permission (the system camera app takes the photo), so no runtime camera permission prompt is needed.
- ViewModel holds UI state: folder name, current SN, thumbnails, scanner availability, error message.
- Activity wires lifecycle (`ScannerController.start/stop`), the folder picker (`ActivityResultContracts.OpenDocumentTree`), and the camera (`ActivityResultContracts.TakePicture` into a temp file in `cacheDir` exposed via `FileProvider`).
- After the camera returns success: `InspectionStorage.savePhoto(...)`, then delete the temp file.

## 7. Error Handling

| Situation | Behavior |
|---|---|
| No folder chosen / permission revoked / folder deleted | Show "請選擇儲存資料夾" prompt and open the picker; 拍照 disabled until resolved. |
| Scanner unavailable | Notice "掃描器無法使用，請手動輸入"; manual entry works. |
| Empty/invalid SN | 拍照 stays disabled. |
| Camera cancelled | Nothing saved; temp file deleted. |
| Write fails (storage full, I/O error) | Error message shown; temp photo kept so the user can retry with 重試. |
| Scan arrives while camera is open | Ignored (receiver is unregistered while paused). |

## 8. Testing

- **JVM unit tests** for `Naming`: sanitizing, unique file names, `info.txt` build/parse round-trip.
- **Manual test checklist on the DT630:**
  1. First launch → folder picker → choose/create `Documents/Inspection`.
  2. Press trigger on a barcode → SN appears.
  3. Take 2 photos → thumbnails appear; files and `info.txt` exist in `Inspection/<SN>/`.
  4. 完成 → scan the same SN again → existing thumbnails shown; a 3rd photo appends; "First inspected" unchanged.
  5. Type an SN manually → take photo → saved.
  6. Kill and relaunch app → folder remembered.
  7. Change folder in settings → new photos go to the new folder.
  8. Leave app → other apps still receive scanner input in their previous mode.
  9. Connect PDA to a PC via USB → folder and files visible.

## 9. Risks

- **DT630 not listed in the Urovo sample README.** `ScanManager` is Urovo's common API across its Android PDAs, so it is expected to work; verified in step 2 of the checklist. Fallback: manual entry / keyboard-wedge.
- **Urovo SDK jar is from 2020 (v4.1.0326) while the device runs Android 15.** The jar is only used to compile; calls go to the `ScanManager` in the device firmware, which is current. Only long-standing methods are used (`openScanner`, `getOutputMode`, `switchOutputMode`, `getParameterString`, `stopDecode`). If Urovo supplies a newer jar for the DT630, it drops in unchanged.
- **Some Urovo units restrict app installs or developer options** via enterprise settings; may need the admin password from the supplier.
- **SAF folder picker** on Android 11+ blocks choosing the root of storage or `Download/` itself; the user should pick or create a subfolder (e.g. `Documents/Inspection`). The first-run prompt will say so.
