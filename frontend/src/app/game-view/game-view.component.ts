import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { TitleCasePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, map, Observable, of, startWith, switchMap } from 'rxjs';
import { Game, GameService, RESULT_LABEL } from '../game.service';
import { PlayerColor, PlayerOutcome } from '../player-game.model';
import { outcomeFor } from '../shared/game-outcome';
import { OutcomeBadgeComponent } from '../shared/outcome-badge/outcome-badge.component';
import { PlayerAvatarComponent } from '../shared/player-avatar/player-avatar.component';
import { PgnPanelComponent } from './pgn-panel/pgn-panel.component';

type GameState =
  | { status: 'loading' }
  | { status: 'ready'; game: Game }
  | { status: 'notFound' }
  | { status: 'error' };

interface PlayerCard {
  color: PlayerColor;
  colorLabel: string;
  playerId: string;
  name: string;
  rating: number;
  outcome: PlayerOutcome;
}

const CARD_AVATAR_PX = 64;

@Component({
  selector: 'app-game-view',
  imports: [
    RouterLink,
    TitleCasePipe,
    PlayerAvatarComponent,
    OutcomeBadgeComponent,
    PgnPanelComponent,
  ],
  templateUrl: './game-view.component.html',
  styleUrl: './game-view.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameViewComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly gameService = inject(GameService);

  protected readonly avatarSize = CARD_AVATAR_PX;
  protected readonly resultLabel = RESULT_LABEL;

  /**
   * One request per game id: switchMap cancels the previous request when the component is
   * reused for another id, and startWith('loading') keeps the old game from showing meanwhile.
   */
  private readonly state = toSignal(
    this.route.paramMap.pipe(
      map((params) => params.get('id') ?? ''),
      distinctUntilChanged(),
      switchMap((id) => this.load(id)),
    ),
    { initialValue: { status: 'loading' } as GameState },
  );

  protected readonly status = computed(() => this.state().status);
  protected readonly game = computed(() => {
    const state = this.state();
    return state.status === 'ready' ? state.game : null;
  });
  protected readonly cards = computed((): PlayerCard[] => {
    const game = this.game();
    if (!game) return [];
    return [
      {
        color: 'WHITE',
        colorLabel: 'White',
        playerId: game.whitePlayerId,
        name: game.whitePlayerName,
        rating: game.whitePlayerRating,
        outcome: outcomeFor(game.result, 'WHITE'),
      },
      {
        color: 'BLACK',
        colorLabel: 'Black',
        playerId: game.blackPlayerId,
        name: game.blackPlayerName,
        rating: game.blackPlayerRating,
        outcome: outcomeFor(game.result, 'BLACK'),
      },
    ];
  });

  private load(id: string): Observable<GameState> {
    return this.gameService.getById(id).pipe(
      map((game): GameState => ({ status: 'ready', game })),
      // The 404 body is empty (not ProblemDetail), so only the status is meaningful.
      catchError((error: unknown) =>
        of<GameState>(
          error instanceof HttpErrorResponse && error.status === 404
            ? { status: 'notFound' }
            : { status: 'error' },
        ),
      ),
      startWith<GameState>({ status: 'loading' }),
    );
  }
}
