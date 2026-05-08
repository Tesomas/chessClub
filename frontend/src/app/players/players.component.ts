import { Component, inject, OnInit, signal } from '@angular/core';
import { PlayerService, Player } from '../player.service';

@Component({
  selector: 'app-players',
  templateUrl: './players.component.html',
  styleUrl: './players.component.scss'
})
export class PlayersComponent implements OnInit {
  private readonly playerService = inject(PlayerService);

  protected readonly players = signal<Player[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.playerService.getAll().subscribe({
      next: (data) => {
        this.players.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load players. Is the backend running?');
        this.loading.set(false);
      }
    });
  }
}
