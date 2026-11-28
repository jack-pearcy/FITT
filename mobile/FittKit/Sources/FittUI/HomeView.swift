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

/// Marks the plan the user is training on.
private struct ActiveBadge: View {
    var body: some View {
        Text("Active")
            .font(.caption.weight(.semibold))
            .foregroundStyle(Theme.canvas)
            .padding(.horizontal, Theme.Space.s)
            .padding(.vertical, 3)
            .background(Theme.accent, in: Capsule())
    }
}

/// What to train next, as one card: the day, the plan it belongs to, and how far
/// through the rotation it is.
private struct NextLiftCard: View {
    let next: NextLift
    let day: WorkoutPlanDay

    private var setCount: Int { day.exercises.reduce(0) { $0 + $1.sets } }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.l) {
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                HStack {
                    Text("Next up").sectionLabel()
                    Spacer()
                    if next.deload {
                        Text("Deload")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.accent)
                            .padding(.horizontal, Theme.Space.s)
                            .padding(.vertical, 3)
                            .background(Theme.well, in: Capsule())
                    }
                }
                Text(day.dayLabel)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text(next.planName ?? "")
                    .font(.subheadline)
                    .foregroundStyle(Theme.textSecondary)
            }

            VStack(alignment: .leading, spacing: Theme.Space.s) {
                ProgressBar(fraction: next.daysInRotation > 0
                    ? Double(next.dayNumber) / Double(next.daysInRotation)
                    : 0)
                HStack {
                    Text("Day \(next.dayNumber) of \(next.daysInRotation)").ticking()
                    Spacer()
                    Text("Week \(next.weekInCycle)").ticking()
                }
                .font(.footnote.weight(.medium))
                .foregroundStyle(Theme.textSecondary)
            }

            Text("\(day.exercises.count) \(day.exercises.count == 1 ? "exercise" : "exercises") · \(setCount) \(setCount == 1 ? "set" : "sets")")
                .font(.footnote)
                .foregroundStyle(Theme.textSecondary)
        }
        .card()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Next up: \(day.dayLabel), \(next.planName ?? ""). Day \(next.dayNumber) of \(next.daysInRotation), week \(next.weekInCycle)\(next.deload ? ", deload" : "")")
        .accessibilityIdentifier("nextLift")
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
                        .entrance(.opacity.combined(with: .offset(y: 24)))
                } else if let next = model.nextLift, checkedForSession {
                    content(next)
                        .entrance(.opacity)
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await load() }
                } else {
                    SkeletonList()
                }
            }
            .canvas()
            // The workout rises into place when it starts, and Today fades back
            // when it ends.
            .motion(value: model.activeSession?.id)
            .navigationTitle(model.activeSession == nil ? "Today" : "Workout")
        }
        .task { await load() }
    }

    private func load() async {
        // A read cancelled with the tab leaves no error and no answer, so the
        // answer itself is asked for, not the lack of an error.
        checkedForSession = await model.loadActiveSession()
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
                    NextLiftCard(next: next, day: day)
                        .bareRow()
                } footer: {
                    if next.alreadyTrainedToday {
                        Text("You have already trained today. This is the next session.")
                            .accessibilityIdentifier("trainedToday")
                    }
                }

                LabeledSection("Exercises") {
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
                        HStack(spacing: Theme.Space.s) {
                            Text("Start Workout")
                            if model.isWorking {
                                ProgressView().tint(Theme.canvas)
                            }
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(model.isWorking || day.exercises.isEmpty)
                    .accessibilityIdentifier("startWorkout")
                    .bareRow()
                } footer: {
                    if let message = model.errorMessage {
                        Text(message)
                            .foregroundStyle(Theme.negative)
                            .accessibilityIdentifier("error")
                    }
                }
            }
            // The next day slides its numbers over when a workout is finished.
            .motion(value: next)
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
                                HStack(spacing: Theme.Space.m) {
                                    VStack(alignment: .leading, spacing: Theme.Space.xs) {
                                        Text(plan.name)
                                            .font(.headline)
                                            .foregroundStyle(Theme.textPrimary)
                                        Text("\(plan.splitType.label), \(plan.daysPerWeek) days a week")
                                            .font(.footnote)
                                            .foregroundStyle(Theme.textSecondary)
                                    }
                                    Spacer(minLength: 0)
                                    if plan.active {
                                        ActiveBadge()
                                            .entrance()
                                    }
                                }
                                .padding(.vertical, Theme.Space.xs)
                            }
                            .accessibilityIdentifier("plan.\(plan.id)")
                            .listRowBackground(Theme.card)
                        }
                        .themedScroll()
                        // A new plan slides in, a deleted one slides out, and the
                        // Active badge settles on the plan that now has it.
                        .motion(value: plans)
                        .refreshable { await model.loadPlans() }
                    }
                } else if let message = model.errorMessage {
                    LoadFailedView(message: message) { await model.loadPlans() }
                } else {
                    SkeletonList()
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
                        LabeledContent("Status") { ActiveBadge() }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("Status")
                            .accessibilityValue("Active")
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
            // Make Active gives way to the status row, and edited days move to
            // their new places.
            .motion(value: plan)
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
                                    .ticking()
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
                    NavigationLink("Settings") {
                        SettingsView()
                    }
                    .accessibilityIdentifier("settings")
                } footer: {
                    Text("Units, rest timer and body measures.")
                }

                Section {
                    NavigationLink("Change Password") {
                        ChangePasswordView()
                    }
                    .accessibilityIdentifier("changePassword")
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
            // Saved goals roll and fade to their new values, and the summary
            // under the progression picker fades to the chosen model's.
            .motion(value: model.goals)
            .motion(value: model.progressionModel)
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

/// Changing the password. The current one is asked for again, and the new one is
/// typed twice, since nothing on screen shows what was typed.
private struct ChangePasswordView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    private enum Field { case current, new, confirmation }

    @State private var current = ""
    @State private var new = ""
    @State private var confirmation = ""
    @State private var isDone = false
    @FocusState private var focus: Field?

    private var passwordsMatch: Bool { new == confirmation }

    private var canSubmit: Bool {
        !current.isEmpty && !new.isEmpty && passwordsMatch && !model.isWorking
    }

    var body: some View {
        ThemedForm {
            Section {
                SecureField("Current password", text: $current)
                    .textContentType(.password)
                    .focused($focus, equals: .current)
                    .submitLabel(.next)
                    .onSubmit { focus = .new }
                    .accessibilityIdentifier("currentPassword")
                FieldError(field: "currentPassword")
            }

            Section {
                SecureField("New password", text: $new)
                    .textContentType(.newPassword)
                    .focused($focus, equals: .new)
                    .submitLabel(.next)
                    .onSubmit { focus = .confirmation }
                    .accessibilityIdentifier("newPassword")
                FieldError(field: "newPassword")

                SecureField("Confirm new password", text: $confirmation)
                    .textContentType(.newPassword)
                    .focused($focus, equals: .confirmation)
                    .submitLabel(.go)
                    .onSubmit(submit)
                    .accessibilityIdentifier("confirmation")
                if !confirmation.isEmpty && !passwordsMatch {
                    Text("Passwords do not match")
                        .font(.footnote)
                        .foregroundStyle(Theme.negative)
                }
            } footer: {
                Text("At least 12 characters. Your other devices will be signed out.")
            }

            ErrorSection()

            Section {
                Button(action: submit) {
                    HStack {
                        Text("Change Password")
                        if model.isWorking {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(!canSubmit)
                .accessibilityIdentifier("submit")
            }
        }
        .canvas()
        .navigationTitle("Change Password")
        .alert("Password changed", isPresented: $isDone) {
            Button("OK") { dismiss() }
        } message: {
            Text("Your other devices have been signed out.")
        }
        .onAppear { model.clearErrors() }
        // Only while signed in. When the session has ended this view disappears
        // too, and the message for the sign-in screen has to survive that.
        .onDisappear {
            if case .signedIn = model.phase { model.clearErrors() }
        }
        .onChange(of: current) { model.clearErrors() }
        .onChange(of: new) { model.clearErrors() }
    }

    private func submit() {
        guard canSubmit else { return }
        // The keyboard goes away first, so the answer is not hidden behind it.
        focus = nil
        Task {
            if await model.changePassword(current: current, new: new) {
                isDone = true
            }
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
