import FittUI
import SwiftUI

// The app target stays this thin on purpose. Screens and server access live in
// FittUI, rules live in FittCore, and both are testable without launching the app.
@main
struct FittApp: App {
    var body: some Scene {
        WindowGroup { RootView(config: backendConfig) }
    }
}
