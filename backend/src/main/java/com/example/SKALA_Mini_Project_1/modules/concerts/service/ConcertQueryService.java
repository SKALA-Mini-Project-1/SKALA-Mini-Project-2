package com.example.SKALA_Mini_Project_1.modules.concerts.service;

import com.example.SKALA_Mini_Project_1.modules.concerts.dto.ConcertResponse;
import com.example.SKALA_Mini_Project_1.modules.concerts.repository.ConcertQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConcertQueryService {

    private final ConcertQueryRepository concertQueryRepository;

    public List<ConcertResponse> getVisibleConcerts() {
        return concertQueryRepository.findVisibleConcerts();
    }

    public ConcertResponse getVisibleConcertById(Long concertId) {
        return concertQueryRepository.findVisibleConcertById(concertId);
    }
}
