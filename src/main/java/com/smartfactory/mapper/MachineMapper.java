package com.smartfactory.mapper;

import com.smartfactory.dto.response.MachineResponse;
import com.smartfactory.entity.Machine;
import org.springframework.stereotype.Component;

@Component
public class MachineMapper {

    public MachineResponse toResponse(Machine machine, String zoneName) {
        if (machine == null) {
            return null;
        }
        return new MachineResponse(
                machine.getId(),
                machine.getName(),
                machine.getCode(),
                machine.getType(),
                machine.getZoneId(),
                zoneName,
                machine.getStatus(),
                machine.getDescription(),
                machine.getCaracteristiques(),
                machine.getCreatedAt(),
                machine.getUpdatedAt()
        );
    }
}
