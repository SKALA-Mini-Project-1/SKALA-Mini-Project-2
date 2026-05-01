package com.example.SKALA_Mini_Project_1.modules.fanscore;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "user_artist_fan_scores",
        schema = "queue",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_artist_fan_scores_user_artist",
                        columnNames = {"user_id", "artist_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserArtistFanScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Builder.Default
    @Column(name = "booking_score", nullable = false)
    private Integer bookingScore = 0;

    @Builder.Default
    @Column(name = "external_score", nullable = false)
    private Integer externalScore = 0;

    @Builder.Default
    @Column(name = "total_score", nullable = false)
    private Integer totalScore = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
