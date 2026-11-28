import FittCore

/// What the user has typed for the next set of each exercise, kept with the
/// unit it was typed in. A number typed in pounds must never be read as
/// kilograms, so a row is given back only in the unit it was typed in. In any
/// other unit the row has no typed text and the screen shows its prefill,
/// which is built in the unit in use.
struct TypedSetRows: Equatable {
    private var rows: [Int64: SetDraft] = [:]
    private var units: UnitsPreference?

    /// The typed row of an entry, or nil when nothing was typed in `units`.
    func row(entryId: Int64, units: UnitsPreference) -> SetDraft? {
        self.units == units ? rows[entryId] : nil
    }

    /// Keeps a typed row. Rows typed in another unit are dropped first.
    mutating func set(_ draft: SetDraft, entryId: Int64, units: UnitsPreference) {
        if self.units != units {
            rows = [:]
            self.units = units
        }
        rows[entryId] = draft
    }
}
