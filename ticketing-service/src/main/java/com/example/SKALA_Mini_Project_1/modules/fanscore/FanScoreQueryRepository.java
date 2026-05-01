package com.example.SKALA_Mini_Project_1.modules.fanscore;

import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FanScoreQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<Long> findArtistIdByBookingId(UUID bookingId) {
        String sql = """
                SELECT c.artist_id
                FROM ticketing.bookings b
                JOIN concert.schedules s ON s.id = b.schedule_id
                JOIN concert.concerts c ON c.id = s.concert_id
                WHERE b.id = ?
                  AND c.artist_id IS NOT NULL
                """;

        List<Long> rows = jdbcTemplate.query(sql, this::mapNullableLong, bookingId);
        return rows.stream().findFirst();
    }

    public Optional<ConfirmedBookingFanScoreTargetRow> findEligibleConfirmedBookingTarget(
            UUID bookingId,
            OffsetDateTime referenceTime
    ) {
        String sql = """
                SELECT b.id AS booking_id,
                       b.user_id AS user_id,
                       s.concert_id AS concert_id
                FROM bookings b
                JOIN schedules s ON s.id = b.schedule_id
                JOIN payments p ON p.booking_id = b.id
                WHERE b.id = ?
                  AND b.status = 'CONFIRMED'
                  AND b.confirmed_at IS NOT NULL
                  AND p.status = 'CONFIRMED'
                  AND s.end_time <= ?
                  AND b.fan_score_applied_at IS NULL
                """;

        List<ConfirmedBookingFanScoreTargetRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ConfirmedBookingFanScoreTargetRow(
                        rs.getObject("booking_id", UUID.class),
                        rs.getLong("user_id"),
                        rs.getLong("concert_id")
                ),
                bookingId,
                referenceTime
        );
        return rows.stream().findFirst();
    }

    public List<ConfirmedBookingFanScoreTargetRow> findEligibleConfirmedBookingTargets(OffsetDateTime referenceTime) {
        String sql = """
                SELECT b.id AS booking_id,
                       b.user_id AS user_id,
                       s.concert_id AS concert_id
                FROM bookings b
                JOIN schedules s ON s.id = b.schedule_id
                JOIN payments p ON p.booking_id = b.id
                WHERE b.status = 'CONFIRMED'
                  AND b.confirmed_at IS NOT NULL
                  AND p.status = 'CONFIRMED'
                  AND s.end_time <= ?
                  AND b.fan_score_applied_at IS NULL
                ORDER BY s.end_time ASC, b.confirmed_at ASC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ConfirmedBookingFanScoreTargetRow(
                        rs.getObject("booking_id", UUID.class),
                        rs.getLong("user_id"),
                        rs.getLong("concert_id")
                ),
                referenceTime
        );
    }

    public List<ConfirmedArtistBookingCountRow> findConfirmedArtistBookingCounts() {
        String sql = """
                SELECT b.user_id AS user_id,
                       c.artist_id AS artist_id,
                       COUNT(DISTINCT b.id) AS confirmed_booking_count
                FROM ticketing.bookings b
                JOIN concert.schedules s ON s.id = b.schedule_id
                JOIN concert.concerts c ON c.id = s.concert_id
                JOIN payment.payments p ON p.booking_id = b.id
                WHERE b.status = 'CONFIRMED'
                  AND p.status = 'CONFIRMED'
                  AND c.artist_id IS NOT NULL
                GROUP BY b.user_id, c.artist_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ConfirmedArtistBookingCountRow(
                rs.getLong("user_id"),
                rs.getLong("artist_id"),
                rs.getLong("confirmed_booking_count")
        ));
    }

    public void addBookingScore(Long userId, Long artistId, int delta) {
        String sql = """
                INSERT INTO queue.user_artist_fan_scores (
                    user_id,
                    artist_id,
                    booking_score,
                    external_score,
                    total_score,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, GREATEST(0, ?), 0, GREATEST(0, ?), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, artist_id)
                DO UPDATE
                SET booking_score = GREATEST(0, queue.user_artist_fan_scores.booking_score + EXCLUDED.booking_score),
                    total_score = GREATEST(
                        0,
                        GREATEST(0, queue.user_artist_fan_scores.booking_score + EXCLUDED.booking_score)
                        + queue.user_artist_fan_scores.external_score
                    ),
                    updated_at = CURRENT_TIMESTAMP
                """;

        jdbcTemplate.update(sql, userId, artistId, delta, delta);
    }

    private Long mapNullableLong(ResultSet rs, int rowNum) throws SQLException {
        return rs.getObject(1, Long.class);
    }

    public record ConfirmedBookingFanScoreTargetRow(UUID bookingId, Long userId, Long concertId) {
    }

    public record ConfirmedArtistBookingCountRow(Long userId, Long artistId, Long confirmedBookingCount) {
    }
}
