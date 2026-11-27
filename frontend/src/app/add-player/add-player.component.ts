import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PlayerService } from '../player.service';
import { toIsoDate } from '../age';

function notInFuture(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value > toIsoDate(new Date()) ? { future: true } : null;
}

@Component({
  selector: 'app-add-player',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './add-player.component.html',
  styleUrl: './add-player.component.scss',
})
export class AddPlayerComponent {
  private readonly playerService = inject(PlayerService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly today = toIsoDate(new Date());
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    birthDate: ['', notInFuture],
    rapidElo: [1200, [Validators.required, Validators.min(100), Validators.max(3000)]],
    blitzElo: [1200, [Validators.required, Validators.min(100), Validators.max(3000)]],
    bulletElo: [1200, [Validators.required, Validators.min(100), Validators.max(3000)]],
    classicalElo: [1200, [Validators.required, Validators.min(100), Validators.max(3000)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const { birthDate, ...rest } = this.form.getRawValue();

    this.playerService.create({ ...rest, birthDate: birthDate || null }).subscribe({
      next: () => this.router.navigate(['/players']),
      error: () => {
        this.error.set('Failed to add player. Please try again.');
        this.submitting.set(false);
      },
    });
  }
}
