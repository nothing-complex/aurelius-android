# Stage 5 QA Verification Task

## Task from Studio Director (2026-05-03)

**APK Path:** `C:\Users\luka\Projects\Greyloop\Android Apps\Aurelius\app\build\outputs\apk\debug\app-debug.apk`

**Package:** `com.greyloop.aurelius`

**Screens to verify:**
1. HomeScreen - ChatListItem press animation, FAB border visible, header has no shadow
2. ChatScreen - MessageBubble tail (8dp triangles), AI avatar is DeepSage NOT primary color, TypingIndicator DeepSage avatar
3. SettingsScreen - LazyColumn scrolls, character counters show (e.g. "50 / 500"), HorizontalDividers between sections
4. OnboardingOverlay - dark scrim, centered 320dp card, API key field

## Steps
1. Install APK via `adb install -r` 
2. Launch app via `adb shell am start -n com.greyloop.aurelius/.MainActivity`
3. Wait 3 seconds
4. Capture screenshot of HomeScreen
5. Navigate to each screen and capture screenshots
6. Save screenshots to: `C:\Users\luka\Projects\Greyloop\Android Apps\Aurelius\qa-screenshots\`

## Output
- 4 screenshots named: `screen_01_homescreen.png`, `screen_02_chatscreen.png`, `screen_03_settingsscreen.png`, `screen_04_onboarding.png`
- Report which screens are accessible/navigable