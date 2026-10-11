# Mines Assistant

Android app for tracking Stake Mines rounds. Shows a 5×5 board, 1–24 mine setting, randomly selected next-tile suggestion, and mathematically correct conditional next-pick survival chance assuming a uniformly random board. Suggestions cannot predict hidden mines.

## Build
Open this folder in Android Studio (JDK 17), allow Gradle to sync, then choose Build > Build APK(s). Android Gradle Plugin 8.7.3 and Kotlin 2.0.21. No internet permission, account access, or Stake integration.

## Usage
Choose mine count, tap **Suggest Next Tile** for a random unopened tile, and mark tiles safe only after Stake reveals them. Tap **New Game** to reset. Do not mark a mine as safe; this tracker assumes every marked tile was revealed safe.
