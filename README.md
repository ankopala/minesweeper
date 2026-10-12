# minesweeper

A simple minesweeper for Android. Built with Kotlin and Jetpack Compose — no ads, no tracking, just the classic game.

[中文](README.zh.md)

## Features

- **Four difficulty levels**
  | Level | Grid | Mines |
  |-------|------|-------|
  | Standard | dynamic | 15% of cells |
  | Easy | 9 × 9 | 10 |
  | Medium | 16 × 16 | 40 |
  | Expert | 16 × 30 | 99 |
- **Adaptive board** — the Standard level stretches to fill the screen, while the three fixed levels scale to fit.
- **First-click safe** — your first tap is never a mine, and it always opens a clear area.
- **Flood fill** — tapping an empty cell reveals the whole connected region.
- **Chord (quick reveal)** — tap a revealed number to auto-open its neighbors when the flags match.
- **Two flag modes** — long-press, or a dedicated flag/reveal toggle button.
- **Optional timer & best times** — toggle both in settings; best times are stored per difficulty.
- **Screen orientation** — lock to portrait or allow rotation.
- **Custom launcher icon** — a clean, geometric mine design.

## How to play

- Tap a hidden cell to reveal it.
- Flag a cell (long-press, or use the toggle button) to mark a suspected mine.
- Tap a revealed number to chord-open its neighbors.
- The smiley face restarts the game — it asks for confirmation if a game is still in progress.

## Screenshots

*Coming soon.*

## Requirements

- Android 7.0 (API 24) or later

## Build

Open the project in Android Studio and run, or build a release APK from the command line:

```bash
./gradlew :app:assembleRelease
```

The signed release APK is written to `app/build/outputs/apk/release/app-release.apk`.

> **Note:** Release builds require a `keystore.properties` file at the project root (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`.

## License

[GPL-3.0](LICENSE)
