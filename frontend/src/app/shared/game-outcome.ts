import { GameResult } from '../game.service';
import { PlayerColor, PlayerOutcome } from '../player-game.model';

/** The outcome of a game for the player who had `color`. */
export function outcomeFor(result: GameResult, color: PlayerColor): PlayerOutcome {
  if (result === 'DRAW') return 'DRAW';
  const winner: PlayerColor = result === 'WHITE_WINS' ? 'WHITE' : 'BLACK';
  return winner === color ? 'WIN' : 'LOSS';
}
