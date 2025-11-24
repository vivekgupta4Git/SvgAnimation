# 🖼️ SVG Layer Viewer – Compose Desktop

A lightweight **Compose Desktop** application that loads, parses, visualizes, and controls SVG layers and shapes.  
Made for developers, designers, and anyone who needs to inspect SVG structure with real-time visibility toggles.

---

## ✨ Features

### ✔ Load & View SVG Files  
- Supports standalone SVG files  
- Custom SVG parser (paths, groups, circles, polygons, polylines, rects)

### ✔ Real-Time Canvas Preview  ( work in progress)
- Only selected elements are rendered   
- Great for debugging large vector files

### ✔ Modern Compose Desktop UI  
- Split-screen layout  
- Adjustable left-pane width using Slider  

---

## 🎥 Demo Video  



https://github.com/user-attachments/assets/8cd5f840-40f3-4786-a58d-6d1393dd2bf0


---

## 🛠️ Tech Stack

- **Kotlin**
- **Jetpack Compose Desktop**
- **Coroutines**
- **Custom SVG Parsing Logic**

---

This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
