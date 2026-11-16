import Foundation
import Testing
@testable import FittCore

@Suite("CalendarDate")
struct CalendarDateTests {
    @Test("encodes as yyyy-MM-dd with zero padding")
    func encodes() throws {
        let json = try JSONEncoder().encode([CalendarDate(year: 1994, month: 3, day: 7)])
        #expect(String(decoding: json, as: UTF8.self) == #"["1994-03-07"]"#)
    }

    @Test("decodes the server's LocalDate string")
    func decodes() throws {
        let dates = try JSONDecoder().decode([CalendarDate].self, from: Data(#"["2026-11-15"]"#.utf8))
        #expect(dates == [CalendarDate(year: 2026, month: 11, day: 15)])
    }

    @Test("rejects text that is not a date", arguments: ["", "2026-11", "2026-13-01", "2026-11-32", "15-11-2026", "2026-1-5", "2026-11-15T00:00:00Z"])
    func rejects(text: String) {
        #expect(CalendarDate(iso: text) == nil)
    }

    @Test("orders by year, then month, then day")
    func orders() {
        #expect(CalendarDate(year: 1999, month: 12, day: 31) < CalendarDate(year: 2000, month: 1, day: 1))
        #expect(CalendarDate(year: 2000, month: 1, day: 31) < CalendarDate(year: 2000, month: 2, day: 1))
        #expect(CalendarDate(year: 2000, month: 2, day: 1) < CalendarDate(year: 2000, month: 2, day: 2))
    }

    @Test("takes the day from the calendar's time zone, not UTC")
    func usesCalendarTimeZone() {
        // 2026-11-16 03:30 UTC is still the evening of the 15th in Chicago.
        let instant = Date(timeIntervalSince1970: 1_794_799_800)
        var chicago = Calendar(identifier: .gregorian)
        chicago.timeZone = TimeZone(identifier: "America/Chicago")!
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!

        #expect(CalendarDate(instant, calendar: chicago) == CalendarDate(year: 2026, month: 11, day: 15))
        #expect(CalendarDate(instant, calendar: utc) == CalendarDate(year: 2026, month: 11, day: 16))
    }

    @Test("round trips through Date")
    func roundTrips() throws {
        let day = CalendarDate(year: 2000, month: 2, day: 29)
        let date = try #require(day.date())
        #expect(CalendarDate(date) == day)
    }
}
