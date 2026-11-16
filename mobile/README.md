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
cd FittKit && swift test        # core unit tests
xcodegen && open FITT.xcodeproj # generate and open the Xcode project
```

Setup: Xcode with an iOS simulator runtime, and `brew install xcodegen`.
