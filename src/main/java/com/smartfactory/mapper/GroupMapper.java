package com.smartfactory.mapper;

import com.smartfactory.dto.response.GroupResponse;
import com.smartfactory.entity.Group;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class GroupMapper {

    public GroupResponse toResponse(Group group) {
        return toResponse(group, null);
    }

    public GroupResponse toResponse(Group group, String supervisorName) {
        if (group == null) {
            return null;
        }
        int operatorCount = group.getOperators() != null ? group.getOperators().size() : 0;
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getOperators() != null ? group.getOperators() : new ArrayList<>(),
                group.getSupervisorId(),
                supervisorName,
                operatorCount,
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
