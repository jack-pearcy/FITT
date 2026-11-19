import SwiftUI

private extension View {
    /// The email keyboard with no capitalization or autocorrect. The keyboard
    /// modifiers exist only on iOS, and this module also builds on macOS for tests.
    func emailEntry() -> some View {
        #if os(iOS)
        self.keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
        #else
        self.autocorrectionDisabled()
        #endif
    }
}

/// Sign in and create account share one screen. The mode decides which call is made.
struct SignInView: View {
    @Environment(AppModel.self) private var model

    private enum Mode { case signIn, createAccount }
    private enum Field { case email, password, confirmation }

    @State private var mode = Mode.signIn
    @State private var email = ""
    @State private var password = ""
    @State private var confirmation = ""
    @FocusState private var focus: Field?

    private var passwordsMatch: Bool {
        mode == .signIn || password == confirmation
    }

    private var canSubmit: Bool {
        !email.isEmpty && !password.isEmpty && passwordsMatch && !model.isWorking
    }

    var body: some View {
        NavigationStack {
            ThemedForm {
                Section {
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .emailEntry()
                        .focused($focus, equals: .email)
                        .submitLabel(.next)
                        .onSubmit { focus = .password }
                        .accessibilityIdentifier("email")
                    fieldError("email")

                    SecureField("Password", text: $password)
                        .textContentType(mode == .signIn ? .password : .newPassword)
                        .focused($focus, equals: .password)
                        .submitLabel(mode == .signIn ? .go : .next)
                        .onSubmit { mode == .signIn ? submit() : (focus = .confirmation) }
                        .accessibilityIdentifier("password")
                    fieldError("password")

                    if mode == .createAccount {
                        SecureField("Confirm password", text: $confirmation)
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
                    }
                } footer: {
                    if mode == .createAccount {
                        Text("At least 12 characters.")
                    }
                }

                if let message = model.errorMessage {
                    Section {
                        Text(message)
                            .foregroundStyle(Theme.negative)
                            .accessibilityIdentifier("error")
                    }
                }

                if let notice = model.notice {
                    Section {
                        Text(notice)
                            .accessibilityIdentifier("notice")
                    }
                }

                Section {
                    Button(action: submit) {
                        HStack {
                            Text(mode == .signIn ? "Sign In" : "Create Account")
                            if model.isWorking {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(!canSubmit)
                    .accessibilityIdentifier("submit")

                    Button(mode == .signIn ? "Create an account" : "I already have an account") {
                        mode = mode == .signIn ? .createAccount : .signIn
                        confirmation = ""
                        model.clearErrors()
                    }
                    .accessibilityIdentifier("switchMode")
                    .secondaryAction()
                }
            }
            .canvas()
            .navigationTitle("FITT")
            // A message about the last attempt is stale once the user edits a field.
            .onChange(of: email) { model.clearErrors() }
            .onChange(of: password) { model.clearErrors() }
        }
    }

    @ViewBuilder
    private func fieldError(_ field: String) -> some View {
        if let message = model.fieldErrors[field] {
            Text(message)
                .font(.footnote)
                .foregroundStyle(Theme.negative)
        }
    }

    private func submit() {
        guard canSubmit else { return }
        focus = nil
        Task {
            switch mode {
            case .signIn: await model.signIn(email: email, password: password)
            case .createAccount: await model.register(email: email, password: password)
            }
        }
    }
}
