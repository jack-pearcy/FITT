import SwiftUI

/// The single view the app target shows. It owns the app model and chooses
/// between loading, sign in, and the signed-in screens.
public struct RootView: View {
    @State private var model: AppModel

    public init(config: BackendConfig) {
        let tokenStore = KeychainTokenStore()
        _model = State(initialValue: AppModel(
            backend: Backend(config: config, tokenStore: tokenStore),
            tokenStore: tokenStore
        ))
    }

    public var body: some View {
        Group {
            switch model.phase {
            case .loading:
                ProgressView()
            case .signedOut:
                SignInView()
            case .signedIn(let userId):
                SignedInPlaceholderView(userId: userId)
            }
        }
        .environment(model)
        .task { await model.start() }
    }
}

/// Stands in for onboarding and the main tabs until those screens exist.
private struct SignedInPlaceholderView: View {
    @Environment(AppModel.self) private var model
    let userId: Int64

    var body: some View {
        NavigationStack {
            List {
                LabeledContent("User id", value: String(userId))
                LabeledContent("Onboarding", value: onboardingText)
                if let message = model.errorMessage {
                    Text(message).foregroundStyle(.red)
                }
                Button("Sign Out", role: .destructive) { model.signOut() }
                    .accessibilityIdentifier("signOut")
            }
            .navigationTitle("Signed In")
        }
    }

    private var onboardingText: String {
        switch model.onboardingComplete {
        case true?: "Complete"
        case false?: "Not complete"
        case nil: "Unknown"
        }
    }
}
