import FittUI
import Foundation

// Which FITT API the app talks to. Nothing secret belongs in this file.
//
// Debug builds read FITT_API_BASE_URL from the environment, so the simulator can
// be pointed at a backend on this Mac without editing code:
//   SIMCTL_CHILD_FITT_API_BASE_URL=http://localhost:8080 xcrun simctl launch booted dev.fittapp.fitt
// Plain http to local addresses is allowed in project.yml. Release builds ignore the variable.
let backendConfig: BackendConfig = {
    #if DEBUG
    if let override = ProcessInfo.processInfo.environment["FITT_API_BASE_URL"],
       let url = URL(string: override) {
        return BackendConfig(baseURL: url)
    }
    #endif
    return BackendConfig(baseURL: URL(string: "https://fittapp.dev")!)
}()
