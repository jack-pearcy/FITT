import FittCore
import SwiftUI

/// The rest after a set, pinned to the bottom of the workout: a ring that
/// empties, the time left, +30 s and Skip. It reads the time left from the end
/// date on each tick, so it is right again the moment the app comes back.
struct RestBar: View {
    let timer: RestTimer
    let addTime: () -> Void
    let skip: () -> Void
    @ScaledMetric(relativeTo: .title2) private var ringSize: CGFloat = 40
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        TimelineView(.periodic(from: timer.startedAt, by: 1)) { context in
            let left = timer.remaining(at: context.date)
            let over = left == 0
            // One row is too narrow at the accessibility sizes, where words broke
            // letter by letter: the buttons go on a second row, as in SetForm.
            let stacked = typeSize.isAccessibilitySize
            let layout = stacked
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: Theme.Space.m))
                : AnyLayout(HStackLayout(spacing: Theme.Space.m))
            layout {
                HStack(spacing: Theme.Space.m) {
                    RestRing(fraction: timer.fractionLeft(at: context.date), over: over)
                        .frame(width: ringSize, height: ringSize)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(over ? "Rest over" : "Rest").sectionLabel()
                        Text(RestTimer.clock(left))
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(over ? Theme.accent : Theme.textPrimary)
                            .ticking()
                    }
                    .lineLimit(1)
                    .fixedSize()
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Rest")
                    .accessibilityValue(over ? "Over" : "\(RestTimer.spoken(left)) left")
                    .accessibilityIdentifier("restTime")
                }
                if !stacked { Spacer(minLength: Theme.Space.s) }
                HStack(spacing: Theme.Space.m) {
                    Button("+30 s", action: addTime)
                        .buttonStyle(PillButtonStyle())
                        .accessibilityLabel("Add 30 seconds")
                        .accessibilityIdentifier("restAdd")
                    Button(over ? "Done" : "Skip", action: skip)
                        .secondaryAction()
                        .accessibilityLabel(over ? "Close rest" : "Skip rest")
                        .accessibilityIdentifier("restSkip")
                }
                .lineLimit(1)
                .fixedSize()
            }
            // Full width in both layouts, so the bar covers the list behind it.
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Theme.Space.l)
            .padding(.vertical, Theme.Space.m)
            .glassBar()
            // A light tap when rest runs out while the screen is open.
            .sensoryFeedback(.success, trigger: over) { _, isOver in isOver }
            // The digits roll from one second to the next.
            .motion(Theme.quick, value: Int(left.rounded(.up)))
        }
        .accessibilityIdentifier("restBar")
    }
}

/// A ring that empties as rest runs down, and fills with the accent when it is over.
private struct RestRing: View {
    let fraction: Double
    let over: Bool

    var body: some View {
        ZStack {
            Circle().stroke(Theme.well, lineWidth: 4)
            Circle()
                .trim(from: 0, to: over ? 1 : fraction)
                .stroke(Theme.accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .accessibilityHidden(true)
    }
}

#if DEBUG
#Preview("Rest, running") {
    VStack {
        Spacer()
        RestBar(timer: RestTimer(length: 90, now: .now), addTime: {}, skip: {})
    }
    .fittTheme()
}
#endif
