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
            List {
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

                Section(day.dayLabel) {
                    if day.exercises.isEmpty {
                        Text("This day has no exercises yet.")
                            .foregroundStyle(.secondary)
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
                            .foregroundStyle(.red)
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
                        }
                        .refreshable { await model.loadPlans() }
                    }
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await model.loadPlans() }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Plans")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("New Plan", systemImage: "plus") { isCreating = true }
                        .accessibilityIdentifier("newPlan")
                }
            }
            .sheet(isPresented: $isCreating) {
                NewPlanView()
            }
            // The detail screen looks the plan up by id each time it draws, so it
            // shows the reloaded plan after an activation instead of a stale copy.
            .navigationDestination(for: Int64.self) { planId in
                PlanDetailView(planId: planId)
            }
        }
        .task { await model.loadPlans() }
    }
}

private struct PlanDetailView: View {
    @Environment(AppModel.self) private var model
    let planId: Int64

    var body: some View {
        if let plan = model.plans?.first(where: { $0.id == planId }) {
            List {
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
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("error")
                    }
                }

                ForEach(plan.orderedDays) { day in
                    Section(day.dayLabel) {
                        if day.exercises.isEmpty {
                            Text("No exercises yet.")
                                .foregroundStyle(.secondary)
                        }
                        ForEach(day.orderedExercises) { item in
                            ExerciseRow(item: item)
                        }
                    }
                }
            }
            .navigationTitle(plan.name)
        } else {
            ContentUnavailableView("Plan not found", systemImage: "questionmark.folder")
        }
    }
}

private struct AccountView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        NavigationStack {
            List {
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
                            .foregroundStyle(.red)
                        Button("Try Again") {
                            Task { await model.loadGoals() }
                        }
                    } else {
                        ProgressView()
                    }
                } header: {
                    Text("Goals")
                }

                Section {
                    Button("Sign Out", role: .destructive) {
                        Task { await model.signOut() }
                    }
                    .accessibilityIdentifier("signOut")
                }
            }
            .navigationTitle("Account")
        }
        .task { await model.loadGoals() }
    }

    /// The target in the units the user works in, with the unit named.
    private func targetText(_ kilograms: Decimal) -> String {
        switch model.units {
        case .metric: "\(kilograms.formatted()) kg"
        case .imperial: "\(UnitConversion.pounds(kilograms: kilograms).formatted()) lb"
        }
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
        .navigationTitle("Change Goals")
        .onAppear {
            draft = GoalsDraft(goals: goals, units: model.units)
            model.clearErrors()
        }
    }
}
