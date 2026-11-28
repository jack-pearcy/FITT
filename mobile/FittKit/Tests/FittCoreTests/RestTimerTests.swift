import FittCore
import Foundation
import Testing

@Suite("RestTimer")
struct RestTimerTests {
    private let start = Date(timeIntervalSince1970: 1_800_000_000)

    @Test("the time left counts down from the end date and stops at zero")
    func remaining() {
        let timer = RestTimer(length: 120, now: start)
        #expect(timer.remaining(at: start) == 120)
        #expect(timer.remaining(at: start.addingTimeInterval(45)) == 75)
        #expect(timer.remaining(at: start.addingTimeInterval(500)) == 0)
        #expect(!timer.isOver(at: start.addingTimeInterval(119)))
        #expect(timer.isOver(at: start.addingTimeInterval(120)))
    }

    @Test("time spent in the background is counted, because nothing ticks")
    func backgrounded() {
        let timer = RestTimer(length: 90, now: start)
        // Ten minutes away from the app, then back.
        #expect(timer.isOver(at: start.addingTimeInterval(600)))
        #expect(timer.fractionLeft(at: start.addingTimeInterval(600)) == 0)
    }

    @Test("the share left goes from one to zero")
    func fractionLeft() {
        let timer = RestTimer(length: 120, now: start)
        #expect(timer.fractionLeft(at: start) == 1)
        #expect(timer.fractionLeft(at: start.addingTimeInterval(30)) == 0.75)
        #expect(timer.fractionLeft(at: start.addingTimeInterval(120)) == 0)
    }

    @Test("+30 s moves the end on, and after rest is over it counts from now")
    func addExtra() {
        var timer = RestTimer(length: 60, now: start)
        timer.addExtra(now: start.addingTimeInterval(10))
        #expect(timer.endsAt == start.addingTimeInterval(90))

        var over = RestTimer(length: 60, now: start)
        over.addExtra(now: start.addingTimeInterval(200))
        #expect(over.endsAt == start.addingTimeInterval(230))
        #expect(over.remaining(at: start.addingTimeInterval(200)) == 30)
    }

    @Test("lengths stay between 30 seconds and 10 minutes, on 15 second steps")
    func clamped() {
        #expect(RestTimer.clamped(5) == 30)
        #expect(RestTimer.clamped(1_000) == 600)
        #expect(RestTimer.clamped(100) == 105)
        #expect(RestTimer.clamped(120) == 120)
        #expect(RestTimer(length: 2, now: start).endsAt == start.addingTimeInterval(30))
    }

    @Test("the clock reads minutes and seconds, rounding up")
    func clock() {
        #expect(RestTimer.clock(120) == "2:00")
        #expect(RestTimer.clock(90) == "1:30")
        #expect(RestTimer.clock(65) == "1:05")
        #expect(RestTimer.clock(0.2) == "0:01")
        #expect(RestTimer.clock(0) == "0:00")
    }

    @Test("the spoken time names minutes and seconds")
    func spoken() {
        #expect(RestTimer.spoken(90) == "1 minute 30 seconds")
        #expect(RestTimer.spoken(120) == "2 minutes")
        #expect(RestTimer.spoken(1) == "1 second")
        #expect(RestTimer.spoken(0) == "0 seconds")
    }

    @Test("the default rest is two minutes")
    func defaultLength() {
        #expect(RestTimer.defaultLength == 120)
    }
}
