import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { WeightLogComponent } from './weight-log';
import { AuthService } from '../../../../core/services/auth.service';
import {
  MeasurementsService,
  BodyWeightEntryResponse
} from '../../../../core/services/measurements.service';

// Component test for WeightLogComponent. Covers two distinct concerns:
// 1. Chart geometry computation in the computed signal (empty list,
//    single point, flat weight series, sort order, axis label rules).
// 2. Behavioral signals and side effects (load on init, period change,
//    todayLogged detection, error handling, duplicate-log guard).
// AuthService and MeasurementsService are stubbed with Vitest mocks.
// The component is instantiated via TestBed.createComponent so the
// inject() field initializers resolve. fixture.detectChanges() is only
// called when ngOnInit needs to run. For pure signal computation tests
// no detectChanges is needed since computed signals recompute on read.
describe('WeightLogComponent', () => {

  const USER_ID = 42;

  const entry = (loggedAt: string, weight: number): BodyWeightEntryResponse => ({
    id: 0,
    loggedAt,
    weight,
    createdAt: `${loggedAt}T08:00:00Z`
  });

  const mockAuth = {
    getUserId: vi.fn()
  };

  const mockMeasurements = {
    getEntries: vi.fn(),
    logWeight: vi.fn()
  };

  let fixture: ComponentFixture<WeightLogComponent>;
  let component: WeightLogComponent;

  beforeEach(() => {
    vi.resetAllMocks();
    mockAuth.getUserId.mockReturnValue(USER_ID);
    mockMeasurements.getEntries.mockReturnValue(of([]));
    mockMeasurements.logWeight.mockReturnValue(of(entry('2026-01-01', 80)));

    TestBed.configureTestingModule({
      imports: [WeightLogComponent],
      providers: [
        { provide: AuthService, useValue: mockAuth },
        { provide: MeasurementsService, useValue: mockMeasurements }
      ]
    });

    fixture = TestBed.createComponent(WeightLogComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    // Restore real timers in case a test used vi.useFakeTimers.
    vi.useRealTimers();
  });

  describe('chart geometry computation', () => {

    it('returns null when entries is empty', () => {
      component.entries.set([]);
      expect(component.chart()).toBeNull();
    });

    it('sorts entries oldest-first before computing geometry', () => {
      // Backend returns newest-first; chart needs oldest-first so the
      // polyline draws left-to-right correctly. The dots array must
      // reflect sorted order regardless of input order.
      component.entries.set([
        entry('2026-05-26', 80.0),
        entry('2026-05-24', 78.5),
        entry('2026-05-25', 79.0)
      ]);

      const chart = component.chart()!;
      expect(chart.dots[0].date).toBe('2026-05-24');
      expect(chart.dots[1].date).toBe('2026-05-25');
      expect(chart.dots[2].date).toBe('2026-05-26');
    });

    it('expands the weight range to a minimum 2kg spread when variance is small', () => {
      // All weights within 0.5kg of each other. Without expansion the
      // line would be visually flat. The component pads the range to
      // 2kg so the line has visible slope.
      component.entries.set([
        entry('2026-05-24', 80.0),
        entry('2026-05-25', 80.3),
        entry('2026-05-26', 80.5)
      ]);

      const chart = component.chart()!;
      const lowestLabel = parseFloat(chart.yLabels[0].text);
      const highestLabel = parseFloat(chart.yLabels[chart.yLabels.length - 1].text);
      expect(highestLabel - lowestLabel).toBeCloseTo(2.0, 1);
    });

    it('expands the date axis by one day on each side for a single data point', () => {
      // With only one date, minD === maxD and the divisor in mapX would
      // be zero. The component pads by one day on each side so the math
      // stays valid and the single dot sits at the chart center.
      component.entries.set([entry('2026-05-26', 80.0)]);

      const chart = component.chart()!;
      expect(chart.dots).toHaveLength(1);
      // Chart spans CHART_L=70 to CHART_R=980, center at 525.
      expect(chart.dots[0].cx).toBeCloseTo(525, 0);
    });

    it('produces exactly 5 y-axis labels and always includes the last date in x-axis labels', () => {
      component.entries.set([
        entry('2026-05-20', 75.0),
        entry('2026-05-21', 76.0),
        entry('2026-05-22', 77.0),
        entry('2026-05-23', 78.0),
        entry('2026-05-24', 79.0),
        entry('2026-05-25', 80.0),
        entry('2026-05-26', 81.0)
      ]);

      const chart = component.chart()!;
      expect(chart.yLabels).toHaveLength(5);
      const lastXLabel = chart.xLabels[chart.xLabels.length - 1];
      expect(lastXLabel.text).toBe('5/26');
    });
  });

  describe('component behavior', () => {

    it('loads data with the default selectedPeriod when ngOnInit runs', () => {
      // detectChanges runs ngOnInit which calls loadData(7).
      fixture.detectChanges();

      expect(mockMeasurements.getEntries).toHaveBeenCalledWith(USER_ID, 7);
      // Loading resolves synchronously because the mock returns of([]).
      expect(component.loading()).toBe(false);
    });

    it('changePeriod updates the signal and reloads with the new period', () => {
      fixture.detectChanges();
      // Clear the initial 7-day call so the next assertion is unambiguous.
      mockMeasurements.getEntries.mockClear();

      component.changePeriod(30);

      expect(component.selectedPeriod()).toBe(30);
      expect(mockMeasurements.getEntries).toHaveBeenCalledWith(USER_ID, 30);
    });

    it('sets todayLogged to true when the loaded entries include todays date', () => {
      // Pin today so the component's date arithmetic is deterministic
      // regardless of when the test runs.
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2026, 4, 26, 12, 0));

      mockMeasurements.getEntries.mockReturnValue(of([
        entry('2026-05-25', 79.0),
        entry('2026-05-26', 80.0)
      ]));

      fixture.detectChanges();

      expect(component.todayLogged()).toBe(true);
    });

    it('sets the error signal when loadData fails', () => {
      mockMeasurements.getEntries.mockReturnValue(throwError(() => new Error('boom')));

      fixture.detectChanges();

      expect(component.error()).toBe('Failed to load weight entries');
      expect(component.loading()).toBe(false);
    });

    it('blocks logWeight submission when todays weight is already logged', () => {
      // Pre-flag the signal so the duplicate guard fires before any
      // service call would happen.
      component.todayLogged.set(true);
      const input = { value: '80.5' } as HTMLInputElement;

      component.logWeight(input);

      expect(mockMeasurements.logWeight).not.toHaveBeenCalled();
      expect(component.error()).toBe("Today's weight is already logged");
    });
  });
});
