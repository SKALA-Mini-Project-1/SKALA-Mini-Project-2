package com.example.SKALA_Mini_Project_1.modules.fanscore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FanScoreService {

    private final UserArtistFanScoreRepository userArtistFanScoreRepository;

    public int getTotalFanScore(Long userId) {
        Integer total = userArtistFanScoreRepository.sumTotalScoreByUserId(userId);
        return total == null ? 0 : Math.max(0, total);
    }
}
