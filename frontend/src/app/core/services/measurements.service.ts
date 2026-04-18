import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BodyWeightAverageResponse {
  average: number | null;
}

export interface BodyWeightEntryResponse {
  id: number;
  loggedAt: string;
  weight: number;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class MeasurementsService {

  // userId is embedded in the path because the backend requires it for ownership enforcement
  private readonly http = inject(HttpClient);

  getSevenDayAverage(userId: number): Observable<BodyWeightAverageResponse> {
    return this.http.get<BodyWeightAverageResponse>(
      `/api/users/${userId}/measurements/weight/average`
    );
  }

  getEntries(userId: number, days: number = 7): Observable<BodyWeightEntryResponse[]> {
    return this.http.get<BodyWeightEntryResponse[]>(
      `/api/users/${userId}/measurements/weight`,
      { params: { days: days.toString() } }
    );
  }

  logWeight(userId: number, weight: number): Observable<BodyWeightEntryResponse> {
    const loggedAt = new Date().toISOString().slice(0, 10);
    return this.http.post<BodyWeightEntryResponse>(
      `/api/users/${userId}/measurements/weight`,
      { loggedAt, weight }
    );
  }
}
