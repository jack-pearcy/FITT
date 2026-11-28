import FittCore
import Foundation
import Testing
@testable import FittUI

@Suite("Typed set rows")
struct TypedSetRowsTests {
    @Test("a typed row is given back in the unit it was typed in")
    func keepsRow() {
        var rows = TypedSetRows()
        rows.set(SetDraft(weight: "225", reps: "5"), entryId: 3, units: .imperial)

        #expect(rows.row(entryId: 3, units: .imperial) == SetDraft(weight: "225", reps: "5"))
        #expect(rows.row(entryId: 4, units: .imperial) == nil)
    }

    @Test("a row typed in pounds is never read as kilograms after the units change")
    func rowIsNotReadInAnotherUnit() throws {
        var rows = TypedSetRows()
        let typed = SetDraft(weight: "225", reps: "5")
        rows.set(typed, entryId: 3, units: .imperial)

        // Units go to metric in Settings while the row holds 225. The row has
        // no typed text in kilograms, so the screen falls back to its prefill.
        #expect(rows.row(entryId: 3, units: .metric) == nil)

        // What the row would log in the unit it was typed in, and what the
        // same text would wrongly log if it were read in the new one.
        #expect(try typed.loggedSet(units: .imperial).weight == Decimal(string: "102.06"))
        #expect(try typed.loggedSet(units: .metric).weight == 225)
    }

    @Test("typing in the new unit drops the rows typed in the old one")
    func typingInNewUnitDropsOldRows() {
        var rows = TypedSetRows()
        rows.set(SetDraft(weight: "225", reps: "5"), entryId: 3, units: .imperial)
        rows.set(SetDraft(weight: "135", reps: "8"), entryId: 4, units: .imperial)

        rows.set(SetDraft(weight: "100", reps: "5"), entryId: 4, units: .metric)

        #expect(rows.row(entryId: 4, units: .metric) == SetDraft(weight: "100", reps: "5"))
        #expect(rows.row(entryId: 3, units: .metric) == nil)
        // Going back to pounds does not bring the old rows back either.
        #expect(rows.row(entryId: 3, units: .imperial) == nil)
        #expect(rows.row(entryId: 4, units: .imperial) == nil)
    }

    @Test("rows typed in the same unit are kept side by side")
    func sameUnitKeepsRows() {
        var rows = TypedSetRows()
        rows.set(SetDraft(weight: "100", reps: "5"), entryId: 3, units: .metric)
        rows.set(SetDraft(weight: "60", reps: "8"), entryId: 4, units: .metric)
        rows.set(SetDraft(weight: "102.5", reps: "5"), entryId: 3, units: .metric)

        #expect(rows.row(entryId: 3, units: .metric) == SetDraft(weight: "102.5", reps: "5"))
        #expect(rows.row(entryId: 4, units: .metric) == SetDraft(weight: "60", reps: "8"))
    }

    @Test("with nothing typed there is no row in either unit")
    func emptyAtStart() {
        #expect(TypedSetRows().row(entryId: 3, units: .metric) == nil)
        #expect(TypedSetRows().row(entryId: 3, units: .imperial) == nil)
    }
}
