package com.amalitech.quickpoll.repository;

import com.amalitech.quickpoll.model.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    List<PollOption> findByPollId(Long pollId);
}
