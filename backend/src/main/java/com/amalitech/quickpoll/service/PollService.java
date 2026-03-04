package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.dto.*;
import com.amalitech.quickpoll.model.*;
import com.amalitech.quickpoll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PollService {
    private final PollRepository pollRepository;
    private final PollOptionRepository optionRepository;
    private final VoteRepository voteRepository;

    public Page<PollResponse> getAllPolls(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return pollRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    public PollResponse getPollById(Long id) {
        Poll poll = pollRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Poll not found"));
        return toResponse(poll);
    }

    public PollResponse createPoll(PollRequest request, User creator) {
        Poll poll = Poll.builder()
                .question(request.getQuestion())
                .description(request.getDescription())
                .creator(creator)
                .multipleChoice(request.isMultipleChoice())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
        poll = pollRepository.save(poll);

        for (String optionText : request.getOptions()) {
            PollOption option = PollOption.builder()
                    .text(optionText)
                    .poll(poll)
                    .build();
            optionRepository.save(option);
        }
        return toResponse(pollRepository.findById(poll.getId()).get());
    }

    // TODO: Implement vote method
    // public void vote(Long pollId, VoteRequest request, User voter) { ... }

    // TODO: Implement closePoll method
    // public PollResponse closePoll(Long pollId, User creator) { ... }

    // TODO: Implement deletePoll method

    private PollResponse toResponse(Poll poll) {
        List<PollOption> options = optionRepository.findByPollId(poll.getId());
        int totalVotes = options.stream()
                .mapToInt(o -> voteRepository.countByPollOptionId(o.getId()))
                .sum();

        List<OptionResponse> optionResponses = options.stream().map(o -> {
            int count = voteRepository.countByPollOptionId(o.getId());
            return OptionResponse.builder()
                    .id(o.getId())
                    .text(o.getText())
                    .voteCount(count)
                    .percentage(totalVotes > 0 ? (count * 100.0 / totalVotes) : 0)
                    .build();
        }).toList();

        return PollResponse.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .description(poll.getDescription())
                .creatorName(poll.getCreator().getName())
                .status(poll.getStatus())
                .multipleChoice(poll.isMultipleChoice())
                .createdAt(poll.getCreatedAt())
                .totalVotes(totalVotes)
                .options(optionResponses)
                .build();
    }
}
