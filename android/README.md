# UniHub for Android

Native Kotlin + Jetpack Compose app for asubank.org. It reads the same data as the site and the iOS app
(`assets/app/materials.json`, `pages.json`, `app-config.json`, `drive-map.json`, `broken.json`).

Built in the cloud by `.github/workflows/android.yml` (no Android Studio needed):

- every push to `android/` builds the APK (run page > Artifacts) and saves emulator screenshots to the `android-screenshots` branch
- pushing a tag `android-vX.Y.Z` publishes a public GitHub Release with `UniHub-X.Y.Z.apk`; the app finds new versions from these releases

Releasing a new version: change `versionName` in `app/build.gradle.kts`, `android.latest` in `assets/app/app-config.json`, and `RELEASE_NOTES.md`, push, then push the tag.

## Signing

Without secrets the APK is signed with a CI debug key, which changes between builds, so phones may have to uninstall the old version before installing a new one.
For updates that install over the old version, add these repository secrets (Settings > Secrets and variables > Actions) once:

- `ANDROID_KEYSTORE_BASE64`: the keystore file in base64 (`keytool -genkeypair -v -keystore asubank.jks -alias asubank -keyalg RSA -keysize 2048 -validity 10000`, then `base64 -w0 asubank.jks`)
- `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`

Keep the keystore file safe: every future version must be signed with the same one.
