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
        let phone = Self.phoneLink()
        self.phone = phone
        _model = State(initialValue: WatchModel(
            tokenStore: tokenStore,
            phone: phone,
            backend: Backend(config: config, tokenStore: tokenStore)
        ))
    }

    private let phone: (any PhoneLink)?

    private static func phoneLink() -> (any PhoneLink)? {
        #if canImport(WatchConnectivity)
        WatchConnectivityPhoneLink()
        #else
        nil
        #endif
    }

    public var body: some View {
        ZStack {
            WatchTheme.canvas.ignoresSafeArea()
            switch model.phase {
            case .checking:
                ProgressView().tint(WatchTheme.accent)
            case .notConnected:
                NotConnectedView(model: model)
            case .connected:
                TodayView(model: model)
            }
        }
        .onAppear { model.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.start() }
        }
        // Each time the app comes to the front: connect if there is no session,
        // then read today's training, which may have changed on the phone.
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            await model.connect()
            await model.loadToday()
        }
        // Read today's training as soon as a connect works.
        .onChange(of: model.phase) { _, phase in
            if case .connected = phase {
                Task { await model.loadToday() }
            }
        }
        // And one each time the phone comes into reach while the app is open.
        .task {
            guard let phone else { return }
            for await _ in phone.becameReachable {
                await model.connect()
            }
        }
    }
}

/// Shown until this watch has a session. It says what the last try ran into
/// and offers another.
struct NotConnectedView: View {
    let model: WatchModel

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                Image(systemName: "iphone.and.arrow.forward")
                    .font(.title2)
                    .foregroundStyle(WatchTheme.accent)
                    .accessibilityHidden(true)
                Text(NotConnectedText.title(for: model.problem))
                    .font(.headline)
                    .foregroundStyle(WatchTheme.textPrimary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("connectTitle")
                Text(NotConnectedText.detail(for: model.problem))
                    .font(.footnote)
                    .foregroundStyle(WatchTheme.textSecondary)
                    .multilineTextAlignment(.center)
                if model.isConnecting {
                    ProgressView()
                        .tint(WatchTheme.accent)
                        .accessibilityLabel("Connecting")
                } else {
                    Button("Try Again") {
                        Task { await model.connect() }
                    }
                    .tint(WatchTheme.accent)
                    .accessibilityIdentifier("connectRetry")
                }
            }
            .padding(.horizontal, 4)
        }
    }
}

/// The words for each reason the watch is not connected. Kept apart from the
/// view so a test can read them.
enum NotConnectedText {
    static func title(for problem: WatchModel.Problem?) -> String {
        switch problem {
        case .phoneSignedOut: "Sign in on your iPhone"
        case .failed: "Could not connect"
        case .phoneUnreachable, nil: "Keep your iPhone nearby"
        }
    }

    static func detail(for problem: WatchModel.Problem?) -> String {
        switch problem {
        case .phoneSignedOut: "Open FITT on your iPhone and sign in. This watch connects after that."
        case .failed: "Unlock your iPhone, check its connection, and try again."
        case .phoneUnreachable, nil: "This watch connects by itself when your signed-in iPhone is in reach."
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
    static let card = Color(red: 0x15 / 255, green: 0x19 / 255, blue: 0x20 / 255)
    /// Text on the accent color.
    static let onAccent = Color(red: 0x0B / 255, green: 0x0D / 255, blue: 0x10 / 255)
}
