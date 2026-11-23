import FittAPI
import SwiftUI

/// The one token store and the one backend client of the phone app.
///
/// The app target makes this once and hands it to everything that talks to the
/// server: the screens, and the part that answers the watch. They must share
/// it, because only one of them may renew the session at a time.
public struct AppServices: Sendable {
    public let tokenStore: any TokenStore
    public let backend: Backend

    public init(config: BackendConfig) {
        let tokenStore = KeychainTokenStore()
        self.tokenStore = tokenStore
        self.backend = Backend(config: config, tokenStore: tokenStore)
    }
}

/// The single view the app target shows. It owns the app model and chooses
/// between loading, sign in, and the signed-in screens.
public struct RootView: View {
    @State private var model: AppModel

    public init(services: AppServices) {
        _model = State(initialValue: AppModel(
            backend: services.backend,
            tokenStore: services.tokenStore,
            drafts: DraftStore()
        ))
    }

    public var body: some View {
        Group {
            switch model.phase {
            case .loading:
                ProgressView()
                    .transition(.opacity)
            case .signedOut:
                SignInView()
                    .transition(.opacity)
            case .signedIn:
                SignedInView()
                    .transition(.opacity)
            }
        }
        // One screen fades into the next, so signing in or out is not a hard cut.
        .motion(value: model.phase)
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
                // The loading and failed states have no title of their own to hang this on.
                .canvas()
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Sign Out") {
                            Task { await model.signOut() }
                        }
                        .accessibilityIdentifier("signOut")
                        .secondaryAction()
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
