import { Component, inject, OnInit, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GameService, Game, RESULT_LABEL } from '../game.service';

@Component({
  selector: 'app-games',
  standalone: true,
  imports: [RouterLink, LowerCasePipe],
  templateUrl: './games.component.html',
  styleUrl: './games.component.scss',
})
export class GamesComponent implements OnInit {
  private readonly gameService = inject(GameService);

  protected readonly games = signal<Game[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly resultLabel = RESULT_LABEL;

  ngOnInit(): void {
    this.gameService.getAll().subscribe({
      next: (data) => {
        this.games.set(data.slice().sort((a, b) => b.date.localeCompare(a.date)));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load games. Is the backend running?');
        this.loading.set(false);
      },
    });
  }
}
