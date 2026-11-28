import FittCore
import SwiftUI

/// Sets and volume for each muscle group, week by week, worked out on the
/// device from the finished workouts the app already holds.
struct MuscleVolumeView: View {
    /// How many weeks the screen shows, the current one included. Nonisolated,
    /// so the pure helpers below can read it off the main actor.
    nonisolated static let weeks = 8

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        MuscleVolumeContent(
            state: MuscleVolumeContent.screenState(
                history: model.history,
                errorMessage: model.errorMessage,
                isOffline: model.isOffline,
                now: .now
            ),
            units: model.units,
            retry: { await model.loadSessions() },
            back: { dismiss() }
        )
        .navigationTitle("Weekly Volume")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
        .task {
            if model.history == nil {
                await model.loadSessions()
            }
        }
    }
}

struct MuscleVolumeContent: View {
    let state: ScreenState<[MuscleWeek]>
    let units: UnitsPreference
    let retry: () async -> Void
    let back: () -> Void

    /// Content once a finished workout in the weeks shown holds a set. Empty
    /// for no history, and for a history with nothing in those weeks.
    nonisolated static func screenState(
        history: [WorkoutSession]?,
        errorMessage: String?,
        isOffline: Bool,
        now: Date,
        calendar: Calendar = CalendarDate.gregorian
    ) -> ScreenState<[MuscleWeek]> {
        ScreenState(history: history, errorMessage: errorMessage, isOffline: isOffline) { history in
            let weeks = MuscleWeek.weeks(history, count: MuscleVolumeView.weeks, now: now, calendar: calendar)
            return weeks.contains { $0.totalSets > 0 } ? weeks : nil
        }
    }

    /// "This week", "Last week", then "Week of Nov 9". `weeks` runs newest first.
    /// The date is written in the calendar's time zone, so it is the Monday the week starts on.
    nonisolated static func title(
        of week: MuscleWeek,
        in weeks: [MuscleWeek],
        calendar: Calendar = CalendarDate.gregorian,
        locale: Locale = .autoupdatingCurrent
    ) -> String {
        switch weeks.firstIndex(of: week) {
        case 0: return "This week"
        case 1: return "Last week"
        default:
            let monday = week.start.date(in: calendar).map { date in
                var style = Date.FormatStyle.dateTime.month(.abbreviated).day()
                style.timeZone = calendar.timeZone
                style.locale = locale
                return date.formatted(style)
            }
            return "Week of \(monday ?? week.start.iso)"
        }
    }

    /// "1 set" or "4 sets".
    nonisolated static func setsText(_ sets: Int) -> String {
        "\(sets) \(sets == 1 ? "set" : "sets")"
    }

    /// "1,200 kg", in the user's unit and to a whole number. Nil when the
    /// muscle had only bodyweight sets, which have no volume.
    nonisolated static func volumeText(
        kilograms: Decimal,
        units: UnitsPreference,
        locale: Locale = .autoupdatingCurrent
    ) -> String? {
        guard kilograms > 0 else { return nil }
        let shown = units.shown(kilograms: kilograms)
        return "\(shown.formatted(.number.precision(.fractionLength(0)).locale(locale))) \(units.weightLabel)"
    }

    /// What VoiceOver reads for a whole week: the total, then each muscle, most sets first.
    nonisolated static func summary(of week: MuscleWeek) -> String {
        guard week.totalSets > 0 else { return "No sets" }
        let muscles = week.rankedMuscles.map { "\($0.label) \(setsText(week.sets[$0] ?? 0))" }
        return "\(setsText(week.totalSets)). \(muscles.joined(separator: ", "))"
    }

    var body: some View {
        StatesView(
            state: state,
            empty: EmptyState(
                title: "No sets in the last \(MuscleVolumeView.weeks) weeks",
                systemImage: "chart.bar",
                message: "Finish a workout to see how many sets each muscle group gets each week.",
                actionTitle: "Back to History",
                action: back
            ),
            retry: retry
        ) { weeks in
            ThemedList {
                ForEach(weeks) { week in
                    Section {
                        if week.totalSets == 0 {
                            Text("No sets")
                                .foregroundStyle(Theme.textSecondary)
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                                // The header already says so.
                                .accessibilityHidden(true)
                        }
                        ForEach(week.rankedMuscles, id: \.self) { muscle in
                            MuscleRow(
                                muscle: muscle,
                                sets: week.sets[muscle] ?? 0,
                                kilograms: week.volume[muscle] ?? 0,
                                units: units
                            )
                        }
                    } header: {
                        // Stacked, so a long title and the total never share a line at large text sizes.
                        VStack(alignment: .leading, spacing: Theme.Space.xs) {
                            Text(Self.title(of: week, in: weeks))
                            if week.totalSets > 0 {
                                Text(Self.setsText(week.totalSets)).metric()
                            }
                        }
                        .sectionLabel()
                        .accessibilityElement(children: .ignore)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityLabel(Self.title(of: week, in: weeks))
                        .accessibilityValue(Self.summary(of: week))
                    } footer: {
                        if week.id == weeks.last?.id {
                            Text("Weeks run Monday to Sunday. Volume is weight times reps, so bodyweight sets count as sets with no volume.")
                        }
                    }
                    .accessibilityIdentifier("muscleWeek.\(week.start.iso)")
                }
            }
            // A finished workout changes the counts. Under Reduce Motion they fade.
            .motion(value: weeks)
            .refreshable { await retry() }
        }
        .canvas()
    }
}

/// One muscle group in one week: its name over its sets and volume, so
/// nothing is squeezed or cut at large text sizes.
private struct MuscleRow: View {
    let muscle: MuscleGroup
    let sets: Int
    let kilograms: Decimal
    let units: UnitsPreference

    private var setsText: String { MuscleVolumeContent.setsText(sets) }

    private var volumeText: String? { MuscleVolumeContent.volumeText(kilograms: kilograms, units: units) }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Text(muscle.label)
                .font(.headline)
                .foregroundStyle(Theme.textPrimary)
            Text([setsText, volumeText].compactMap { $0 }.joined(separator: " · "))
                .font(.footnote)
                .metric()
                .foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(muscle.label)
        .accessibilityValue([setsText, volumeText.map { "volume \($0)" }].compactMap { $0 }.joined(separator: ", "))
    }
}

#if DEBUG
@MainActor
private func volumePreview(_ state: ScreenState<[MuscleWeek]>) -> some View {
    NavigationStack {
        MuscleVolumeContent(state: state, units: .metric, retry: {}, back: {})
            .navigationTitle("Weekly Volume")
    }
    .fittTheme()
}

#Preview("Volume, content") {
    volumePreview(MuscleVolumeContent.screenState(history: StatsFixtures.sessions, errorMessage: nil, isOffline: false, now: .now))
}

#Preview("Volume, largest text") {
    volumePreview(MuscleVolumeContent.screenState(history: StatsFixtures.sessions, errorMessage: nil, isOffline: false, now: .now))
        .dynamicTypeSize(.accessibility5)
}

#Preview("Volume, loading") { volumePreview(.loading) }
#Preview("Volume, empty") { volumePreview(.empty) }
#Preview("Volume, error") { volumePreview(.failed("The server gave an unexpected answer. Try again.")) }
#Preview("Volume, offline") { volumePreview(.offline) }
#endif
