import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { AuthService } from '../../../../core/services/auth.service';
import {
  MeasurementsService,
  BodyWeightEntryResponse
} from '../../../../core/services/measurements.service';
import { Subscription } from 'rxjs';

// Chart coordinate space inside the viewBox
const CHART_L = 70;
const CHART_R = 980;
const CHART_T = 20;
const CHART_B = 350;

interface ChartPoint {
  cx: number;
  cy: number;
  weight: number;
  date: string;
}

interface ChartGeometry {
  polylinePoints: string;
  dots: ChartPoint[];
  yLabels: { y: number; text: string }[];
  xLabels: { x: number; text: string }[];
  gridYs: number[];
}

@Component({
  selector: 'app-weight-log',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './weight-log.html',
  styleUrl: './weight-log.scss'
})
export class WeightLogComponent implements OnInit, OnDestroy {

  private readonly auth = inject(AuthService);
  private readonly measurements = inject(MeasurementsService);

  readonly entries = signal<BodyWeightEntryResponse[]>([]);
  readonly selectedPeriod = signal(7);
  readonly loading = signal(true);
  readonly todayLogged = signal(false);
  readonly submitting = signal(false);
  readonly error = signal('');

  // Cancel in-flight request when period changes rapidly
  private loadSub?: Subscription;

  readonly periods = [7, 30, 60, 90];

  // Chart geometry derived from entries. Null when there is nothing to draw.
  readonly chart = computed<ChartGeometry | null>(() => {
    const raw = this.entries();
    if (raw.length === 0) return null;

    // Backend returns newest first, chart needs oldest first
    const sorted = [...raw].sort((a, b) => a.loggedAt.localeCompare(b.loggedAt));

    const dates = sorted.map(e => new Date(e.loggedAt + 'T00:00:00').getTime());
    const weights = sorted.map(e => e.weight);

    let minW = Math.min(...weights);
    let maxW = Math.max(...weights);

    // Minimum 2kg spread so the line never looks flat
    if (maxW - minW < 2) {
      const mid = (minW + maxW) / 2;
      minW = mid - 1;
      maxW = mid + 1;
    }

    let minD = Math.min(...dates);
    let maxD = Math.max(...dates);

    // Single data point, give the date axis some breathing room
    if (minD === maxD) {
      minD -= 86400000;
      maxD += 86400000;
    }

    const mapX = (d: number) =>
      CHART_L + ((d - minD) / (maxD - minD)) * (CHART_R - CHART_L);

    const mapY = (w: number) =>
      CHART_T + ((maxW - w) / (maxW - minW)) * (CHART_B - CHART_T);

    const dots: ChartPoint[] = sorted.map((e, i) => ({
      cx: mapX(dates[i]),
      cy: mapY(e.weight),
      weight: e.weight,
      date: e.loggedAt
    }));

    const polylinePoints = dots.map(d => `${d.cx},${d.cy}`).join(' ');

    // 5 evenly spaced labels on the y axis
    const yCount = 5;
    const yLabels: { y: number; text: string }[] = [];
    const gridYs: number[] = [];
    for (let i = 0; i < yCount; i++) {
      const w = minW + (i / (yCount - 1)) * (maxW - minW);
      const y = mapY(w);
      yLabels.push({ y, text: w.toFixed(1) });
      gridYs.push(y);
    }

    // Up to 7 date labels spread across the data points
    const maxLabels = 7;
    const step = Math.max(1, Math.floor((sorted.length - 1) / (maxLabels - 1)));
    const xLabels: { x: number; text: string }[] = [];
    for (let i = 0; i < sorted.length; i += step) {
      const d = new Date(sorted[i].loggedAt + 'T00:00:00');
      xLabels.push({
        x: mapX(dates[i]),
        text: `${d.getMonth() + 1}/${d.getDate()}`
      });
    }
    // Always include the last date
    const lastIdx = sorted.length - 1;
    if (xLabels.length === 0 || xLabels[xLabels.length - 1].x !== mapX(dates[lastIdx])) {
      const d = new Date(sorted[lastIdx].loggedAt + 'T00:00:00');
      xLabels.push({
        x: mapX(dates[lastIdx]),
        text: `${d.getMonth() + 1}/${d.getDate()}`
      });
    }

    return { polylinePoints, dots, yLabels, xLabels, gridYs };
  });

  ngOnInit(): void {
    this.loadData(this.selectedPeriod());
  }

  ngOnDestroy(): void {
    this.loadSub?.unsubscribe();
  }

  changePeriod(period: number): void {
    this.selectedPeriod.set(period);
    this.loadData(period);
  }

  loadData(days: number): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    // Kill previous in-flight request so a stale response never overwrites fresh data
    this.loadSub?.unsubscribe();
    this.loading.set(true);
    this.error.set('');

    this.loadSub = this.measurements.getEntries(userId, days).subscribe({
      next: entries => {
        this.entries.set(entries);
        const now = new Date();
        const today = [
          now.getFullYear(),
          String(now.getMonth() + 1).padStart(2, '0'),
          String(now.getDate()).padStart(2, '0')
        ].join('-');
        this.todayLogged.set(entries.some(e => e.loggedAt === today));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load weight entries');
        this.loading.set(false);
      }
    });
  }

  logWeight(input: HTMLInputElement): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    const weight = parseFloat(input.value);
    if (isNaN(weight) || weight <= 0) return;

    if (this.todayLogged()) {
      this.error.set("Today's weight is already logged");
      return;
    }

    this.submitting.set(true);

    this.measurements.logWeight(userId, weight).subscribe({
      next: () => {
        input.value = '';
        this.submitting.set(false);
        this.loadData(this.selectedPeriod());
      },
      error: () => {
        this.error.set('Failed to log weight');
        this.submitting.set(false);
      }
    });
  }
}
