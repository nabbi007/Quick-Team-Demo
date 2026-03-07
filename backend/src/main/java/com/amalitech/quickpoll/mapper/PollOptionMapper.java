package com.amalitech.quickpoll.mapper;

import com.amalitech.quickpoll.dto.OptionResponse;
import com.amalitech.quickpoll.model.PollOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PollOptionMapper {
    
    @Mapping(target = "text", source = "optionText")
    @Mapping(target = "voteCount", ignore = true)
    @Mapping(target = "percentage", ignore = true)
    OptionResponse toResponse(PollOption pollOption);
}
