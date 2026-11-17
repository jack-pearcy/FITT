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
            case .signedIn:
                SignedInView()
            }
        }
        .environment(model)
        .fittTheme()
        .task { await model.start() }
    }
}

/// Chooses the screen for a signed-in user from what the server says is done.
private struct SignedInView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if model.onboarding?.onboardingComplete == true {
            HomeView()
        } else {
            // Onboarding has no tabs, so it carries its own way out.
            NavigationStack {
                Group {
                    if let onboarding = model.onboarding {
                        if onboarding.biometricsComplete {
                            GoalsStepView()
                        } else {
                            BiometricsStepView()
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
