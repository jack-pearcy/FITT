import SwiftUI

/// Motion in one place. Every animated change in the app goes through `motion`,
/// `entrance`, or `Theme.animation(_:reduceMotion:)`, so the Reduce Motion
/// setting is honored by all of them and no screen has to remember it.
extension Theme {
    /// For a tap answering back: a press, a toggle, a number that ticks.
    static let quick = Animation.spring(response: 0.24, dampingFraction: 0.86)

    /// What every animation becomes under Reduce Motion: a short fade, nothing travels.
    static let calm = Animation.easeOut(duration: 0.18)

    /// The animation to hand to `withAnimation` from an action.
    static func animation(_ animation: Animation = Theme.spring, reduceMotion: Bool) -> Animation {
        reduceMotion ? calm : animation
    }
}

private struct Motion<Value: Equatable>: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let animation: Animation
    let value: Value

    func body(content: Content) -> some View {
        content.animation(Theme.animation(animation, reduceMotion: reduceMotion), value: value)
    }
}

private struct Entrance: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let transition: AnyTransition

    func body(content: Content) -> some View {
        content.transition(reduceMotion ? .opacity : transition)
    }
}

extension AnyTransition {
    /// A new row or card: it fades in while it grows the last few points to full size.
    static var settle: AnyTransition { .opacity.combined(with: .scale(scale: 0.94)) }
}

extension View {
    /// Animates this view when `value` changes. Under Reduce Motion the change fades.
    func motion<Value: Equatable>(_ animation: Animation = Theme.spring, value: Value) -> some View {
        modifier(Motion(animation: animation, value: value))
    }

    /// How this view arrives and leaves. Under Reduce Motion it only fades.
    func entrance(_ transition: AnyTransition = .settle) -> some View {
        modifier(Entrance(transition: transition))
    }
}

/// The main action of a screen: a filled accent bar that gives a little under the finger.
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(Theme.canvas)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(Theme.accent, in: RoundedRectangle(cornerRadius: Theme.rowRadius, style: .continuous))
            .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.4)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .animation(Theme.animation(Theme.quick, reduceMotion: reduceMotion), value: configuration.isPressed)
    }
}

/// Stands in for a list that is still loading: the outline of its rows, breathing
/// slowly. Under Reduce Motion the outline holds still.
struct SkeletonList: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dimmed = false
    var rows = 4

    var body: some View {
        VStack(spacing: Theme.Space.m) {
            ForEach(0..<rows, id: \.self) { _ in
                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .fill(Theme.card)
                    .frame(height: 64)
            }
            Spacer(minLength: 0)
        }
        .padding(Theme.Space.l)
        .opacity(dimmed ? 0.45 : 1)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                dimmed = true
            }
        }
        .accessibilityElement()
        .accessibilityLabel("Loading")
    }
}
