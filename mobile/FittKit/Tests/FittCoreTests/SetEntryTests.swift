import Foundation
import Testing
@testable import FittCore

@Suite("SetEntry")
struct SetEntryTests {
    // Guards the reason weights are Decimal. JSONDecoder has a history of routing
    // JSON numbers through Double first; if that ever returns, these values drift.
    @Test("decodes weights from JSON numbers without losing digits", arguments: [
        "102.35", "9999.99", "0.1", "72.3", "185.55", "699.95", "0",
    ])
    func decodesWeightExactly(literal: String) throws {
        let json = #"{"id":1,"setNumber":1,"reps":8,"weight":\#(literal),"rpe":8.5,"completed":true}"#
        let entry = try JSONDecoder().decode(SetEntry.self, from: Data(json.utf8))
        #expect(entry.weight == Decimal(string: literal))
        #expect(entry.rpe == Decimal(string: "8.5"))
    }

    @Test("decodes null weight and rpe as nil")
    func decodesNulls() throws {
        let json = #"{"id":2,"setNumber":3,"reps":null,"weight":null,"rpe":null,"completed":false}"#
        let entry = try JSONDecoder().decode(SetEntry.self, from: Data(json.utf8))
        #expect(entry == SetEntry(id: 2, setNumber: 3, reps: nil, weight: nil, rpe: nil, completed: false))
    }

    @Test("encodes a weight as the same digits the user entered")
    func encodesWeightExactly() throws {
        let entry = SetEntry(id: 1, setNumber: 1, reps: 5, weight: Decimal(string: "102.35"), rpe: nil, completed: true)
        let json = String(decoding: try JSONEncoder().encode(entry), as: UTF8.self)
        #expect(json.contains(#""weight":102.35"#))
    }
}
