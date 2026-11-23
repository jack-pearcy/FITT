import FittUI
import SwiftUI

// The app target stays this thin on purpose. Screens and server access live in
// FittUI, rules live in FittCore, and both are testable without launching the app.
@main
struct FittApp: App {
    private let services: AppServices
    // Answers the paired watch when it asks to be connected. It is started here
    // and not in a view: the watch can start this app in the background, where
    // no view appears.
    private let watch: PhoneWatchSession

    init() {
        let services = AppServices(config: backendConfig)
        self.services = services
        self.watch = PhoneWatchSession(responder: WatchLinkResponder(backend: services.backend))
        watch.activate()
    }

    var body: some Scene {
        WindowGroup { RootView(services: services) }
    }
}
