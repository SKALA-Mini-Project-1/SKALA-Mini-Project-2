package com.example.SKALA_Mini_Project_1.modules.fanscore;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserArtistFanScoreRepository extends JpaRepository<UserArtistFanScore, Long> {
    Optional<UserArtistFanScore> findByUserIdAndArtistId(Long userId, Long artistId);
}
