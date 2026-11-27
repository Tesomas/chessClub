import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  InjectionToken,
  linkedSignal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs';
import { Player, PlayerService } from '../player.service';
import {
  DEFAULT_QUERY,
  Page,
  PlayerGameFilter,
  PlayerGameQuery,
  PlayerGameView,
} from '../player-game.model';
import { isFilterValid, queryFromParamMap, queryToRouteParams } from '../player-game-query';
import { ageFrom } from '../age';
import { GameFilterComponent } from './game-filter/game-filter.component';
import {
  PlayerGamesTableComponent,
  SortChange,
} from './player-games-table/player-games-table.component';

/** Clock used for the age calculation; overridable in tests. */
export const TODAY = new InjectionToken<() => Date>('TODAY', {
  providedIn: 'root',
  factory: () => () => new Date(),
});

type PlayerState =
  | { status: 'loading' }
  | { status: 'ready'; player: Player }
  | { status: 'notFound' }
  | { status: 'error' };

/** Games for one player id; the id keeps one profile's rows from showing under another. */
type GamesState = { playerId: string } & (
  | { status: 'loading' }
  | { status: 'ready'; page: Page<PlayerGameView> }
  | { status: 'error'; message: string }
);

/** Route state for one navigation: path id and query params read together. */
interface ProfileNav {
  id: string;
  query: PlayerGameQuery;
}

function sameNav(a: ProfileNav, b: ProfileNav): boolean {
  return a.id === b.id && JSON.stringify(a.query) === JSON.stringify(b.query);
}

const INVALID_FILTER_MESSAGE =
  'The date range in this link is invalid. Adjust or reset the filters.';
const GAMES_ERROR_MESSAGE = 'Could not load games. Please try again.';

function problemDetail(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse)) return null;
  const detail: unknown = error.error?.detail;
  return typeof detail === 'string' && detail.trim() ? detail : null;
}

@Component({
  selector: 'app-player-profile',
  imports: [RouterLink, GameFilterComponent, PlayerGamesTableComponent],
  templateUrl: './player-profile.component.html',
  styleUrl: './player-profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayerProfileComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly playerService = inject(PlayerService);
  private readonly today = inject(TODAY);

  /**
   * One emission per navigation. paramMap and queryParamMap emit separately when the component
   * is reused (e.g. following an opponent link), which would pair the new id with the old query
   * and fire an extra request; reading the snapshot after NavigationEnd sees both at once.
   */
  private readonly nav$: Observable<ProfileNav> = this.router.events.pipe(
    filter((event) => event instanceof NavigationEnd),
    startWith(null),
    map(() => this.readNav()),
    distinctUntilChanged(sameNav),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  private readonly player$ = this.nav$.pipe(
    map((nav) => nav.id),
    distinctUntilChanged(),
    switchMap((id) => this.loadPlayer(id).pipe(map((state) => ({ id, state })))),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  protected readonly query = toSignal(this.nav$.pipe(map((nav) => nav.query)), {
    initialValue: DEFAULT_QUERY,
  });

  private readonly playerState = toSignal(this.player$.pipe(map(({ state }) => state)), {
    initialValue: { status: 'loading' } as PlayerState,
  });

  /** Games are only requested once the player is known to exist, and never for a stale id. */
  private readonly gamesState = toSignal(
    combineLatest([this.player$, this.nav$]).pipe(
      filter(([player, nav]) => player.id === nav.id),
      switchMap(([player, nav]) =>
        player.state.status === 'ready'
          ? this.loadGames(nav.id, nav.query)
          : of<GamesState>({ playerId: nav.id, status: 'loading' }),
      ),
    ),
    { initialValue: { playerId: '', status: 'loading' } as GamesState },
  );

  protected readonly status = computed(() => this.playerState().status);
  protected readonly player = computed(() => {
    const state = this.playerState();
    return state.status === 'ready' ? state.player : null;
  });
  protected readonly age = computed(() => {
    const player = this.player();
    return player ? ageFrom(player.birthDate, this.today()) : null;
  });
  protected readonly initials = computed(() => {
    const player = this.player();
    return player ? `${player.firstName.charAt(0)}${player.lastName.charAt(0)}`.toUpperCase() : '';
  });
  protected readonly ratings = computed(() => {
    const player = this.player();
    if (!player) return [];
    return [
      { label: 'Bullet', value: player.bulletElo },
      { label: 'Blitz', value: player.blitzElo },
      { label: 'Rapid', value: player.rapidElo },
      { label: 'Classical', value: player.classicalElo },
    ];
  });
  protected readonly gamesLoading = computed(() => this.gamesState().status === 'loading');
  protected readonly gamesError = computed(() => {
    const state = this.gamesState();
    return state.status === 'error' ? state.message : null;
  });
  /** Last loaded page of the current player, kept on screen (dimmed) while the next one loads. */
  private readonly visibleGames = linkedSignal<
    GamesState,
    { playerId: string; page: Page<PlayerGameView> } | null
  >({
    source: this.gamesState,
    computation: (state, previous) => {
      if (state.status === 'ready') return { playerId: state.playerId, page: state.page };
      const kept = previous?.value;
      return state.status === 'loading' && kept?.playerId === state.playerId ? kept : null;
    },
  });
  protected readonly visiblePage = computed(() => this.visibleGames()?.page ?? null);

  protected onFilterChange(filter: PlayerGameFilter): void {
    this.navigate({ ...this.query(), filter, page: 0 });
  }

  protected onSortChange({ sort, direction }: SortChange): void {
    this.navigate({ ...this.query(), sort, direction, page: 0 });
  }

  protected onPageChange(page: number): void {
    this.navigate({ ...this.query(), page });
  }

  private navigate(query: PlayerGameQuery): void {
    // Replace (not merge) the query params so shared URLs always have the same key order.
    const queryParams = Object.fromEntries(
      Object.entries(queryToRouteParams(query)).filter(([, value]) => value !== null),
    );
    this.router.navigate([], { relativeTo: this.route, queryParams });
  }

  private readNav(): ProfileNav {
    const snapshot = this.route.snapshot;
    return {
      id: snapshot.paramMap.get('id') ?? '',
      query: queryFromParamMap(snapshot.queryParamMap),
    };
  }

  private loadPlayer(id: string): Observable<PlayerState> {
    return this.playerService.getById(id).pipe(
      map((player): PlayerState => ({ status: 'ready', player })),
      catchError((error: unknown) =>
        of<PlayerState>(
          error instanceof HttpErrorResponse && error.status === 404
            ? { status: 'notFound' }
            : { status: 'error' },
        ),
      ),
      startWith<PlayerState>({ status: 'loading' }),
    );
  }

  private loadGames(playerId: string, query: PlayerGameQuery): Observable<GamesState> {
    if (!isFilterValid(query.filter)) {
      return of<GamesState>({ playerId, status: 'error', message: INVALID_FILTER_MESSAGE });
    }
    return this.playerService.getGames(playerId, query).pipe(
      map((page): GamesState => ({ playerId, status: 'ready', page })),
      catchError((error: unknown) =>
        of<GamesState>({
          playerId,
          status: 'error',
          message: problemDetail(error) ?? GAMES_ERROR_MESSAGE,
        }),
      ),
      startWith<GamesState>({ playerId, status: 'loading' }),
    );
  }
}
