# Contributing

Thank you for helping. This app follows the same rules as the
[server](https://github.com/Philippe-Rusare/ultra-sms-gateway/blob/main/CONTRIBUTING.md); the points below
are specific to Android.

## Before you start

- Open an issue first for a feature or a change of behaviour, so that we agree on the approach.
- Never post real phone numbers, device tokens or message texts in issues, logs or screenshots.
- Security problems: report them privately, see the server's
  [SECURITY.md](https://github.com/Philippe-Rusare/ultra-sms-gateway/blob/main/SECURITY.md).

## Development setup

Requirements: Android Studio (or JDK 17+ and the Android SDK), and a phone or an emulator with Android 8.0+.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Guidelines

- **Keep the logic testable.** Message handling lives in `engine` and does not depend on Android; add unit
  tests there for any behaviour change.
- **The device protocol is a contract** with deployed servers. Follow
  [docs/device-protocol.md](https://github.com/Philippe-Rusare/ultra-sms-gateway/blob/main/docs/device-protocol.md);
  ignore unknown fields, never rename or remove one. A protocol change starts in the server repository.
- **Privacy.** Never log or display a message text, the device token or a full phone number.
- **Battery and network.** The app runs all day on a phone: no polling loops, no work on the main thread,
  reconnect with a growing delay.
- **Strings.** Every user-visible text goes in `res/values/strings.xml`, with its French translation in
  `res/values-fr/strings.xml`.
- **Style.** Kotlin official code style, English names and comments.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Make your change with its tests.
3. Run `./gradlew testDebugUnitTest assembleDebug`.
4. Open the pull request, describe what changed and how you tested it (emulator, phone model, Android version).

By contributing, you agree that your contribution is licensed under the [Apache License 2.0](LICENSE).
