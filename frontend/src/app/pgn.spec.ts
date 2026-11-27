import { parsePgn } from './pgn';

/** Morphy vs Duke Karl / Count Isouard, Paris 1858 ("the Opera game"), with added annotations. */
const OPERA_GAME = [
  '[Event "A Night at the Opera"]',
  '[Site "Paris FRA"]',
  '[Date "1858.??.??"]',
  '[Round "?"]',
  '[White "Morphy, Paul"]',
  '[Black "Duke Karl / Count Isouard"]',
  '[Result "1-0"]',
  '[ECO "C41"]',
  '',
  '; Philidor Defence',
  '1. e4 e5 2. Nf3 d6 {The Philidor Defence.} 3. d4 Bg4 $6 {Already dubious.}',
  '(3... exd4 4. Nxd4 (4. Qxd4 Nc6 {with (nested) ideas}) 4... Nf6) 4. dxe5 Bxf3',
  '5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 $1 Qe7 8. Nc3 c6 9. Bg5 b5 $2 10. Nxb5! cxb5',
  '11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 15. Bxd7+ Nxd7',
  '16. Qb8+!! Nxb8 17. Rd8# 1-0',
].join('\r\n');

describe('parsePgn', () => {
  it('parses a real annotated game with comments, nested variations and NAGs', () => {
    const parsed = parsePgn(OPERA_GAME);
    expect(parsed).not.toBeNull();
    expect(parsed?.headers.map((h) => h.name)).toEqual([
      'Event',
      'Site',
      'Date',
      'Round',
      'White',
      'Black',
      'Result',
      'ECO',
    ]);
    expect(parsed?.headers[4]).toEqual({ name: 'White', value: 'Morphy, Paul' });
    expect(parsed?.moves).toHaveLength(17);
    expect(parsed?.moves[0]).toEqual({ number: 1, white: 'e4', black: 'e5' });
    expect(parsed?.moves[2]).toEqual({ number: 3, white: 'd4', black: 'Bg4' });
    // the variation 3... exd4 4. Nxd4 ... is skipped, main line continues
    expect(parsed?.moves[3]).toEqual({ number: 4, white: 'dxe5', black: 'Bxf3' });
    expect(parsed?.moves[9]).toEqual({ number: 10, white: 'Nxb5!', black: 'cxb5' });
    expect(parsed?.moves[11]).toEqual({ number: 12, white: 'O-O-O', black: 'Rd8' });
    expect(parsed?.moves[15]).toEqual({ number: 16, white: 'Qb8+!!', black: 'Nxb8' });
    expect(parsed?.moves[16]).toEqual({ number: 17, white: 'Rd8#' });
    expect(parsed?.result).toBe('1-0');
  });

  it('parses movetext without headers or move numbers', () => {
    expect(parsePgn('e4 e5 Nf3 Nc6')).toEqual({
      headers: [],
      moves: [
        { number: 1, white: 'e4', black: 'e5' },
        { number: 2, white: 'Nf3', black: 'Nc6' },
      ],
      result: null,
    });
  });

  it('handles a game that starts with a black move number', () => {
    expect(parsePgn('12... Qxd5 13. Nc3 Qd8')?.moves).toEqual([
      { number: 12, black: 'Qxd5' },
      { number: 13, white: 'Nc3', black: 'Qd8' },
    ]);
  });

  it('accepts move numbers with spacing and repeated black numbers', () => {
    expect(parsePgn('1 . d4 {c} 1... d5 2.c4')?.moves).toEqual([
      { number: 1, white: 'd4', black: 'd5' },
      { number: 2, white: 'c4' },
    ]);
  });

  it('keeps promotions, castling variants and check marks', () => {
    expect(parsePgn('1. e8=Q+ exf1=N# 2. 0-0 0-0-0 3. b8Q O-O')?.moves).toEqual([
      { number: 1, white: 'e8=Q+', black: 'exf1=N#' },
      { number: 2, white: '0-0', black: '0-0-0' },
      { number: 3, white: 'b8Q', black: 'O-O' },
    ]);
  });

  it.each(['1-0', '0-1', '1/2-1/2', '*'])('recognizes the %s result token', (token) => {
    expect(parsePgn(`1. e4 e5 ${token}`)?.result).toBe(token);
  });

  it('falls back to the Result header when the movetext has no result token', () => {
    expect(parsePgn('[Result "0-1"]\n1. f3 e5 2. g4 Qh4#')?.result).toBe('0-1');
  });

  it('parses a headers-only PGN', () => {
    expect(parsePgn('[Event "Casual"]\n[Result "*"]\n')).toEqual({
      headers: [
        { name: 'Event', value: 'Casual' },
        { name: 'Result', value: '*' },
      ],
      moves: [],
      result: '*',
    });
  });

  it('unescapes quotes and backslashes in header values', () => {
    expect(parsePgn('[Annotator "A \\"Fritz\\" C:\\\\x"]')?.headers).toEqual([
      { name: 'Annotator', value: 'A "Fritz" C:\\x' },
    ]);
  });

  it('ignores % escape lines, extra whitespace and tabs', () => {
    expect(parsePgn('%engine data\n\n  1.\te4   \t e5  \n\n')?.moves).toEqual([
      { number: 1, white: 'e4', black: 'e5' },
    ]);
  });

  it('ignores a semicolon comment that runs to the end of the input', () => {
    expect(parsePgn('1. e4 ; best by test')?.moves).toEqual([{ number: 1, white: 'e4' }]);
  });

  it('rejects anything but comments after the result token', () => {
    expect(parsePgn('1. e4 1-0 garbage that is not a move')).toBeNull();
    expect(parsePgn('1. e4 e5 1-0 2. Nf3')).toBeNull();
  });

  it('rejects a second game so the raw text is shown instead of only game one', () => {
    const twoGames = '[Event "A"]\n1. e4 e5 1-0\n\n[Event "B"]\n1. d4 d5 0-1\n';
    expect(parsePgn(twoGames)).toBeNull();
  });

  it('allows whitespace and comments after the result token', () => {
    expect(parsePgn('1. e4 e5 1/2-1/2 \r\n {agreed} ; late note\n  ')).toEqual({
      headers: [],
      moves: [{ number: 1, white: 'e4', black: 'e5' }],
      result: '1/2-1/2',
    });
  });

  it('keeps up to three annotation glyphs on a move', () => {
    expect(parsePgn('1. e4!!? e5?!? 2. Nf3!? Nc6??')?.moves).toEqual([
      { number: 1, white: 'e4!!?', black: 'e5?!?' },
      { number: 2, white: 'Nf3!?', black: 'Nc6??' },
    ]);
  });

  it('accepts a black move number written with a space before the dots', () => {
    expect(parsePgn('1. e4 {c} 1. ... e5 2. Nf3')?.moves).toEqual([
      { number: 1, white: 'e4', black: 'e5' },
      { number: 2, white: 'Nf3' },
    ]);
  });

  it('parses a very long game in linear time', () => {
    const plies = 30_000;
    const sans = ['Nf3', 'Nf6', 'Ng1', 'Ng8'];
    const movetext = Array.from({ length: plies / 2 }, (_, i) => {
      const n = i + 1;
      return `${n}. ${sans[(2 * i) % 4]} ${sans[(2 * i + 1) % 4]}`;
    }).join(' ');

    const started = performance.now();
    const parsed = parsePgn(`${movetext} 1/2-1/2`);
    const elapsed = performance.now() - started;

    expect(parsed?.moves).toHaveLength(plies / 2);
    expect(parsed?.moves.at(-1)).toEqual({ number: plies / 2, white: 'Ng1', black: 'Ng8' });
    // Generous bound. Building the move list by copying took ~900 ms for 30K plies (vs <1 ms).
    expect(elapsed).toBeLessThan(250);
  });

  it.each([
    ['empty input', ''],
    ['blank input', '  \r\n '],
    ['free text', 'we drew after a long fight'],
    ['an unterminated comment', '1. e4 {oops'],
    ['an unterminated variation', '1. e4 (1. d4 d5'],
    ['an unmatched closing paren', '1. e4 ) e5'],
    ['a malformed tag pair', '[Event Casual]\n1. e4'],
    ['a tag pair after the moves', '1. e4 [Event "x"]'],
    ['an invalid square', '1. e9 e5'],
    ['a lone move number', '1.'],
  ])('returns null for %s', (_label, text) => {
    expect(parsePgn(text)).toBeNull();
  });
});
