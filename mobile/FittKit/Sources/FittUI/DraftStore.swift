import Foundation

/// Keeps unsaved forms on this device, so a form survives its sheet being swiped
/// away by accident, or iOS closing the app while it is in the background.
///
/// One file per user and form. A draft is kept for `lifetime` and dropped the
/// next time it is read after that. The folder is left out of iCloud and device
/// backups, and on iOS each file can only be read while the phone is unlocked.
/// Signing out removes every draft.
///
/// A draft is a convenience: if a write fails, the form still works and only
/// the copy on the device is missing, so errors are not reported.
public struct DraftStore: Sendable {
    /// Seven days: long enough to finish a plan later in the week, short enough
    /// that a forgotten draft does not linger on the phone.
    public static let defaultLifetime: TimeInterval = 7 * 24 * 60 * 60

    public static var defaultDirectory: URL {
        URL.applicationSupportDirectory.appending(path: "Drafts", directoryHint: .isDirectory)
    }

    private let directory: URL
    private let lifetime: TimeInterval
    private let now: @Sendable () -> Date

    public init(
        directory: URL = DraftStore.defaultDirectory,
        lifetime: TimeInterval = DraftStore.defaultLifetime,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.directory = directory
        self.lifetime = lifetime
        self.now = now
    }

    private struct Stored<Value: Codable>: Codable {
        let savedAt: Date
        let value: Value
    }

    /// The draft kept under `key`, or nil when there is none, it has expired, or
    /// it cannot be read. An expired or unreadable file is removed.
    public func load<Value: Codable>(_ type: Value.Type, key: String) -> Value? {
        let file = fileURL(for: key)
        guard let data = try? Data(contentsOf: file) else { return nil }
        guard let stored = try? JSONDecoder().decode(Stored<Value>.self, from: data),
              now().timeIntervalSince(stored.savedAt) <= lifetime
        else {
            remove(key: key)
            return nil
        }
        return stored.value
    }

    /// Replaces the draft kept under `key` and restarts its lifetime.
    public func save<Value: Codable>(_ value: Value, key: String) {
        do {
            try prepareDirectory()
            let data = try JSONEncoder().encode(Stored(savedAt: now(), value: value))
            try data.write(to: fileURL(for: key), options: writeOptions)
        } catch {
            // See the type's comment: a missing draft costs nothing that exists today.
        }
    }

    public func remove(key: String) {
        try? FileManager.default.removeItem(at: fileURL(for: key))
    }

    /// Removes every draft for every user. Used on sign out.
    public func removeAll() {
        try? FileManager.default.removeItem(at: directory)
    }

    private func fileURL(for key: String) -> URL {
        directory.appending(path: "\(key).json", directoryHint: .notDirectory)
    }

    private var writeOptions: Data.WritingOptions {
        #if os(iOS)
        [.atomic, .completeFileProtection]
        #else
        [.atomic]
        #endif
    }

    private func prepareDirectory() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var folder = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try folder.setResourceValues(values)
    }
}
