# Focus Time Tracker

**Focus Time Tracker** is a modern, feature-rich Android time-tracking and productivity application built with **Jetpack Compose** and **Material 3**. Designed to help users maintain deep focus and organize daily productivity habits efficiently.

---

## 📱 Pre-built APK

You can find the pre-built debug APK in the repository under the [apk/](file:///home/bct/AndroidStudioProjects/FocusTimeTracker/apk/app-debug.apk) folder:
- **Path:** `apk/app-debug.apk`

---

## ✨ Key Features

### ⏱️ Task Management & Customization
- **Create Tasks:** Easily add new tasks from the sidebar navigation drawer.
- **Color & Icon Pickers:** Choose from a curated color palette and icons (Star, Work, Home, Code, School, Coffee, etc.) for each task.
- **Task Editing & Deletion:** Rename tasks or delete them with built-in confirmation dialogs.

### 🎨 Immersive UI & Dynamic Design
- **Hero Timer Card:** The active/running timer automatically floats to the top-left position and displays as a prominent circular tile at **1.5x size**.
- **Live Color Styling:** Active timer cards dynamically use the exact color chosen for that task.

### 🔒 Background & Lock Screen Operation
- **Screen-Off Ticking:** Timers continue running seamlessly even when the screen is turned off or the device is locked, powered by Android **Foreground Services** and partial **WakeLocks**.
- **Lock Screen Controls:** View live elapsed time and stop timers directly from the lock screen notification.

### 📊 History & CSV Reports
- **Midnight Crossover:** Automatically rolls over daily focus time into history records at midnight.
- **Color-Coded History:** History logs are grouped and styled with alternating soft background colors per date for effortless readability.
- **CSV Export:** Export historical focus data in standard CSV format (`date`, `task name`, `duration`) compatible with Google Drive and spreadsheet applications.

### 💾 Persistence & Cloud Backup
- **State Preservation:** All tasks, accumulated durations, and history records persist automatically across app relaunches.
- **Yearly CSV Backups:** Manual backup & restore feature supporting yearly CSV logs named `focus_time_tracker_<year>.csv`.
- **Daily Auto-Backup:** Optional background auto-backup powered by Jetpack **WorkManager** to sync your data daily.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework:** Jetpack Compose & Material 3
- **Asynchronous Work:** Kotlin Coroutines & Flow
- **Background Processing:** Foreground Service (`TimerService`) & WorkManager (`BackupWorker`)
- **Data Persistence:** SharedPreferences & JSON/CSV serialization via Android `org.json`
- **File Management:** Storage Access Framework (SAF) for seamless Google Drive and device storage integration

---

## 🚀 Getting Started

1. Clone or open this repository in **Android Studio** (Koala or newer recommended).
2. Sync project with Gradle files.
3. Build and run the app on an Android device or emulator (API 26+), or install the pre-built APK from [`apk/app-debug.apk`](file:///home/bct/AndroidStudioProjects/FocusTimeTracker/apk/app-debug.apk).
