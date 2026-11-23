import Charts
import FittCore
import SwiftUI

private extension CalendarDate {
    /// "Nov 16, 2026" in the user's language.
    var display: String {
        guard let date = date() else { return iso }
        var style = Date.FormatStyle(date: .abbreviated, time: .omitted)
        style.calendar = CalendarDate.gregorian
        return date.formatted(style)
    }
}

/// The body weight log: the seven day average and one entry per day, newest first.
struct WeightView: View {
    @Environment(AppModel.self) private var model
    @State private var isLogging = false

    var body: some View {
        NavigationStack {
            Group {
                if let entries = model.weightEntries {
                    if entries.isEmpty {
                        ContentUnavailableView {
                            Label("No weight logged", systemImage: "scalemass")
                        } description: {
                            Text("Log your weight to see it here, one entry a day.")
                        } actions: {
                            Button("Log Weight") { isLogging = true }
                                .accessibilityIdentifier("logFirstWeight")
                        }
                    } else {
                        ThemedList {
                            if let average = model.weightAverage {
                                Section {
                                    VStack(alignment: .leading, spacing: Theme.Space.m) {
                                        VStack(alignment: .leading, spacing: Theme.Space.xs) {
                                            Text("7 day average").sectionLabel()
                                            Text(model.units.bodyWeightText(kilograms: average))
                                                .heroMetric()
                                                .ticking()
                                        }
                                        if entries.count > 1 {
                                            WeightTrend(entries: entries)
                                        }
                                    }
                                    .card()
                                    .accessibilityElement(children: .combine)
                                    .accessibilityIdentifier("weightAverage")
                                    .bareRow()
                                }
                            }
                            Section {
                                ForEach(entries) { entry in
                                    LabeledContent(entry.loggedAt.display) {
                                        Text(model.units.bodyWeightText(kilograms: entry.weight))
                                            .monospacedDigit()
                                    }
                                    .swipeActions {
                                        Button("Delete", role: .destructive) {
                                            Task { await model.deleteWeight(entry.id) }
                                        }
                                    }
                                }
                            } header: {
                                Text("Last \(AppModel.weightLogDays) days").sectionLabel()
                            } footer: {
                                if let message = model.errorMessage {
                                    Text(message)
                                        .foregroundStyle(Theme.negative)
                                        .accessibilityIdentifier("error")
                                }
                            }
                        }
                        // A logged day slides into the list, a deleted one slides out,
                        // and the average rolls to its new value.
                        .motion(value: entries)
                        .motion(value: model.weightAverage)
                        .refreshable { await model.loadWeightLog() }
                    }
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await model.loadWeightLog() }
                } else {
                    SkeletonList()
                }
            }
            .canvas()
            .navigationTitle("Weight")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Log Weight", systemImage: "plus") { isLogging = true }
                        .accessibilityIdentifier("logWeight")
                }
            }
            .sheet(isPresented: $isLogging) {
                LogWeightView()
            }
        }
        .task { await model.loadWeightLog() }
    }
}

/// The logged weights as one line, oldest on the left. It has no axes: it shows
/// the direction, and the list below has the numbers. The line draws in from
/// the left the first time it shows, and fades in under Reduce Motion.
private struct WeightTrend: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let entries: [BodyWeightEntry]
    @State private var drawn = false

    private struct Point: Identifiable {
        let id: Int64
        let date: Date
        let kilograms: Double
    }

    private var points: [Point] {
        entries
            .compactMap { entry in
                entry.loggedAt.date().map {
                    Point(id: entry.id, date: $0, kilograms: NSDecimalNumber(decimal: entry.weight).doubleValue)
                }
            }
            .sorted { $0.date < $1.date }
    }

    var body: some View {
        let points = points
        let low = points.map(\.kilograms).min() ?? 0
        let high = points.map(\.kilograms).max() ?? 1
        // Room above and below, so a flat week is a line across the middle
        // and not one pressed against an edge.
        let margin = max((high - low) * 0.15, 0.25)

        Chart(points) { point in
            LineMark(x: .value("Day", point.date), y: .value("Weight", point.kilograms))
                .interpolationMethod(.monotone)
                .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                .foregroundStyle(Theme.accent)
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartYScale(domain: (low - margin)...(high + margin))
        .frame(height: 64)
        .mask(alignment: .leading) {
            Rectangle().scaleEffect(x: drawn || reduceMotion ? 1 : 0, anchor: .leading)
        }
        .opacity(drawn ? 1 : 0)
        .onAppear {
            withAnimation(reduceMotion ? Theme.calm : .easeOut(duration: 0.7)) {
                drawn = true
            }
        }
        .accessibilityHidden(true)
    }
}

/// The form for one day's weight. Saving a day that already has an entry replaces it.
private struct LogWeightView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft = BodyWeightDraft(date: CalendarDate(Date()))
    @FocusState private var weightFocused: Bool

    /// The date picker works in `Date`; the draft keeps a plain calendar day.
    private var date: Binding<Date> {
        Binding(
            get: { draft.date.date() ?? .now },
            set: { draft.date = CalendarDate($0) }
        )
    }

    var body: some View {
        NavigationStack {
            ThemedForm {
                Section {
                    DatePicker("Date", selection: date, in: ...Date.now, displayedComponents: .date)
                        .environment(\.calendar, CalendarDate.gregorian)
                        .accessibilityIdentifier("weightDate")
                    FieldError(field: "loggedAt")

                    // The unit is in the label, so it stays visible once a number is typed.
                    LabeledContent("Weight (\(model.units.weightLabel))") {
                        TextField("0.0", text: $draft.weight)
                            .numberEntry(decimal: true)
                            .focused($weightFocused)
                            .accessibilityIdentifier("weightValue")
                    }
                    FieldError(field: "weight")
                } footer: {
                    if let existing = draft.replaced(in: model.weightEntries ?? []) {
                        Text("This replaces the \(model.units.bodyWeightText(kilograms: existing.weight)) already logged for this day.")
                            .accessibilityIdentifier("replacesNote")
                    }
                }

                ErrorSection()
            }
            .canvas()
            .navigationTitle("Log Weight")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .secondaryAction()
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task {
                            if await model.logWeight(draft) {
                                dismiss()
                            }
                        }
                    }
                    .disabled(model.isWorking)
                    .accessibilityIdentifier("saveWeight")
                }
            }
            // A message about the last attempt is stale once the user edits the form.
            .onChange(of: draft) { model.clearErrors() }
        }
        .onAppear {
            model.clearErrors()
            weightFocused = true
        }
        .interactiveDismissDisabled(model.isWorking)
    }
}
