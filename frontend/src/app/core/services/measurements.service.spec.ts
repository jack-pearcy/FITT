import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import {
  BodyWeightAverageResponse,
  BodyWeightEntryResponse,
  MeasurementsService
} from './measurements.service';

// Service test for MeasurementsService. Three methods: two GETs and one
// POST. The POST is non-trivial because it builds the loggedAt date string
// at call time from new Date(), with zero-padded month and day. Tests lock
// down URL templates, the query parameter behavior for getEntries (default
// 7 plus override), response delivery, and the date formatting in logWeight
// using Vitest fake timers for determinism.
describe('MeasurementsService', () => {

  const USER_ID = 42;
  const BASE_URL = `/api/users/${USER_ID}/measurements/weight`;

  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('getSevenDayAverage GETs the average URL and delivers the typed response', () => {
    const service = TestBed.inject(MeasurementsService);
    const mockResponse: BodyWeightAverageResponse = { average: 79.5 };

    let received: BodyWeightAverageResponse | undefined;
    service.getSevenDayAverage(USER_ID).subscribe(r => received = r);

    const req = controller.expectOne(`${BASE_URL}/average`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(received).toEqual(mockResponse);
  });

  it('getEntries defaults to a 7-day window when no days value is provided', () => {
    const service = TestBed.inject(MeasurementsService);

    service.getEntries(USER_ID).subscribe();

    // Predicate form checks the base url and the days param separately so
    // the assertion is explicit about what is being matched.
    const req = controller.expectOne(
      r => r.url === BASE_URL && r.params.get('days') === '7'
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getEntries forwards a supplied days value as the days query parameter', () => {
    const service = TestBed.inject(MeasurementsService);

    service.getEntries(USER_ID, 30).subscribe();

    const req = controller.expectOne(
      r => r.url === BASE_URL && r.params.get('days') === '30'
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getEntries delivers the typed entry array to the subscriber', () => {
    const service = TestBed.inject(MeasurementsService);
    const mockEntries: BodyWeightEntryResponse[] = [
      { id: 1, loggedAt: '2026-05-25', weight: 80.0, createdAt: '2026-05-25T08:00:00Z' },
      { id: 2, loggedAt: '2026-05-26', weight: 79.8, createdAt: '2026-05-26T08:00:00Z' }
    ];

    let received: BodyWeightEntryResponse[] | undefined;
    service.getEntries(USER_ID).subscribe(r => received = r);

    controller.expectOne(r => r.url === BASE_URL).flush(mockEntries);

    expect(received).toEqual(mockEntries);
  });

  it('logWeight POSTs the zero-padded loggedAt date and the weight, delivers the response', () => {
    // March 5, 2026 at noon local time exercises both the month and day
    // padStart branches without timezone surprises. Constructing with
    // year+month+day args (not an ISO string) avoids UTC parsing so the
    // getDate/getMonth/getFullYear calls return predictable local values
    // regardless of the host OS timezone.
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 5, 12, 0));

    try {
      const service = TestBed.inject(MeasurementsService);
      const mockResponse: BodyWeightEntryResponse = {
        id: 99,
        loggedAt: '2026-03-05',
        weight: 80.5,
        createdAt: '2026-03-05T12:00:00Z'
      };

      let received: BodyWeightEntryResponse | undefined;
      service.logWeight(USER_ID, 80.5).subscribe(r => received = r);

      const req = controller.expectOne(BASE_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ loggedAt: '2026-03-05', weight: 80.5 });
      req.flush(mockResponse);

      expect(received).toEqual(mockResponse);
    } finally {
      // Restore real timers regardless of whether the test throws so the
      // next test starts with normal Date behavior.
      vi.useRealTimers();
    }
  });
});
