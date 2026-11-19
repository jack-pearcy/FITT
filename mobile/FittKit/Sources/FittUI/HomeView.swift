import FittCore
import SwiftUI

/// The screens for a user who has finished onboarding.
struct HomeView: View {
    var body: some View {
        TabView {
            TodayView()
                .tabItem { Label("Today", systemImage: "figure.strengthtraining.traditional") }
            PlansView()
                .tabItem { Label("Plans", systemImage: "list.bullet.clipboard") }
            WeightView()
                .tabItem { Label("Weight", systemImage: "scalemass") }
            HistoryView()
                .tabItem { Label("History", systemImage: "clock.arrow.circlepath") }
            AccountView()
                .tabItem { Label("Account", systemImage: "person.crop.circle") }
        }
    }
}

/// One prescribed exercise: its name, muscle group, and sets by reps.
private struct ExerciseRow: View {
    let item: PlanDayExercise

    var body: some View {
        LabeledContent {
            Text("\(item.sets) × \(item.reps)")
                .monospacedDigit()
        } label: {
            Text(item.exercise.name)
            Text(item.exercise.muscleGroup.label)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.exercise.name), \(item.sets) sets of \(item.reps)")
    }
}

/// A full-screen message with a retry, for a screen that could not load at all.
struct LoadFailedView: View {
    let message: String
    let retry: () async -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Could not load", systemImage: "wifi.exclamationmark")
        } description: {
            Text(message)
        } actions: {
            Button("Try Again") {
                Task { await retry() }
            }
            .accessibilityIdentifier("retry")
        }
    }
}

/// The workout in progress when there is one, otherwise what to train next.
struct TodayView: View {
    @Environment(AppModel.self) private var model
    /// False until the first check for an unfinished session has come back, so
    /// the Start button is not offered over a workout that is already running.
    @State private var checkedForSession = false

    var body: some View {
        NavigationStack {
            Group {
                if model.activeSession != nil {
                    WorkoutView()
                } else if let next = model.nextLift, checkedForSession {
                    content(next)
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await load() }
                } else {
                    ProgressView()
                }
            }
            .canvas()
            .navigationTitle(model.activeSession == nil ? "Today" : "Workout")
        }
        .task { await load() }
    }

    private func load() async {
        await model.loadActiveSession()
        checkedForSession = model.errorMessage == nil
        await model.loadNextLift()
    }

    @ViewBuilder
    private func content(_ next: NextLift) -> some View {
        if !next.hasActivePlan {
            ContentUnavailableView(
                "No active plan",
                systemImage: "list.bullet.clipboard",
                description: Text("Choose a plan in the Plans tab to see what to train.")
            )
        } else if let day = next.day {
            ThemedList {
                Section {
                    LabeledContent("Plan", value: next.planName ?? "")
                    LabeledContent("Day", value: "\(next.dayNumber) of \(next.daysInRotation)")
                    LabeledContent("Week", value: next.deload ? "\(next.weekInCycle), deload" : "\(next.weekInCycle)")
                } footer: {
                    if next.alreadyTrainedToday {
                        Text("You have already trained today. This is the next session.")
                            .accessibilityIdentifier("trainedToday")
                    }
                }

                LabeledSection(day.dayLabel) {
                    if day.exercises.isEmpty {
                        Text("This day has no exercises yet.")
                            .foregroundStyle(Theme.textSecondary)
                    }
                    ForEach(day.orderedExercises) { item in
                        ExerciseRow(item: item)
                    }
                }

                Section {
                    Button {
                        Task { await model.startWorkout() }
                    } label: {
                        HStack {
                            Text("Start Workout")
                            if model.isWorking {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(model.isWorking || day.exercises.isEmpty)
                    .accessibilityIdentifier("startWorkout")
                } footer: {
                    if let message = model.errorMessage {
                        Text(message)
                            .foregroundStyle(Theme.negative)
                            .accessibilityIdentifier("error")
                    }
                }
            }
            .refreshable { await load() }
        } else {
            ContentUnavailableView(
                "Nothing to train",
                systemImage: "calendar.badge.exclamationmark",
                description: Text("\(next.planName ?? "The active plan") has no days yet.")
            )
        }
    }
}

/// The user's plans. The active one is marked; any plan opens to its days.
struct PlansView: View {
    @Environment(AppModel.self) private var model
    @State private var isCreating = false

    var body: some View {
        NavigationStack {
            Group {
                if let plans = model.plans {
                    if plans.isEmpty {
                        ContentUnavailableView {
                            Label("No plans yet", systemImage: "list.bullet.clipboard")
                        } description: {
                            Text("A plan is the list of training days you rotate through.")
                        } actions: {
                            Button("Create a Plan") { isCreating = true }
                                .accessibilityIdentifier("createFirstPlan")
                        }
                    } else {
                        List(plans) { plan in
                            NavigationLink(value: plan.id) {
                                LabeledContent {
                                    if plan.active {
                                        Text("Active")
                                            .foregroundStyle(.tint)
                                    }
                                } label: {
                                    Text(plan.name)
                                    Text("\(plan.splitType.label), \(plan.daysPerWeek) days a week")
                                }
                            }
                            .accessibilityIdentifier("plan.\(plan.id)")
                            .listRowBackground(Theme.card)
                        }
                        .themedScroll()
                        .refreshable { await model.loadPlans() }
                    }
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await model.loadPlans() }
                } else {
                    ProgressView()
                }
            }
            .canvas()
            .navigationTitle("Plans")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("New Plan", systemImage: "plus") { isCreating = true }
                        .accessibilityIdentifier("newPlan")
                }
                ToolbarItem(placement: Self.libraryPlacement) {
                    NavigationLink {
                        ExerciseLibraryView()
                    } label: {
                        Label("Exercises", systemImage: "dumbbell")
                    }
                    .accessibilityIdentifier("exerciseLibrary")
                }
            }
            .sheet(isPresented: $isCreating) {
                PlanFormView()
            }
            // The detail screen looks the plan up by id each time it draws, so it
            // shows the reloaded plan after an activation instead of a stale copy.
            .navigationDestination(for: Int64.self) { planId in
                PlanDetailView(planId: planId)
            }
        }
        .task { await model.loadPlans() }
    }

    private static var libraryPlacement: ToolbarItemPlacement {
        #if os(iOS)
        .topBarLeading
        #else
        .automatic
        #endif
    }
}

private struct PlanDetailView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var isEditing = false
    @State private var isConfirmingDelete = false
    let planId: Int64

    var body: some View {
        if let plan = model.plans?.first(where: { $0.id == planId }) {
            ThemedList {
                Section {
                    LabeledContent("Split", value: plan.splitType.label)
                    LabeledContent("Days a week", value: "\(plan.daysPerWeek)")
                    if plan.active {
                        LabeledContent("Status", value: "Active")
                    } else {
                        Button {
                            Task { await model.activatePlan(plan.id) }
                        } label: {
                            HStack {
                                Text("Make Active")
                                if model.isWorking {
                                    Spacer()
                                    ProgressView()
                                }
                            }
                        }
                        .disabled(model.isWorking)
                        .accessibilityIdentifier("activate")
                    }
                } footer: {
                    if let message = model.errorMessage {
                        Text(message)
                            .foregroundStyle(Theme.negative)
                            .accessibilityIdentifier("error")
                    }
                }

                ForEach(plan.orderedDays) { day in
                    LabeledSection(day.dayLabel) {
                        if day.exercises.isEmpty {
                            Text("No exercises yet.")
                                .foregroundStyle(Theme.textSecondary)
                        }
                        ForEach(day.orderedExercises) { item in
                            ExerciseRow(item: item)
                        }
                    }
                }

                Section {
                    Button("Delete Plan", role: .destructive) { isConfirmingDelete = true }
                        .disabled(model.isWorking)
                        .accessibilityIdentifier("deletePlan")
                }
            }
            .canvas()
            .navigationTitle(plan.name)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Edit") { isEditing = true }
                        .disabled(model.isWorking)
                        .accessibilityIdentifier("editPlan")
                }
            }
            .sheet(isPresented: $isEditing) {
                PlanFormView(editing: plan)
            }
            .confirmationDialog("Delete \(plan.name)?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("Delete Plan", role: .destructive) {
                    Task {
                        if await model.deletePlan(plan.id) {
                            dismiss()
                        }
                    }
                }
            } message: {
                Text(plan.active
                    ? "This is your active plan, so Today will have nothing to train until you make another plan active. Workouts you logged with it are kept."
                    : "Workouts you logged with it are kept.")
            }
        } else {
            ContentUnavailableView("Plan not found", systemImage: "questionmark.folder")
        }
    }
}

private struct AccountView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            ThemedList {
                Section {
                    if let goals = model.goals {
                        LabeledContent("Goal", value: goals.goalDirection.label)
                        if let target = goals.targetWeightKg {
                            LabeledContent("Target weight", value: targetText(target))
                        }
                        if let weeks = goals.timelineWeeks {
                            LabeledContent("Timeline", value: "\(weeks) weeks")
                        }
                        if let calories = goals.calorieGoal {
                            LabeledContent("Daily calories") {
                                Text("\(calories)")
                                    .monospacedDigit()
                                    .accessibilityIdentifier("calorieGoal")
                            }
                            LabeledContent("Calorie goal", value: goals.calorieGoalMode == .auto ? "Automatic" : "Manual")
                        }
                        NavigationLink("Change Goals") {
                            EditGoalsView(goals: goals)
                        }
                        .accessibilityIdentifier("changeGoals")
                    } else if let message = model.errorMessage {
                        Text(message)
                            .foregroundStyle(Theme.negative)
                        Button("Try Again") {
                            Task { await model.loadGoals() }
                        }
                    } else {
                        ProgressView()
                    }
                } header: {
                    Text("Goals").sectionLabel()
                }

                if let progressionModel = model.progressionModel {
                    Section {
                        Picker("Model", selection: Binding(
                            get: { progressionModel },
                            set: { chosen in Task { await model.setProgressionModel(chosen) } }
                        )) {
                            ForEach(ProgressionModel.allCases, id: \.self) { option in
                                Text(option.label).tag(option)
                            }
                        }
                        .disabled(model.isWorking)
                        .accessibilityIdentifier("progressionModel")
                        .quietValue()
                    } header: {
                        Text("Progression").sectionLabel()
                    } footer: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(progressionModel.summary)
                            if model.goals != nil, let message = model.errorMessage {
                                Text(message)
                                    .foregroundStyle(Theme.negative)
                                    .accessibilityIdentifier("error")
                            }
                        }
                    }
                }

                Section {
                    Button("Sign Out", role: .destructive) {
                        Task { await model.signOut() }
                    }
                    .accessibilityIdentifier("signOut")
                }

                Section {
                    NavigationLink("Delete Account") {
                        DeleteAccountView()
                    }
                    .foregroundStyle(Theme.negative)
                    .accessibilityIdentifier("deleteAccount")
                }
            }
            .canvas()
            .navigationTitle("Account")
        }
        .task {
            await model.loadGoals()
            // A later load clears the message of an earlier one, so the model is
            // only read once the goals are in and their error has nowhere to hide.
            if model.goals != nil {
                await model.loadProgressionModel()
            }
        }
    }

    /// The target in the units the user works in, with the unit named.
    private func targetText(_ kilograms: Decimal) -> String {
        switch model.units {
        case .metric: "\(kilograms.formatted()) kg"
        case .imperial: "\(UnitConversion.pounds(kilograms: kilograms).formatted()) lb"
        }
    }
}

/// Deleting the account. The password is asked for again, and a second
/// confirmation stands between the button and the request, because nothing
/// brings the data back.
private struct DeleteAccountView: View {
    @Environment(AppModel.self) private var model
    @State private var password = ""
    @State private var isConfirming = false
    @FocusState private var passwordFocused: Bool

    var body: some View {
        ThemedForm {
            Section {
                Text("This deletes your account and everything stored for it: workouts, plans, custom exercises, body weight entries, goals and measurements.")
                Text("It happens at once and cannot be undone.")
                    .foregroundStyle(Theme.negative)
            }

            Section {
                SecureField("Password", text: $password)
                    .textContentType(.password)
                    .focused($passwordFocused)
                    .submitLabel(.done)
                    .onSubmit { passwordFocused = false }
                    .accessibilityIdentifier("password")
                FieldError(field: "password")
            } footer: {
                Text("Enter your password to confirm it is you.")
            }

            ErrorSection()

            Section {
                Button(role: .destructive) {
                    // The keyboard goes away first, so the answer to this attempt
                    // is not hidden behind it.
                    passwordFocused = false
                    isConfirming = true
                } label: {
                    HStack {
                        Text("Delete Account")
                        if model.isWorking {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(password.isEmpty || model.isWorking)
                .accessibilityIdentifier("submit")
                // On the button, not on the form: the system shows this as a
                // popover pointing at the view it is attached to, and attached to
                // the form it lands on top of the title.
                .confirmationDialog("Delete your account?", isPresented: $isConfirming, titleVisibility: .visible) {
                    Button("Delete Account", role: .destructive) {
                        // On success the model signs out and the whole tab view goes
                        // away, so there is nothing to dismiss here.
                        Task { _ = await model.deleteAccount(password: password) }
                    }
                    .accessibilityIdentifier("confirmDelete")
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("This cannot be undone.")
                }
            }
        }
        .canvas()
        .navigationTitle("Delete Account")
        .onAppear { model.clearErrors() }
        // A message about a failed attempt must not follow the user back to the Account tab.
        // Only while signed in. When the session has ended this view disappears
        // too, and the message for the sign-in screen has to survive that.
        .onDisappear {
            if case .signedIn = model.phase { model.clearErrors() }
        }
        .onChange(of: password) { model.clearErrors() }
    }
}

/// Changing goals after onboarding. The form is the one onboarding uses, filled in from the saved goals.
private struct EditGoalsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft: GoalsDraft

    init(goals: Goals) {
        // The units are not known here yet; the draft is rebuilt with them when the view appears.
        _draft = State(initialValue: GoalsDraft(goals: goals, units: .metric))
        self.goals = goals
    }

    private let goals: Goals

    var body: some View {
        GoalsForm(draft: $draft, submitTitle: "Save") {
            Task {
                if await model.updateGoals(draft) {
                    dismiss()
                }
            }
        }
        .canvas()
        .navigationTitle("Change Goals")
        .onAppear {
            draft = GoalsDraft(goals: goals, units: model.units)
            model.clearErrors()
        }
    }
}
