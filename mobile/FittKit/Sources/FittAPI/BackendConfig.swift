import Foundation

/// Where the FITT API lives. The app target builds one and passes it to `RootView`.
///
/// The base URL is the only backend value the app ships with. There is no API key:
/// access comes from the signed-in user's token, which is kept in the Keychain.
public struct BackendConfig: Sendable {
    public let baseURL: URL

    public init(baseURL: URL) {
        self.baseURL = baseURL
    }
}
