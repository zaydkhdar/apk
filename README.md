# Android Keyboard [Unofficial] — Version 3

An offline Android Input Method Service (IME), styled after the clean, rounded iOS keyboard look.

## Version 3 adds a clipboard
- Select all / Copy / Cut / Paste keys above the letters.
- 📋 key opens clipboard history: tap an item to paste it, ★ to pin it, ✕ to delete it, Clear to remove all unpinned items.
- Keeps the last 30 copied texts (plus pinned ones), stored only on the device. Copies flagged as sensitive by the source app (Android 13+) are skipped.
- "Erase clipboard history" button in the app.
- Clipboard history only records copies made while this keyboard is the active keyboard.

## Version 2 features
- Android keyboard service that can be selected as the default input method.
- English QWERTY, French AZERTY, and Arabic character layouts. Tap the language control to cycle EN → FR → AR.
- Local word-frequency learning and up to 3 prefix suggestions.
- Conservative automatic correction: only considers a one-edit typo when a learned candidate has appeared at least 3 times; if the top candidates tie, it does not autocorrect.
- Backspace immediately after an automatic correction restores the original typed word and delimiter.
- Local typing history of up to 500 individual words, not complete messages.
- App buttons to erase typing history or learned vocabulary.
- Password fields are excluded from learning/correction.
- No internet permission; nothing is uploaded.

## Build in the cloud (GitHub Actions)
Push this project to GitHub. `.github/workflows/build.yml` builds a debug APK on every push; download it from the Actions tab → latest run → Artifacts.

## Build locally
1. Install Android Studio on a computer.
2. Extract this ZIP.
3. In Android Studio choose **Open**, then select the `AndroidKeyboardUnofficial` folder.
4. Allow Gradle sync. Use JDK 17 if requested.
5. Connect an Android phone with USB debugging enabled (or use an emulator).
6. Press **Run**.

## Enable on Android
1. Open the app.
2. Tap **Enable keyboard** and enable Android Keyboard [Unofficial].
3. Return to the app and tap **Choose keyboard**.
4. Select Android Keyboard [Unofficial].
5. Open a notes app and test.

## Known limitations
This is a learning prototype, not a production-ready Gboard replacement. The language layouts are basic character layouts, not complete localized keyboard experiences. Arabic is displayed as a simple row layout; punctuation, Arabic diacritics, RTL handling, and Arabic language-model correction need more work. The French layout is AZERTY but does not yet provide accented long-press characters. The Shift key is currently visual only. The local history stores individual words only, not sentence context.

## Privacy
A keyboard can process sensitive text. Android shows a system warning for third-party keyboards. This project has no network permission and keeps its word counts/history in app-private local storage. Do not use it for passwords or highly sensitive text. Clear history and learned words from the app at any time.
