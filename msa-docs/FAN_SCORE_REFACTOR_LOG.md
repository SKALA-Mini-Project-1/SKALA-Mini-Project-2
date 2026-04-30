# Fan Score Refactor Log

## Background

- Goal: give queue-entry time priority to users with higher fan score for the artist of the target concert.
- Current code uses a single global `users.fan_score`, but the real requirement is artist-specific fan score.
- Reliable internal source for score accumulation: confirmed booking history for the same artist.
- External fan club verification is out of scope for now, but the new structure should allow it later.

## Current Findings

- `QueueService` currently reads `users.fan_score` and applies queue weight from that value.
- `concerts.artist_id` already exists.
- Booking history can already be traced through:
  - `bookings.schedule_id`
  - `schedules.concert_id`
  - `concerts.artist_id`
- Current DB/model does not store artist-specific fan scores.

## Planned Direction

1. Introduce artist-specific fan score storage.
2. Refactor queue logic to use `user + artist` score instead of global user score.
3. Derive score from confirmed booking history for the same artist.
4. Keep room for future external fan-club score adjustments.
5. Document schema and logic clearly after implementation.

## Proposed Work Order

1. Add DB structure for artist-specific fan scores.
2. Add repository/service code to read and update artist-specific scores.
3. Backfill scores from existing confirmed bookings.
4. Refactor queue entry logic to resolve artist by concert and apply artist score.
5. Adjust `/api/users/me` and frontend display strategy as needed.
6. Verify build and logic behavior.

## Implemented Changes

- Added new DB-backed summary model: `user_artist_fan_scores`
  - key: `user_id + artist_id`
  - fields: `booking_score`, `external_score`, `total_score`, `created_at`, `updated_at`
- Refactored queue priority lookup to use:
  - target `concertId`
  - resolved `artist_id`
  - artist-specific fan score for `user_id + artist_id`
- Added startup sync to backfill booking-based scores from existing confirmed history.
- Connected payment confirmation flow so a newly confirmed booking increments the corresponding artist fan score.
- Changed `/api/users/me` fanScore response to return the user's cumulative total across artists.
- Updated MyPage copy so it no longer claims a global score is the same as the queue boost for every concert.

## Scoring Rules Applied

- Internal, currently reliable source of artist fan score:
  - confirmed booking history for the same artist
- Scoring unit for this refactor:
  - one confirmed booking for the same artist = `1000` booking score
- Queue priority benefit:
  - artist-specific score is converted to time priority
  - capped at `5000ms`

## Assumptions

- Because the current service does not store actual attendance completion, `CONFIRMED` booking history is used as the best internal proxy for "관람 이력".
- Score accumulation is counted per confirmed booking, not by seat count.
- External fan-club verification is not implemented yet, but `external_score` was added to leave a safe extension point.

## Verification

- Backend compile check:
  - `./gradlew compileJava` succeeded
- Frontend build check:
  - could not run in this environment because `npm` is not installed

## Notes

- Recommended default accumulation rule:
  - one confirmed booking for the same artist = one score accumulation event
- Queue benefit should remain time-based.
- Legacy `users.fan_score` column is no longer used by the queue priority logic in code.
