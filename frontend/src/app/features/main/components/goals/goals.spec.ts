import { describe, it, beforeEach, expect, vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { GoalsComponent } from './goals';
import { AuthService } from '../../../../core/services/auth.service';
import {
  IdentityService,
  GoalsResponse
} from '../../../../core/services/identity.service';

// Component test for GoalsComponent. Covers the four-state mode machine
// (loading, view, edit, error), the forkJoin parallel load in ngOnInit,
// the constructor subscription that toggles the calorieGoal control's
// enabled state based on calorieGoalMode, the startEdit form patching,
// the saveGoals success refetch flow, and the MANUAL mode validation
// guard. AuthService and IdentityService are stubbed with Vitest mocks.
// The component is instantiated via TestBed.createComponent so the
// inject() field initializers and the constructor subscription resolve.
// detectChanges triggers ngOnInit when the test needs the initial load.
describe('GoalsComponent', () => {

  const USER_ID = 42;

  const initialGoals: GoalsResponse = {
    goalDirection: 'MAINTAIN',
    targetWeightKg: 80,
    timelineWeeks: 12,
    calorieGoalMode: 'AUTO',
    calorieGoal: null
  };

  const mockAuth = {
    getUserId: vi.fn()
  };

  const mockIdentity = {
    getGoals: vi.fn(),
    getTdee: vi.fn(),
    saveGoals: vi.fn()
  };

  let fixture: ComponentFixture<GoalsComponent>;
  let component: GoalsComponent;

  beforeEach(() => {
    vi.resetAllMocks();
    mockAuth.getUserId.mockReturnValue(USER_ID);
    mockIdentity.getGoals.mockReturnValue(of(initialGoals));
    mockIdentity.getTdee.mockReturnValue(of({ tdee: 2500 }));
    mockIdentity.saveGoals.mockReturnValue(of(undefined));

    TestBed.configureTestingModule({
      imports: [GoalsComponent],
      providers: [
        { provide: AuthService, useValue: mockAuth },
        { provide: IdentityService, useValue: mockIdentity }
      ]
    });

    fixture = TestBed.createComponent(GoalsComponent);
    component = fixture.componentInstance;
  });

  describe('mode state machine', () => {

    it('starts in loading mode before ngOnInit runs', () => {
      // No detectChanges yet, so ngOnInit has not fired. The signal's
      // initial value is the only thing under test here.
      expect(component.mode()).toBe('loading');
    });

    it('transitions to view and populates goals plus tdee on successful parallel load', () => {
      fixture.detectChanges();

      expect(component.mode()).toBe('view');
      expect(component.goals()).toEqual(initialGoals);
      expect(component.tdee()).toBe(2500);
      expect(mockIdentity.getGoals).toHaveBeenCalledWith(USER_ID);
      expect(mockIdentity.getTdee).toHaveBeenCalledWith(USER_ID);
    });

    it('transitions to error mode when the forkJoin fails', () => {
      // forkJoin emits an error when any of its inputs errors out, so
      // failing just one input is enough to exercise the error path.
      mockIdentity.getTdee.mockReturnValue(throwError(() => new Error('boom')));

      fixture.detectChanges();

      expect(component.mode()).toBe('error');
      expect(component.error()).toBe('Failed to load your goals');
    });

    it('transitions to edit mode and patches the form from the goals signal on startEdit', () => {
      fixture.detectChanges();

      component.startEdit();

      expect(component.mode()).toBe('edit');
      expect(component.goalDirection.value).toBe(initialGoals.goalDirection);
      expect(component.targetWeightKg.value).toBe(initialGoals.targetWeightKg);
      expect(component.timelineWeeks.value).toBe(initialGoals.timelineWeeks);
      expect(component.calorieGoalMode.value).toBe(initialGoals.calorieGoalMode);
    });

    it('returns to view mode on cancelEdit without touching the goals signal', () => {
      fixture.detectChanges();
      component.startEdit();

      component.cancelEdit();

      expect(component.mode()).toBe('view');
      // The goals signal still holds the original value, not the form's
      // working copy. cancelEdit is a pure mode transition.
      expect(component.goals()).toEqual(initialGoals);
    });

    it('refetches goals after a successful save and transitions back to view', () => {
      const refetchedGoals: GoalsResponse = {
        ...initialGoals,
        calorieGoal: 2400
      };
      fixture.detectChanges();
      component.startEdit();

      // Override getGoals so the post-save refetch returns the updated
      // record. The server may have calculated the AUTO calorie goal
      // and the component needs to surface that to the user.
      mockIdentity.getGoals.mockReturnValue(of(refetchedGoals));

      component.saveGoals();

      expect(mockIdentity.saveGoals).toHaveBeenCalled();
      // Two getGoals calls total: one in ngOnInit, one after save.
      expect(mockIdentity.getGoals).toHaveBeenCalledTimes(2);
      expect(component.mode()).toBe('view');
      expect(component.goals()).toEqual(refetchedGoals);
      expect(component.saving()).toBe(false);
    });

    it('sets the error signal and clears saving when saveGoals fails', () => {
      fixture.detectChanges();
      component.startEdit();
      mockIdentity.saveGoals.mockReturnValue(throwError(() => new Error('boom')));

      component.saveGoals();

      expect(component.error()).toBe('Failed to save goals');
      expect(component.saving()).toBe(false);
      // Mode should stay in edit on save failure. The error mode is
      // reserved for the initial load failure path.
      expect(component.mode()).toBe('edit');
    });
  });

  describe('calorie goal mode toggle', () => {

    it('enables the calorieGoal control when calorieGoalMode switches to MANUAL', () => {
      // Initial declared state: calorieGoal is disabled.
      expect(component.calorieGoal.disabled).toBe(true);

      component.calorieGoalMode.setValue('MANUAL');

      // The constructor subscription fires synchronously on valueChanges.
      expect(component.calorieGoal.disabled).toBe(false);
    });

    it('disables the calorieGoal control when calorieGoalMode switches back to AUTO', () => {
      component.calorieGoalMode.setValue('MANUAL');
      expect(component.calorieGoal.disabled).toBe(false);

      component.calorieGoalMode.setValue('AUTO');

      expect(component.calorieGoal.disabled).toBe(true);
    });
  });

  describe('saveGoals validation', () => {

    it('blocks the save when MANUAL mode has a null calorieGoal', () => {
      fixture.detectChanges();
      component.startEdit();
      component.calorieGoalMode.setValue('MANUAL');
      component.calorieGoal.setValue(null);

      component.saveGoals();

      // Validation guard should fire before the service call.
      expect(mockIdentity.saveGoals).not.toHaveBeenCalled();
    });
  });
});
