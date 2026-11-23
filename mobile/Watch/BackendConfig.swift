import FittAPI
import Foundation

// Which FITT API the watch talks to. The same rule as on the phone: Debug builds
// may be pointed at a backend on this Mac through FITT_API_BASE_URL, and Release
// builds ignore the variable. Nothing secret belongs in this file.
let backendConfig: BackendConfig = {
    #if DEBUG
    if let override = ProcessInfo.processInfo.environment["FITT_API_BASE_URL"],
       let url = URL(string: override) {
        return BackendConfig(baseURL: url)
    }
    #endif
    return BackendConfig(baseURL: URL(string: "https://fittapp.dev")!)
}()
