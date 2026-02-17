package com.example.SKALA_Mini_Project_1.modules.concerts.repository;

import com.example.SKALA_Mini_Project_1.modules.concerts.dto.ConcertResponse;
import com.example.SKALA_Mini_Project_1.modules.concerts.dto.ConcertScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ConcertQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<ConcertResponse> findVisibleConcerts() {
        String sql = """
                SELECT c.id,
                       c.title,
                       c.category,
                       c.description,
                       c.location,
                       c.duration_minutes,
                       c.is_visible,
                       c.created_at,
                       c.artist_id,
                       a.name AS artist_name,
                       (
                         SELECT MIN(s2.price)
                         FROM schedules sc2
                         JOIN seats s2 ON s2.schedule_id = sc2.id
                         WHERE sc2.concert_id = c.id
                       ) AS min_price
                FROM concerts c
                LEFT JOIN artist a ON a.id = c.artist_id
                WHERE c.is_visible = true
                ORDER BY c.id DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapConcert(rs, findSchedules(rs.getLong("id"))));
    }

    public ConcertResponse findVisibleConcertById(Long concertId) {
        String sql = """
                SELECT c.id,
                       c.title,
                       c.category,
                       c.description,
                       c.location,
                       c.duration_minutes,
                       c.is_visible,
                       c.created_at,
                       c.artist_id,
                       a.name AS artist_name,
                       (
                         SELECT MIN(s2.price)
                         FROM schedules sc2
                         JOIN seats s2 ON s2.schedule_id = sc2.id
                         WHERE sc2.concert_id = c.id
                       ) AS min_price
                FROM concerts c
                LEFT JOIN artist a ON a.id = c.artist_id
                WHERE c.id = ? AND c.is_visible = true
                """;

        List<ConcertResponse> rows = jdbcTemplate.query(sql, (rs, rowNum) -> mapConcert(rs, findSchedules(concertId)), concertId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<ConcertScheduleResponse> findSchedules(Long concertId) {
        String sql = """
                SELECT id, start_time, end_time, total_seats
                FROM schedules
                WHERE concert_id = ?
                ORDER BY start_time ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ConcertScheduleResponse(
                rs.getLong("id"),
                toOffsetDateTime(rs.getTimestamp("start_time")),
                toOffsetDateTime(rs.getTimestamp("end_time")),
                rs.getInt("total_seats")
        ), concertId);
    }

    private ConcertResponse mapConcert(ResultSet rs, List<ConcertScheduleResponse> schedules) throws SQLException {
        return new ConcertResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("description"),
                rs.getString("location"),
                rs.getInt("duration_minutes"),
                rs.getBoolean("is_visible"),
                toOffsetDateTime(rs.getTimestamp("created_at")),
                rs.getLong("artist_id"),
                rs.getString("artist_name"),
                rs.getObject("min_price") == null ? null : rs.getLong("min_price"),
                schedules
        );
    }

    private OffsetDateTime toOffsetDateTime(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
