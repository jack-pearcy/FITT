import FittUI
import Foundation

// Which FITT API the app talks to. Nothing secret belongs in this file.
//
// For a backend running on this Mac, the simulator reaches it at
// http://localhost:8080 (plain http to local addresses is allowed in project.yml).
let backendConfig = BackendConfig(baseURL: URL(string: "https://fittapp.dev")!)
