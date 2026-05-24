# Identify Everything - Android Client

Android mobile application for identifying items using scannable QR codes.

## Features (MVP)

- Scanning QR codes with CameraX and ZXing
- Local SQLite database for offline-first data storage
- item version history tracking
- Background sync when network is available
- GPS location tagging for items

## Project Structure

```
android/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/identify/Everything/
│       │   ├── MainActivity.kt           # Main activity
│       │   ├── MainActivityViewModel.kt   # ViewModel for state management
│       │   ├── data/
│       │   │   ├── AppDatabase.kt        # SQLite database singleton
│       │   │   ├── entities/
│       │   │   │   ├── Item.kt
│       │   │   │   └── ItemVersion.kt
│       │   │   └── ItemRepository.kt     # Data layer
│       │   └── util/
│       │       ├── GuidGenerator.kt      # Base26 encoding
│       │       └── SyncApiClient.kt       # HTTP client for server sync
│       └── res/                          # Android resources
├── build.gradle                          # Android SDK configuration
├── proguard-rules.pro                     # ProGuard rules
└── README.md                             // This file
```

## Quick Start

### Prerequisites

- Android Studio Hedgehog (2023.1.1+) or Ocean
- JDK 11 or higher
- Android SDK: API 26+

### Building

```bash
cd android

# Debug build
./gradlew assembleDebug

# Test locally without emulator
./gradlew test

# Test on emulator
./gradlew connectedAndroidTest
```

### Running

1. Open `MainActivity` in Android Studio
2. Connect an Android device or start an emulator
3. Press "Run" (green triangle button)

### Development

**View logs**:
```bash
# On connected device
adb logs

# Filter by app tag
adb logs | grep "IdentifyEverything"
```

**Install APK directly**:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing

### Unit Tests (no emulator needed)
```bash
./gradlew test
```

### Instrumented Tests (real device/emulator)
```bash
./gradlew connectedAndroidTest
```

### Manual Testing Checklist

- [ ] Scan QR code successfully
- [ ] Manually enter QR data
- [ ] Add new item
- [ ] Edit existing item
- [ ] View item details with version history
- [ ] Background sync (toggle WiFi, wait, verify sync)
- [ ] Offline editing (turn off WiFi, add item, verify saved locally)
- [ ] GPS location preservation on edit
- [ ] Conflict resolution (edit on both devices, verify timestamp logic)

## Architecture

### Offline-First Design

1. **Local Storage**: All data stored in local SQLite database
2. **Sync Queue**: Pending changes tracked separately
3. **Conflict Resolution**: Newer timestamps win by default
4. **Background Sync**: Periodic upload when network available

### Data Flow

```
User Action (View/Edit/Create)
    ↓
ItemRepository.saveItem()
    ↓
Local SQLite writ.e
    ↓
SyncQueue.addItem() (if pending)
    ↓
Background Worker
    ↓
SyncApiClient.upload() (when online)
    ↓
Central Server
```

### Error Handling

- Network failures: Queue changes, retry with exponential backoff
- Sync conflicts: Merge using timestamps, notify user
- Data corruption: Version integrity validation, auto-create missing parent versions

## Dependencies

- **ZXing**: QR code scanning library
- **Play Services Location**: GPS location
- **Lifecycle Components**: MVVM architecture support
- **Kotlin Coroutines**: Async/await patterns

## Future Enhancements

- iOS version
- Per-item access control
- Sync preferences (background frequency)
- Data export
- Attachment compression
- Peer-to-peer sync between devices

## Contributing

See [../ARCHITECTURE.md](../docs/ARCHITECTURE.md#android-client) for implementation details.

## License

Proprietary - All rights reserved
