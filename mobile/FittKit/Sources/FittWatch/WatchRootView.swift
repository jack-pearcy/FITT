import FittAPI
import SwiftUI

/// The single view the watch app target shows.
public struct WatchRootView: View {
    @State private var model: WatchModel
    @Environment(\.scenePhase) private var scenePhase

    /// The watch keeps its session under its own Keychain service, apart from
    /// the phone's. The two are separate sign-ins on the server, so one device
    /// renewing its tokens never invalidates the other's.
    public init(config: BackendConfig, tokenStore: any TokenStore = KeychainTokenStore(service: "dev.fittapp.fitt.watch")) {
        _model = State(initialValue: WatchModel(tokenStore: tokenStore))
    }

    public var body: some View {
        ZStack {
            WatchTheme.canvas.ignoresSafeArea()
            switch model.phase {
            case .checking:
                ProgressView().tint(WatchTheme.accent)
            case .notConnected:
                NotConnectedView()
            case .connected:
                ConnectedView()
            }
        }
        .onAppear { model.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.start() }
        }
    }
}

/// Shown until the phone has connected this watch.
struct NotConnectedView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                Image(systemName: "iphone.and.arrow.forward")
                    .font(.title2)
                    .foregroundStyle(WatchTheme.accent)
                    .accessibilityHidden(true)
                Text("Open FITT on your iPhone")
                    .font(.headline)
                    .foregroundStyle(WatchTheme.textPrimary)
                    .multilineTextAlignment(.center)
                Text("Sign in there and this watch connects by itself.")
                    .font(.footnote)
                    .foregroundStyle(WatchTheme.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 4)
        }
    }
}

/// Placeholder for the workout screens, which arrive with the session handover.
struct ConnectedView: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .font(.title2)
                .foregroundStyle(WatchTheme.positive)
                .accessibilityHidden(true)
            Text("Connected")
                .font(.headline)
                .foregroundStyle(WatchTheme.textPrimary)
        }
    }
}

/// The phone's colors, for the few the watch uses. The phone's `Theme` lives
/// with its screens in FittUI, which does not build for watchOS, so the values
/// are repeated here. Change them in both places.
enum WatchTheme {
    static let canvas = Color(red: 0x0B / 255, green: 0x0D / 255, blue: 0x10 / 255)
    static let textPrimary = Color(red: 0xF4 / 255, green: 0xF6 / 255, blue: 0xF9 / 255)
    static let textSecondary = Color(red: 0x8A / 255, green: 0x94 / 255, blue: 0xA6 / 255)
    static let accent = Color(red: 0xC8 / 255, green: 0xFF / 255, blue: 0x00 / 255)
    static let positive = Color(red: 0x10 / 255, green: 0xB9 / 255, blue: 0x81 / 255)
}
