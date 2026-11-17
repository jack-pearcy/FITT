import FittCore
import SwiftUI

/// The form for a custom exercise, shown as a sheet. `created` gets the
/// exercise as the server stored it, after the sheet has been told to close.
struct NewExerciseView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var draft = ExerciseDraft()
    @FocusState private var nameFocused: Bool
    let created: (Exercise) -> Void

    var body: some View {
        NavigationStack {
            ThemedForm {
                Section {
                    TextField("Name", text: $draft.name)
                        .focused($nameFocused)
                        .submitLabel(.done)
                        .accessibilityIdentifier("exerciseName")
                    FieldError(field: "name")

                    Picker("Muscle group", selection: $draft.muscleGroup) {
                        ForEach(MuscleGroup.allCases, id: \.self) { muscleGroup in
                            Text(muscleGroup.label).tag(muscleGroup)
                        }
                    }
                    .accessibilityIdentifier("muscleGroup")
                    FieldError(field: "muscleGroup")
                } footer: {
                    Text("Only you can see an exercise you create.")
                }

                ErrorSection()
            }
            .canvas()
            .navigationTitle("New Exercise")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(model.isWorking)
                        .accessibilityIdentifier("saveExercise")
                }
            }
            // A message about the last attempt is stale once the user edits the form.
            .onChange(of: draft) { model.clearErrors() }
        }
        .onAppear {
            model.clearErrors()
            nameFocused = true
        }
        .interactiveDismissDisabled(model.isWorking)
    }

    private func save() {
        nameFocused = false
        Task {
            if let exercise = await model.createExercise(draft) {
                dismiss()
                created(exercise)
            }
        }
    }
}
