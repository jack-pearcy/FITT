import { describe, it, beforeEach, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

// App is a thin router host. Its only job is to render the matching route
// component inside <router-outlet />. Visible UI assertions belong in the
// feature component specs, not here. This file confirms that App can be
// instantiated under the test injector so DI wiring regressions surface
// the moment they break, not three components deep into a manual click-through.
describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
