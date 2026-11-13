# ChessClub — Goals

## Purpose

ChessClub is a tool for individuals who organize local chess clubs. It gives an organizer one place to
manage their members, see how strong each member is, keep a record of the games played, and run
tournaments, without spreadsheets and paper pairing sheets.

## Primary users

- **Club organizer (admin)** — runs the club: manages the roster, records results, creates and runs
  tournaments. This is the only user type in the first version.
- **Club member (future)** — logs in to see their own rating and history, records their own games,
  and enters tournaments.

## Core goals

### 1. Track player ELO
- Maintain a roster of club members.
- Keep a separate rating per time-control category: **rapid, blitz, bullet, classical**.
- Update ratings automatically from recorded games (including tournament games) using an ELO
  calculation, rather than requiring manual edits.
- Keep a rating history so progress over time can be viewed, not only the current number.
- Allow the admin to set a starting rating for new members and to correct ratings manually.

### 2. Record game history
- Record every game: the two players, result, game type, time control, date, and optionally the PGN.
- Store each player's rating at the time of the game, so history stays accurate after ratings change.
- Browse and filter history by player, game type, date range, and tournament.
- Show a head-to-head record between any two players.

### 3. Run tournaments
Support three formats, for any subset of club members:

| Format | Summary |
|---|---|
| **Swiss** | A fixed number of rounds; each round pairs players with similar scores, avoiding rematches. |
| **Round robin** | Every player plays every other player once (optionally twice, with colors reversed). |
| **Elimination** | Knockout bracket; losers are out. Handles byes when the player count is not a power of two. |

For every format the app should:
- Create a tournament, register participants, and generate pairings.
- Record results as games, so they feed the ELO and game-history goals above.
- Show live standings (with tiebreaks) and the current round or bracket.
- Handle byes, withdrawals, and corrections to a previously entered result.
- Keep a completed tournament as a permanent record.

## Design principle: single admin now, multi-user later

The first version has one admin and no login, but the design should not block member accounts. Keep
these in mind as features are built:

- **Separate identity from membership.** A `Player` is a club member's chess record. A future
  `User` (login account) should link to a `Player` rather than replace it, so the admin can still
  add players who never log in.
- **Put authorization in the service layer, not only the controllers.** Write operations should be
  structured as "this actor may do this action", so that member-level rules (for example, "record a
  game only if you are one of the players") can be added later without rewriting the business logic.
- **Track who did what.** Games and tournament results should record who entered them, and results
  entered by a member may eventually need the opponent's or admin's confirmation.
- **Plan for more than one club.** Do not assume a single global roster where it is cheap to avoid.
- **Keep the API stateless and token-friendly**, so adding authentication (for example, JWT or
  OAuth2 through Spring Security) is a matter of configuration plus access rules.

## Future capabilities (not in scope yet)

- Member login, self-service game entry, and tournament sign-up.
- Result confirmation workflow between players.
- Public club page with standings and recent results.
- Import and export (PGN, CSV).
- Notifications for pairings and upcoming rounds.

## Out of scope

- Playing chess online or analyzing positions; the app records and organizes games played elsewhere.
- Official federation (FIDE/USCF) rating submission. Ratings are internal to the club.

## Open questions

- Which ELO K-factor scheme to use, and whether new players get a provisional-rating period.
- Which Swiss pairing rules and tiebreaks to implement (for example, Dutch system, Buchholz).
- Whether elimination tournaments need double elimination or only single.
