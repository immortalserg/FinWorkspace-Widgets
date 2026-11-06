# Android release procedure

Release APKs must be signed with a persistent key. **Keep the .jks file and passwords safe**: losing the key prevents updates to existing installs.

Generate a keystore locally (do not commit it):

```bash
keytool -genkeypair -v -keystore finworkspace-widgets-release.jks -alias finworkspace -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 finworkspace-widgets-release.jks
```

In GitHub repository Settings → Secrets and variables → Actions, configure:

- `ANDROID_KEYSTORE_BASE64`: base64 output of the keystore file
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: `finworkspace`
- `ANDROID_KEY_PASSWORD`: key password

Merge the development pull request into `main`, ensure CI passes, then create and push an annotated version tag:

```bash
git switch main
git pull --ff-only
git tag -a v1.0.0 -m "FinWorkspace Widgets v1.0.0"
git push origin v1.0.0
```

The tag starts `.github/workflows/release.yml`, builds a signed release APK and publishes it as a GitHub Release asset.

**Important:** previous GitHub Actions debug APKs are signed with a different debug key. Android will not allow an in-place update from debug to release: uninstall the debug build first (`adb uninstall lu.finworkspace.widgets`), which also deletes local app settings.

Never add keystore or signing passwords to the repository. GitHub Actions secrets are used only for release signing.
