# FITT iOS

Native SwiftUI client for the FITT API. iOS 17 or later.

## Layout

```
project.yml                 XcodeGen spec. Generates FITT.xcodeproj, which is not committed.
App/                        The @main entry point and the API base URL. Nothing else.
FittKit/                    Swift package
  Sources/FittCore/         Rules and API models. Foundation only.
  Sources/FittUI/           Backend client, app model, SwiftUI screens.
  Tests/FittCoreTests/      Unit tests for FittCore (Swift Testing).
```

FittCore imports no UI or networking framework, so its tests run on the Mac without a simulator.

## Commands

```
cd FittKit && swift test        # unit tests for FittCore and FittUI
xcodegen && open FITT.xcodeproj # generate and open the Xcode project
```

CI runs `swift test` on a macOS runner for every pull request (the `mobile-test` job in
`.github/workflows/ci.yml`).

## Running against a local backend

The app talks to https://fittapp.dev by default. A debug build reads `FITT_API_BASE_URL`,
so the simulator can use a backend on this Mac instead:

```
docker compose up -d mysql backend   # from the repo root, serves http://localhost:8080
SIMCTL_CHILD_FITT_API_BASE_URL=http://localhost:8080 xcrun simctl launch booted dev.fittapp.fitt
```

Build the app with normal signing (the Xcode default). An unsigned build cannot write to
the Keychain, so sign in fails after the server has already accepted the credentials.

Setup: Xcode with an iOS simulator runtime, and `brew install xcodegen`.
