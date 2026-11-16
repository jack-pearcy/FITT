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
                SignedInView(userId: userId)
            }
        }
        .environment(model)
        .task { await model.start() }
    }
}

/// Chooses the screen for a signed-in user from what the server says is done.
private struct SignedInView: View {
    @Environment(AppModel.self) private var model
    let userId: Int64

    var body: some View {
        NavigationStack {
            Group {
                if let onboarding = model.onboarding {
                    if !onboarding.biometricsComplete {
                        BiometricsStepView()
                    } else if !onboarding.goalsComplete {
                        GoalsStepView()
                    } else {
                        HomePlaceholderView(userId: userId)
                    }
                } else {
                    OnboardingUnknownView()
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Sign Out") {
                        Task { await model.signOut() }
                    }
                        .accessibilityIdentifier("signOut")
                }
            }
        }
    }
}

/// Shown while the onboarding status is loading, or when it could not be loaded.
private struct OnboardingUnknownView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if let message = model.errorMessage {
            VStack(spacing: 16) {
                Text(message)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("error")
                Button("Try Again") {
                    Task { await model.refreshOnboarding() }
                }
                .disabled(model.isWorking)
                .accessibilityIdentifier("retry")
            }
            .padding()
        } else {
            ProgressView()
        }
    }
}

/// Stands in for the main tabs until those screens exist.
private struct HomePlaceholderView: View {
    let userId: Int64

    var body: some View {
        List {
            LabeledContent("User id", value: String(userId))
            LabeledContent("Onboarding", value: "Complete")
        }
        .navigationTitle("FITT")
    }
}
