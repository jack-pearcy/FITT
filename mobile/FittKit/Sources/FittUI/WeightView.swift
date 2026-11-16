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
                        List {
                            if let average = model.weightAverage {
                                Section {
                                    LabeledContent("7 day average", value: average.formatted())
                                        .accessibilityIdentifier("weightAverage")
                                }
                            }
                            Section {
                                ForEach(entries) { entry in
                                    LabeledContent(entry.loggedAt.display) {
                                        Text(entry.weight.formatted())
                                            .monospacedDigit()
                                    }
                                    .swipeActions {
                                        Button("Delete", role: .destructive) {
                                            Task { await model.deleteWeight(entry.id) }
                                        }
                                    }
                                }
                            } header: {
                                Text("Last \(AppModel.weightLogDays) days")
                            } footer: {
                                if let message = model.errorMessage {
                                    Text(message)
                                        .foregroundStyle(.red)
                                        .accessibilityIdentifier("error")
                                }
                            }
                        }
                        .refreshable { await model.loadWeightLog() }
                    }
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await model.loadWeightLog() }
                } else {
                    ProgressView()
                }
            }
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
            Form {
                Section {
                    DatePicker("Date", selection: date, in: ...Date.now, displayedComponents: .date)
                        .environment(\.calendar, CalendarDate.gregorian)
                        .accessibilityIdentifier("weightDate")
                    FieldError(field: "loggedAt")

                    LabeledContent("Weight") {
                        TextField("0.0", text: $draft.weight)
                            .numberEntry(decimal: true)
                            .focused($weightFocused)
                            .accessibilityIdentifier("weightValue")
                    }
                    FieldError(field: "weight")
                } footer: {
                    if let existing = draft.replaced(in: model.weightEntries ?? []) {
                        Text("This replaces the \(existing.weight.formatted()) already logged for this day.")
                            .accessibilityIdentifier("replacesNote")
                    }
                }

                ErrorSection()
            }
            .navigationTitle("Log Weight")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
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
