import FittCore
import Testing
@testable import FittUI

@Suite("SetForm")
struct SetFormTests {
    @Test("the log button reads the unit in the singular for a weight of one")
    func singularWeight() {
        #expect(SetForm.logSpoken(draft: SetDraft(weight: "1", reps: "8"), units: .metric)
            == "Log set, 1 kilogram for 8 reps")
        #expect(SetForm.logSpoken(draft: SetDraft(weight: "1.0", reps: "1"), units: .imperial)
            == "Log set, 1.0 pound for 1 rep")
    }

    @Test("the log button reads the unit in the plural for any other weight")
    func pluralWeight() {
        #expect(SetForm.logSpoken(draft: SetDraft(weight: "80", reps: "8"), units: .metric)
            == "Log set, 80 kilograms for 8 reps")
        #expect(SetForm.logSpoken(draft: SetDraft(weight: "0.5", reps: "8"), units: .imperial)
            == "Log set, 0.5 pounds for 8 reps")
    }

    @Test("the log button reads reps alone without a weight, and nothing without reps")
    func withoutWeightOrReps() {
        #expect(SetForm.logSpoken(draft: SetDraft(weight: "", reps: "12"), units: .metric) == "Log set, 12 reps")
        #expect(SetForm.logSpoken(draft: SetDraft(weight: "80", reps: ""), units: .metric) == "Log set")
    }

    @Test("the log button shows the set it will log")
    func titleWithSet() {
        #expect(SetForm.logTitle(draft: SetDraft(weight: "80", reps: "8"), units: .metric) == "Log 80 kg × 8")
        #expect(SetForm.logTitle(draft: SetDraft(weight: "225", reps: "5"), units: .imperial) == "Log 225 lb × 5")
    }

    @Test("the log button shows reps alone without a weight, in the singular for one")
    func titleRepsOnly() {
        #expect(SetForm.logTitle(draft: SetDraft(weight: "", reps: "12"), units: .metric) == "Log 12 reps")
        #expect(SetForm.logTitle(draft: SetDraft(weight: "", reps: "1"), units: .metric) == "Log 1 rep")
    }

    @Test("the log button shows plain Log when reps are empty or not a number")
    func titleWithoutReps() {
        #expect(SetForm.logTitle(draft: SetDraft(weight: "80", reps: ""), units: .metric) == "Log")
        #expect(SetForm.logTitle(draft: SetDraft(weight: "80", reps: "abc"), units: .metric) == "Log")
    }
}
