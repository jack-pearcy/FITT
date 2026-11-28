import FittCore
import Foundation
import UserNotifications

/// The local notification that says rest is over. Behind a protocol so the
/// tests can see what was scheduled without the system notification centre,
/// which needs an app bundle. Nothing here is sent off the device (Q8).
@MainActor
public protocol RestAlerts: AnyObject {
    /// Asks the user the first time; afterwards the system answers from what
    /// they chose, without asking again. True when alerts are allowed.
    func requestPermission() async -> Bool
    /// Schedules the one rest alert for `date`, replacing any earlier one.
    func schedule(at date: Date, now: Date)
    /// Removes the rest alert, pending or already shown.
    func cancel()
}

/// The rest alert through UserNotifications.
@MainActor
public final class SystemRestAlerts: RestAlerts {
    static let identifier = "dev.fittapp.fitt.rest"

    public init() {}

    public func requestPermission() async -> Bool {
        // A failure to ask is a no: the in-app timer still runs.
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    public func schedule(at date: Date, now: Date) {
        let content = UNMutableNotificationContent()
        content.title = "Rest is over"
        content.body = "Time for your next set."
        content.sound = .default
        // The trigger needs a wait above zero.
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, date.timeIntervalSince(now)), repeats: false)
        // The same identifier replaces the alert already pending.
        let request = UNNotificationRequest(identifier: Self.identifier, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    public func cancel() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
    }
}

/// Settings kept on this device only.
public struct DeviceSettings: Sendable {
    private static let restLengthKey = "restLength"
    // UserDefaults is documented as safe to use from any thread.
    private nonisolated(unsafe) let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// The rest length chosen in Settings, or 2 minutes when none was chosen.
    public var restLength: TimeInterval {
        get {
            let stored = defaults.double(forKey: Self.restLengthKey)
            return stored > 0 ? RestTimer.clamped(stored) : RestTimer.defaultLength
        }
        nonmutating set { defaults.set(RestTimer.clamped(newValue), forKey: Self.restLengthKey) }
    }
}
