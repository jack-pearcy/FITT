import Charts
import FittCore
import SwiftUI

private extension Date {
    /// "Nov 16, 2026" in the user's language.
    var day: String { formatted(date: .abbreviated, time: .omitted) }
}

/// Records and progress for one exercise, from the finished workouts.
struct ExerciseStatsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let exercise: Exercise

    var body: some View {
        ExerciseStatsContent(
            state: ExerciseStatsContent.screenState(
                exercise: exercise,
                history: model.history,
                errorMessage: model.errorMessage,
                isOffline: model.isOffline
            ),
            units: model.units,
            retry: { await model.loadSessions() },
            back: { dismiss() }
        )
        .navigationTitle(exercise.name)
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

struct ExerciseStatsContent: View {
    let state: ScreenState<ExerciseStats>
    let units: UnitsPreference
    let retry: () async -> Void
    let back: () -> Void

    /// Content once a finished session holds a set of this exercise, even a
    /// bodyweight set with no estimate. Empty when it was never trained.
    nonisolated static func screenState(
        exercise: Exercise,
        history: [WorkoutSession]?,
        errorMessage: String?,
        isOffline: Bool
    ) -> ScreenState<ExerciseStats> {
        ScreenState(history: history, errorMessage: errorMessage, isOffline: isOffline) { history in
            let stats = ExerciseStats(exercise: exercise, sessions: history)
            return stats.sessionCount > 0 ? stats : nil
        }
    }

    var body: some View {
        StatesView(
            state: state,
            empty: EmptyState(
                title: "No sets yet",
                systemImage: "chart.line.uptrend.xyaxis",
                message: "Finish a workout with this exercise to see its records.",
                actionTitle: "Go Back",
                action: back
            ),
            retry: retry
        ) { stats in
            ThemedList {
                Section {
                    VStack(alignment: .leading, spacing: Theme.Space.m) {
                        VStack(alignment: .leading, spacing: Theme.Space.xs) {
                            Text("Estimated one-rep max").sectionLabel()
                            if let best = stats.bestEstimate {
                                Text(units.weightText(kilograms: best.value)).heroMetric().ticking()
                                Text("From \(best.from.set.summary(units: units)) on \(best.from.date.day)")
                                    .font(.footnote)
                                    .foregroundStyle(Theme.textSecondary)
                            } else {
                                Text("Log a set with weight and 1 to 12 reps to see an estimate.")
                                    .foregroundStyle(Theme.textSecondary)
                            }
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityIdentifier("bestEstimate")
                        OneRepMaxChart(series: stats.series, units: units)
                    }
                    .card()
                    .bareRow()
                } footer: {
                    Text("An estimate worked out from your sets, not a tested max.")
                }

                Section {
                    if let best = stats.bestSet {
                        Stat(title: "Best set", value: best.set.summary(units: units), detail: best.date.day)
                    }
                    Stat(title: "Workouts", value: "\(stats.sessionCount)", detail: nil)
                }
            }
            .refreshable { await retry() }
            // A refresh that brings a new best rolls the number to it.
            .motion(value: stats)
        }
        .canvas()
    }
}

/// A label over its value, so neither is squeezed at large text sizes.
private struct Stat: View {
    let title: String
    let value: String
    let detail: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Text(title).sectionLabel()
            Text(value).font(.headline).metric()
            if let detail {
                Text(detail).font(.footnote).foregroundStyle(Theme.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// The estimated one-rep max of each training day, oldest on the left, with
/// axes in the user's unit. VoiceOver reads a summary in place of the marks.
private struct OneRepMaxChart: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric private var height: CGFloat = 180
    /// Room above the plot for the half of the top axis label that sits
    /// over the top gridline.
    @ScaledMetric(relativeTo: .footnote) private var labelRoom: CGFloat = 10
    @State private var drawn = false
    let series: [ExerciseStats.Point]
    let units: UnitsPreference

    private struct Plot: Identifiable {
        let date: Date
        let kilograms: Decimal
        let shown: Double
        var id: Date { date }
    }

    private var plots: [Plot] {
        series.compactMap { point in
            guard let kilograms = point.estimatedOneRepMax, let date = point.day.date() else { return nil }
            return Plot(date: date, kilograms: kilograms, shown: NSDecimalNumber(decimal: units.shown(kilograms: kilograms)).doubleValue)
        }
    }

    /// First, latest and best, since the line itself is hard to hear.
    private func summary(_ plots: [Plot]) -> String {
        guard let first = plots.first, let latest = plots.last,
              let best = plots.max(by: { ($0.kilograms, $1.date) < ($1.kilograms, $0.date) })
        else { return "" }
        let text = { (plot: Plot) in "\(units.weightText(kilograms: plot.kilograms)) on \(plot.date.day)" }
        return "First \(text(first)), latest \(text(latest)), best \(text(best))"
    }

    var body: some View {
        let plots = plots
        if plots.count > 1 {
            Chart(plots) { plot in
                LineMark(x: .value("Day", plot.date, unit: .day), y: .value("Estimated one-rep max", plot.shown))
                    .interpolationMethod(.monotone)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                PointMark(x: .value("Day", plot.date, unit: .day), y: .value("Estimated one-rep max", plot.shown))
                    .symbolSize(30)
            }
            .foregroundStyle(Theme.accent)
            .chartYScale(domain: .automatic(includesZero: false))
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                    AxisGridLine().foregroundStyle(Theme.well)
                    AxisValueLabel {
                        if let shown = value.as(Double.self) {
                            Text("\(shown.formatted(.number.precision(.fractionLength(0)))) \(units.weightLabel)")
                                .foregroundStyle(Theme.textSecondary)
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 3)) {
                    AxisValueLabel(format: .dateTime.month(.abbreviated).day())
                        .foregroundStyle(Theme.textSecondary)
                }
            }
            // Axis text stops growing at the largest standard size, so the
            // labels never crowd out the line. VoiceOver reads the summary.
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            .frame(height: min(height, 260))
            .padding(.top, min(labelRoom, 16))
            // The last date label is centred on the last day, so half of it
            // sits past the plot. Leave room for it on the right.
            .padding(.trailing, min(labelRoom * 2, 28))
            .mask(alignment: .leading) {
                Rectangle().scaleEffect(x: drawn || reduceMotion ? 1 : 0, anchor: .leading)
            }
            .opacity(drawn ? 1 : 0)
            .onAppear {
                withAnimation(Theme.animation(.easeOut(duration: 0.7), reduceMotion: reduceMotion)) {
                    drawn = true
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Estimated one-rep max over time")
            .accessibilityValue(summary(plots))
            .accessibilityIdentifier("oneRepMaxChart")
        }
    }
}

/// The best estimated one-rep max of the exercises trained most. It shows
/// nothing until a logged set gives an estimate.
struct RecordsSection: View {
    let records: [ExerciseStats]
    let units: UnitsPreference

    var body: some View {
        if !records.isEmpty {
            Section {
                ForEach(records, id: \.exercise.id) { stats in
                    if let best = stats.bestEstimate {
                        NavigationLink {
                            ExerciseStatsView(exercise: stats.exercise)
                        } label: {
                            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                                Text(stats.exercise.name)
                                    .font(.headline)
                                    .foregroundStyle(Theme.textPrimary)
                                Text("\(units.weightText(kilograms: best.value)) · \(best.from.date.day)")
                                    .font(.footnote)
                                    .metric()
                                    .foregroundStyle(Theme.textSecondary)
                            }
                            .frame(minHeight: 44, alignment: .leading)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(stats.exercise.name)
                            .accessibilityValue("Estimated one-rep max \(units.weightText(kilograms: best.value)), \(best.from.date.day)")
                        }
                        .accessibilityIdentifier("record.\(stats.exercise.id)")
                        .listRowBackground(Theme.card)
                    }
                }
            } header: {
                Text("Records: estimated one-rep max").sectionLabel()
            } footer: {
                Text("Estimates worked out from your sets, not tested maxes.")
            }
        }
    }
}

#if DEBUG
/// Local data for previews. Nothing here reaches the network.
enum StatsFixtures {
    static let bench = Exercise(id: 1, name: "Barbell Bench Press", muscleGroup: .chest, custom: false)
    static let pullUp = Exercise(id: 2, name: "Pull Up", muscleGroup: .back, custom: false)

    /// Six weekly sessions: bench press rising two kilograms a week, and pull ups.
    static let sessions: [WorkoutSession] = (0..<6).map { week in
        let start = Date.now.addingTimeInterval(Double(week - 5) * 7 * 86_400)
        let id = Int64(week + 1)
        return WorkoutSession(id: id, planId: nil, startedAt: start, finishedAt: start.addingTimeInterval(3600), exerciseEntries: [
            ExerciseEntry(id: id * 10, orderIndex: 0, exercise: bench, sets: [
                SetEntry(id: id * 100, setNumber: 1, reps: 8 - week % 2, weight: Decimal(80 + 2 * week), rpe: nil, completed: true),
                SetEntry(id: id * 100 + 1, setNumber: 2, reps: 6, weight: Decimal(80 + 2 * week), rpe: nil, completed: true),
            ]),
            ExerciseEntry(id: id * 10 + 1, orderIndex: 1, exercise: pullUp, sets: [
                SetEntry(id: id * 100 + 2, setNumber: 1, reps: 10, weight: nil, rpe: nil, completed: true),
            ]),
        ])
    }
}

@MainActor
private func statsPreview(_ state: ScreenState<ExerciseStats>) -> some View {
    NavigationStack {
        ExerciseStatsContent(state: state, units: .metric, retry: {}, back: {})
            .navigationTitle(StatsFixtures.bench.name)
    }
    .fittTheme()
}

#Preview("Exercise, content") {
    statsPreview(.content(ExerciseStats(exercise: StatsFixtures.bench, sessions: StatsFixtures.sessions)))
}

#Preview("Exercise, bodyweight only") {
    statsPreview(.content(ExerciseStats(exercise: StatsFixtures.pullUp, sessions: StatsFixtures.sessions)))
}

#Preview("Exercise, loading") { statsPreview(.loading) }
#Preview("Exercise, empty") { statsPreview(.empty) }
#Preview("Exercise, error") { statsPreview(.failed("The server gave an unexpected answer. Try again.")) }
#Preview("Exercise, offline") { statsPreview(.offline) }

#Preview("Records") {
    NavigationStack {
        ThemedList {
            RecordsSection(records: ExerciseStats.records(StatsFixtures.sessions, limit: 3), units: .imperial)
        }
    }
    .fittTheme()
}
#endif
