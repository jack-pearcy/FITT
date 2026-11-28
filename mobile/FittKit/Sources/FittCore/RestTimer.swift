import Foundation

/// The rest between two sets. It holds the moment rest ends, not a count of
/// ticks, so the time left is right after the app was in the background or
/// the phone was locked.
public struct RestTimer: Equatable, Sendable {
    /// Rest when the user has chosen no length (Q8).
    public static let defaultLength: TimeInterval = 120
    /// The lengths Settings offers: 30 seconds to 10 minutes, in steps of 15 seconds.
    public static let lengths: ClosedRange<TimeInterval> = 30...600
    public static let lengthStep: TimeInterval = 15
    /// What one tap on +30 s adds.
    public static let extra: TimeInterval = 30

    public let startedAt: Date
    public private(set) var endsAt: Date

    public init(length: TimeInterval, now: Date) {
        startedAt = now
        endsAt = now.addingTimeInterval(Self.clamped(length))
    }

    /// Seconds left, never below zero.
    public func remaining(at now: Date) -> TimeInterval {
        max(0, endsAt.timeIntervalSince(now))
    }

    public func isOver(at now: Date) -> Bool {
        remaining(at: now) == 0
    }

    /// How much of the rest is left, from 1 at the start to 0 at the end.
    public func fractionLeft(at now: Date) -> Double {
        let total = endsAt.timeIntervalSince(startedAt)
        guard total > 0 else { return 0 }
        return min(1, max(0, remaining(at: now) / total))
    }

    /// Adds 30 seconds. Rest that is already over starts again from now, so
    /// the tap always gives 30 seconds more to rest.
    public mutating func addExtra(now: Date) {
        endsAt = max(endsAt, now).addingTimeInterval(Self.extra)
    }

    /// A length inside the offered range, on a 15 second step.
    public static func clamped(_ length: TimeInterval) -> TimeInterval {
        let stepped = (length / lengthStep).rounded() * lengthStep
        return min(lengths.upperBound, max(lengths.lowerBound, stepped))
    }

    /// "1:30" for 90 seconds. Rounds up, so the timer shows 0:01 until rest is over.
    public static func clock(_ seconds: TimeInterval) -> String {
        let whole = Int(seconds.rounded(.up))
        let secondsPart = whole % 60
        return "\(whole / 60):\(secondsPart < 10 ? "0" : "")\(secondsPart)"
    }

    /// "1 minute 30 seconds", for VoiceOver and the Settings row.
    public static func spoken(_ seconds: TimeInterval) -> String {
        let whole = Int(seconds.rounded(.up))
        let minutes = whole / 60
        let rest = whole % 60
        var parts: [String] = []
        if minutes > 0 { parts.append("\(minutes) \(minutes == 1 ? "minute" : "minutes")") }
        if rest > 0 || minutes == 0 { parts.append("\(rest) \(rest == 1 ? "second" : "seconds")") }
        return parts.joined(separator: " ")
    }
}
