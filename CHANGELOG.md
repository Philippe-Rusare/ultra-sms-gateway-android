# Changelog

All notable changes to the Android app. Versions follow [Semantic Versioning](https://semver.org/);
the device protocol it speaks is documented in the
[server repository](https://github.com/Philippe-Rusare/ultra-sms-gateway/blob/main/docs/device-protocol.md).

## [0.2.0] - unreleased

### Added

- Pairing by QR code: **Scan the QR code** reads the code shown by the dashboard and receives the server
  address and the device token, with nothing to type. Needs a server with the pairing endpoint
  (`POST /v1/device/pair`).
- The pairing link (`ultrasms://pair?...`) can also be opened directly on a phone that cannot scan.

### Changed

- The camera permission is requested when scanning. Typing the server URL and a token by hand still works.

## [0.1.0] - 2026-11-10

First release, for developers and early testers.

### Added

- Connection to an Ultra SMS Gateway server over WebSocket, authenticated by a device token, with automatic
  reconnection (1 s up to 60 s) and immediate retry when the network comes back.
- Sending with the phone's SIM card, one message every 3 seconds, long messages split into parts; choice of
  the SIM card on dual-SIM phones.
- Reports to the server: acknowledgement, sent, delivered and failed with Android's reason code. Reports
  are stored on the phone until the connection accepts them.
- Messages pushed again by the server are recognised and never sent twice; expired messages are reported
  instead of being sent late.
- Foreground service that keeps running with the screen off and starts again after a reboot or an update.
- Heartbeat every 30 seconds: battery, charging, signal, operator, network and app version.
- One-screen interface in English and French: state, counters, server settings, permissions, start and stop.
- Device token encrypted with the Android Keystore; message texts erased once sent; no backup.
- Adaptive, themed and notification icons with the Ultra SMS Gateway logo.

[0.1.0]: https://github.com/Philippe-Rusare/ultra-sms-gateway-android/releases/tag/v0.1.0
