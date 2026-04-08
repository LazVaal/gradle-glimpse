<div align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Gradle Glimpse Logo" width="120" height="120">

# Gradle Glimpse

**A lifesaver for Flutter developers migrating to AGP 8 and 9.**
</div>

---

Updating the Android Gradle Plugin (AGP) in a mature Flutter project often breaks the build due to hidden legacy native dependencies. **Gradle Glimpse** is an Android Studio and IntelliJ IDEA plugin that lives right inside your `pubspec.yaml`, giving you instant diagnostic insights into the native Android health of your Flutter packages.

## ✨ Features

* **🔍 Instant Compatibility Checks:** Hover over any Android-enabled plugin in your `pubspec.yaml` to instantly see its required AGP version.
* **🧩 Transitive Dependency Tracing:** Uncover "hidden" native Android dependencies dragged in by pure Dart packages (e.g., finding out a harmless UI package is secretly pulling in a broken legacy Android library).
* **🚨 AGP 9 Readiness:** Automatically detects critical AGP 9 compilation blockers, including:
    * The legacy `kotlin-android` apply script syntax.
    * Missing Android namespaces.
    * Hardcoded, outdated Java requirements (<= Java 11).
* **🛠 Flutter Auto-Healing Detection:** Intelligently warns you when Flutter's background scripts are secretly patching legacy code at runtime, allowing you to address technical debt before it breaks in future updates.

## 🚀 Installation

**Via JetBrains Marketplace (Recommended)**
1. Open Android Studio or IntelliJ IDEA.
2. Navigate to **Settings / Preferences** > **Plugins** > **Marketplace**.
3. Search for **"Gradle Glimpse"**.
4. Click **Install** and restart your IDE.

**Manual Installation**
1. Download the latest `.zip` release from the JetBrains Marketplace web page.
2. Go to **Settings** > **Plugins** > ⚙️ (Gear Icon) > **Install Plugin from Disk...**
3. Select the `.zip` file and restart your IDE.

## 💡 Usage

Gradle Glimpse requires zero configuration!

1. Open any Flutter project in Android Studio.
2. Ensure you have run `flutter pub get` so your `.dart_tool` cache is populated.
3. Open your `pubspec.yaml` file.
4. Look for the Glimpse icons in the left-hand gutter next to your dependencies.
    * ✅ **Green Checkmark:** All native dependencies are fully modernized.
    * ❌ **Red Warning:** Legacy Android issues detected.
5. **Hover over the icon** to see the full diagnostic tree and your project's safe AGP upgrade range!

## 🛠 Building from Source

If you want to contribute to Gradle Glimpse or build it locally, you don't need to have Gradle pre-installed. The repository includes a Gradle Wrapper.

```bash
# Clone the repository
git clone [https://github.com/LazVaal/gradle-glimpse.git](https://github.com/LazVaal/gradle-glimpse.git)
cd gradle-glimpse

# Run the automated test suite
./gradlew test

# Build the plugin .zip artifact (Outputs to build/distributions/)
./gradlew buildPlugin

# Run a sandboxed IDE to test changes live
./gradlew runIde