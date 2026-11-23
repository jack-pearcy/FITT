import Foundation
import Testing
@testable import FittUI

@Suite("Draft store")
struct DraftStoreTests {
    private struct Note: Codable, Equatable {
        var text: String
    }

    private final class Clock: @unchecked Sendable {
        private let lock = NSLock()
        private var value: Date
        init(_ start: Date) { value = start }
        var now: Date { lock.withLock { value } }
        func advance(_ seconds: TimeInterval) { lock.withLock { value += seconds } }
    }

    private let folder = FileManager.default.temporaryDirectory
        .appending(path: "fitt-draft-store-\(UUID().uuidString)", directoryHint: .isDirectory)

    private func store(_ clock: Clock, lifetime: TimeInterval = DraftStore.defaultLifetime) -> DraftStore {
        DraftStore(directory: folder, lifetime: lifetime, now: { clock.now })
    }

    @Test("a saved draft reads back")
    func roundTrip() {
        defer { try? FileManager.default.removeItem(at: folder) }
        let drafts = store(Clock(Date(timeIntervalSince1970: 1_800_000_000)))

        drafts.save(Note(text: "Push day"), key: "7.plan.new")

        #expect(drafts.load(Note.self, key: "7.plan.new") == Note(text: "Push day"))
        #expect(drafts.load(Note.self, key: "8.plan.new") == nil)
    }

    @Test("a draft older than its lifetime is dropped")
    func expires() {
        defer { try? FileManager.default.removeItem(at: folder) }
        let clock = Clock(Date(timeIntervalSince1970: 1_800_000_000))
        let drafts = store(clock, lifetime: 3600)
        drafts.save(Note(text: "Pull day"), key: "k")

        clock.advance(3600)
        #expect(drafts.load(Note.self, key: "k") != nil)

        clock.advance(1)
        #expect(drafts.load(Note.self, key: "k") == nil)
        #expect(!FileManager.default.fileExists(atPath: folder.appending(path: "k.json").path()))
    }

    @Test("saving again restarts the lifetime")
    func saveRestartsLifetime() {
        defer { try? FileManager.default.removeItem(at: folder) }
        let clock = Clock(Date(timeIntervalSince1970: 1_800_000_000))
        let drafts = store(clock, lifetime: 3600)
        drafts.save(Note(text: "v1"), key: "k")
        clock.advance(3000)
        drafts.save(Note(text: "v2"), key: "k")
        clock.advance(3000)

        #expect(drafts.load(Note.self, key: "k") == Note(text: "v2"))
    }

    @Test("a file that does not decode is removed")
    func unreadableIsRemoved() throws {
        defer { try? FileManager.default.removeItem(at: folder) }
        let drafts = store(Clock(Date(timeIntervalSince1970: 1_800_000_000)))
        drafts.save(Note(text: "x"), key: "k")

        #expect(drafts.load(Int.self, key: "k") == nil)
        #expect(drafts.load(Note.self, key: "k") == nil)
    }

    @Test("remove and remove all delete the files")
    func removal() {
        defer { try? FileManager.default.removeItem(at: folder) }
        let drafts = store(Clock(Date(timeIntervalSince1970: 1_800_000_000)))
        drafts.save(Note(text: "a"), key: "a")
        drafts.save(Note(text: "b"), key: "b")

        drafts.remove(key: "a")
        #expect(drafts.load(Note.self, key: "a") == nil)
        #expect(drafts.load(Note.self, key: "b") != nil)

        drafts.removeAll()
        #expect(!FileManager.default.fileExists(atPath: folder.path()))
    }

    @Test("the folder is left out of backups")
    func excludedFromBackup() throws {
        defer { try? FileManager.default.removeItem(at: folder) }
        let drafts = store(Clock(Date(timeIntervalSince1970: 1_800_000_000)))
        drafts.save(Note(text: "a"), key: "a")

        let values = try folder.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(values.isExcludedFromBackup == true)
    }
}
