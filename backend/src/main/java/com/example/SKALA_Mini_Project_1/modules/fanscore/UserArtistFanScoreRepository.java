package com.example.SKALA_Mini_Project_1.modules.fanscore;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserArtistFanScoreRepository extends JpaRepository<UserArtistFanScore, Long> {

    Optional<UserArtistFanScore> findByUserIdAndArtistId(Long userId, Long artistId);

    @Query("""
            select coalesce(sum(score.totalScore), 0)
            from UserArtistFanScore score
            where score.userId = :userId
            """)
    Integer sumTotalScoreByUserId(@Param("userId") Long userId);
}
