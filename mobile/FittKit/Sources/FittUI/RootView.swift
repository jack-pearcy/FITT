import SwiftUI

/// The single view the app target shows. It will own the app model and choose
/// between loading, sign in, onboarding, and the main tabs.
public struct RootView: View {
    private let config: BackendConfig

    public init(config: BackendConfig) {
        self.config = config
    }

    public var body: some View {
        VStack(spacing: 12) {
            Text("FITT")
                .font(.largeTitle.bold())
            Text(config.baseURL.host() ?? config.baseURL.absoluteString)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}

#Preview {
    RootView(config: BackendConfig(baseURL: URL(string: "https://fittapp.dev")!))
}
