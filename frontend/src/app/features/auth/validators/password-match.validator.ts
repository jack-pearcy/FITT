import { AbstractControl, ValidationErrors } from '@angular/forms';

export function passwordMatch(group: AbstractControl): ValidationErrors | null {
    const pw      = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;

    if (!pw || !confirm) return null;

    if (pw !== confirm) {
        group.get('confirmPassword')?.setErrors({ passwordMatch: true });
    } else {
        const confirmControl = group.get('confirmPassword');
        const errors = { ...confirmControl?.errors };
        delete errors['passwordMatch'];
        const hasErrors = Object.keys(errors).length > 0;
        confirmControl?.setErrors(hasErrors ? errors : null);
    }

    return null;
}