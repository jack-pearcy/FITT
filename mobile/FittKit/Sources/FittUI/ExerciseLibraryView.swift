import FittCore
import SwiftUI

/// Every exercise the user can pick, grouped by muscle group. The shared ones
/// are read-only. A custom one can be deleted, unless a plan or a logged
/// workout uses it, which the server decides.
struct ExerciseLibraryView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var creating = false

    var body: some View {
        Group {
            if let exercises = model.exercises {
                let groups = ExerciseCatalog.groups(exercises, matching: query)
                List {
                    ErrorSection()
                    ForEach(groups) { group in
                        Section(group.muscleGroup.label) {
                            ForEach(group.exercises) { exercise in
                                row(exercise)
                            }
                        }
                    }
                }
                .overlay {
                    if groups.isEmpty {
                        ContentUnavailableView.search(text: query)
                    }
                }
                .refreshable { await model.loadExercises() }
            } else if let message = model.errorMessage {
                LoadFailedView(message: message) { await model.loadExercises() }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Exercises")
        .searchable(text: $query, prompt: "Search exercises")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("New Exercise", systemImage: "plus") { creating = true }
                    // Until the library has loaded, an error on this screen means the
                    // load failed, so the form is kept away from that state.
                    .disabled(model.exercises == nil)
                    .accessibilityIdentifier("newLibraryExercise")
            }
        }
        .sheet(isPresented: $creating) {
            // The model adds the new exercise to the library, so there is nothing to do here.
            NewExerciseView { _ in }
        }
        .task {
            model.clearErrors()
            if model.exercises == nil {
                await model.loadExercises()
            }
        }
    }

    @ViewBuilder
    private func row(_ exercise: Exercise) -> some View {
        if exercise.custom {
            LabeledContent(exercise.name) {
                Text("Custom")
            }
            .swipeActions {
                Button("Delete", role: .destructive) {
                    Task { await model.deleteExercise(exercise.id) }
                }
            }
            .accessibilityIdentifier("libraryExercise.\(exercise.id)")
        } else {
            Text(exercise.name)
                .accessibilityIdentifier("libraryExercise.\(exercise.id)")
        }
    }
}
