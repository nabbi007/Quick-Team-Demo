package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.dto.*;
import com.amalitech.quickpoll.mapper.PollMapper;
import com.amalitech.quickpoll.mapper.PollOptionMapper;
import com.amalitech.quickpoll.model.*;
import com.amalitech.quickpoll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PollService {
    private final PollRepository pollRepository;
    private final PollOptionRepository optionRepository;
    private final VoteRepository voteRepository;
    private final PollMapper pollMapper;
    private final PollOptionMapper pollOptionMapper;

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
        Poll poll = pollMapper.toEntity(request, creator);
        poll = pollRepository.save(poll);

        for (String optionText : request.getOptions()) {
            PollOption option = new PollOption();
            option.setOptionText(optionText);
            option.setPoll(poll);
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
                .mapToInt(o -> voteRepository.countByOptionId(o.getId()))
                .sum();

        List<OptionResponse> optionResponses = options.stream().map(o -> {
            int count = voteRepository.countByOptionId(o.getId());
            OptionResponse response = pollOptionMapper.toResponse(o);
            response.setVoteCount(count);
            response.setPercentage(totalVotes > 0 ? (count * 100.0 / totalVotes) : 0);
            return response;
        }).toList();

        PollResponse response = pollMapper.toResponse(poll);
        response.setTotalVotes(totalVotes);
        response.setOptions(optionResponses);
        return response;
    }
}
