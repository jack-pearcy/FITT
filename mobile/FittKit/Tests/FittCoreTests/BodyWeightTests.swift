import Foundation
import Testing
@testable import FittCore

@Suite("Body weight")
struct BodyWeightTests {
    private let today = CalendarDate(year: 2026, month: 11, day: 16)

    @Test("decodes an entry, ignoring the created time")
    func decodesEntry() throws {
        let json = #"{"id":9,"loggedAt":"2026-11-16","weight":82.5,"createdAt":"2026-11-16T05:20:32.742695Z"}"#

        #expect(try JSONDecoder().decode(BodyWeightEntry.self, from: Data(json.utf8))
            == BodyWeightEntry(id: 9, loggedAt: today, weight: Decimal(string: "82.5")!))
    }

    @Test("decodes the average, including the zero the server sends for no entries")
    func decodesAverage() throws {
        #expect(try JSONDecoder().decode(BodyWeightAverage.self, from: Data(#"{"average":81.7}"#.utf8)).average == Decimal(string: "81.7"))
        #expect(try JSONDecoder().decode(BodyWeightAverage.self, from: Data(#"{"average":0}"#.utf8)).average == 0)
    }

    @Test("a draft becomes the request, rounded to one decimal place")
    func buildsRequest() throws {
        let entry = try BodyWeightDraft(date: today, weight: "82.46").entry(today: today)

        #expect(entry == NewBodyWeight(loggedAt: today, weight: Decimal(string: "82.5")!))
    }

    @Test("the request encodes the date and weight the server expects")
    func encodes() throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let body = NewBodyWeight(loggedAt: today, weight: Decimal(string: "82.5")!)

        #expect(String(decoding: try encoder.encode(body), as: UTF8.self) == #"{"loggedAt":"2026-11-16","weight":82.5}"#)
    }

    @Test("weight must be a number from 0.1 to 700", arguments: [
        ("", false), ("abc", false), ("0", false), ("0.04", false), ("0.1", true), ("700", true), ("700.1", false), ("82,5", true),
    ])
    func weightRule(text: String, accepted: Bool) {
        #expect(((try? BodyWeightDraft(date: today, weight: text).entry(today: today)) != nil) == accepted)
    }

    @Test("today and earlier days are accepted, tomorrow is not")
    func dateRule() {
        let yesterday = CalendarDate(year: 2026, month: 11, day: 15)
        let tomorrow = CalendarDate(year: 2026, month: 11, day: 17)

        #expect((try? BodyWeightDraft(date: yesterday, weight: "80").entry(today: today)) != nil)
        #expect(throws: ValidationFailure(fields: ["loggedAt": "The date cannot be in the future"])) {
            try BodyWeightDraft(date: tomorrow, weight: "80").entry(today: today)
        }
    }

    @Test("both problems are reported together")
    func reportsProblems() {
        #expect(throws: ValidationFailure(fields: [
            "weight": "Enter a weight from 0.1 to 700",
            "loggedAt": "The date cannot be in the future",
        ])) {
            try BodyWeightDraft(date: CalendarDate(year: 2027, month: 1, day: 1)).entry(today: today)
        }
    }

    @Test("a draft finds the entry its date would replace")
    func findsReplacedEntry() {
        let entries = [
            BodyWeightEntry(id: 2, loggedAt: today, weight: 82),
            BodyWeightEntry(id: 1, loggedAt: CalendarDate(year: 2026, month: 11, day: 15), weight: 83),
        ]

        #expect(BodyWeightDraft(date: today).replaced(in: entries)?.id == 2)
        #expect(BodyWeightDraft(date: CalendarDate(year: 2026, month: 11, day: 14)).replaced(in: entries) == nil)
    }
}
