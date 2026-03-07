package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.repository.PollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollSchedulerService {
    private final PollRepository pollRepository;

    @Scheduled(fixedRateString = "${polls.scheduler.rate:300000}")
    @Transactional
    public void closeExpiredPolls() {
        int closedCount = pollRepository.closeExpiredPolls(LocalDateTime.now());
        if (closedCount > 0) {
            log.info("Closed {} expired polls", closedCount);
        }
    }
}
