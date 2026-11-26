import FittCore
import Foundation
import Testing
@testable import FittUI

@Suite("Settings screen state")
struct SettingsStateTests {
    private let metric = Biometrics(
        heightCm: Decimal(string: "180.34")!, weightKg: Decimal(string: "81.65")!,
        dateOfBirth: CalendarDate(year: 1990, month: 5, day: 17),
        biologicalSex: .male, activityLevel: .active, unitsPreference: .metric)

    private func state(_ biometrics: Biometrics?, missing: Bool = false, error: String? = nil, offline: Bool = false) -> ScreenState<Biometrics> {
        SettingsContent.screenState(biometrics: biometrics, isMissing: missing, errorMessage: error, isOffline: offline)
    }

    @Test("nothing loaded and no error is loading")
    func loading() {
        #expect(state(nil) == .loading)
    }

    @Test("loaded biometrics are content, and stay on screen when a later change fails")
    func content() {
        #expect(state(metric) == .content(metric))
        #expect(state(metric, error: "Cannot reach the server. Check the connection.", offline: true) == .content(metric))
    }

    @Test("a user with none saved is empty")
    func empty() {
        #expect(state(nil, missing: true) == .empty)
    }

    @Test("a load that failed is an error with its message")
    func failed() {
        #expect(state(nil, error: "The server gave an unexpected answer. Try again.")
            == .failed("The server gave an unexpected answer. Try again."))
    }

    @Test("a load that could not reach the server is offline")
    func offline() {
        #expect(state(nil, error: "Cannot reach the server. Check the connection.", offline: true) == .offline)
    }
}
