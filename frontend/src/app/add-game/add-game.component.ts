import { Component, inject, OnInit, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PlayerService, Player } from '../player.service';
import { GameService, GameType, GameResult } from '../game.service';

@Component({
  selector: 'app-add-game',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './add-game.component.html',
  styleUrl: './add-game.component.scss',
})
export class AddGameComponent implements OnInit {
  private readonly playerService = inject(PlayerService);
  private readonly gameService = inject(GameService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly players = signal<Player[]>([]);
  protected readonly loadingPlayers = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    whitePlayerId: ['', Validators.required],
    blackPlayerId: ['', Validators.required],
    gameType: ['RAPID' as GameType, Validators.required],
    result: ['WHITE_WINS' as GameResult, Validators.required],
    time: ['', Validators.required],
    date: [new Date().toISOString().split('T')[0], Validators.required],
    pgn: [''],
  });

  ngOnInit(): void {
    this.playerService.getAll().subscribe({
      next: (data) => {
        this.players.set(data);
        this.loadingPlayers.set(false);
      },
      error: () => {
        this.error.set('Could not load players.');
        this.loadingPlayers.set(false);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;

    const { whitePlayerId, blackPlayerId, gameType, result, time, date, pgn } =
      this.form.getRawValue();

    this.submitting.set(true);
    this.error.set(null);

    this.gameService
      .create({ whitePlayerId, blackPlayerId, gameType, result, time, date, pgn: pgn || null })
      .subscribe({
        next: () => this.router.navigate(['/games']),
        error: () => {
          this.error.set('Failed to save game. Please try again.');
          this.submitting.set(false);
        },
      });
  }
}
