package com.amalitech.quickpoll.mapper;

import com.amalitech.quickpoll.dto.PollResponse;
import com.amalitech.quickpoll.model.Poll;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {PollOptionMapper.class})
public interface PollMapper {
    
    @Mapping(target = "creatorName", source = "creator.fullName")
    @Mapping(target = "status", expression = "java(poll.isActive() ? \"ACTIVE\" : \"CLOSED\")")
    @Mapping(target = "multipleChoice", source = "multiSelect")
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "totalVotes", ignore = true)
    PollResponse toResponse(Poll poll);
}
