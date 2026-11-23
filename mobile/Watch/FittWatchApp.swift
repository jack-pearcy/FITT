import FittWatch
import SwiftUI

// The watch app target stays as thin as the phone's. Its screens and its model
// live in the FittWatch package target, where they are tested without a watch.
@main
struct FittWatchApp: App {
    var body: some Scene {
        WindowGroup { WatchRootView(config: backendConfig) }
    }
}
