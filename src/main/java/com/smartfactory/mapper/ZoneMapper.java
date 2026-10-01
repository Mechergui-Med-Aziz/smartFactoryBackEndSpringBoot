package com.smartfactory.mapper;

import com.smartfactory.dto.response.ZoneResponse;
import com.smartfactory.entity.Zone;
import org.springframework.stereotype.Component;

@Component
public class ZoneMapper {

    public ZoneResponse toResponse(Zone zone, long machineCount) {
        if (zone == null) {
            return null;
        }
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getDescription(),
                zone.getLocation(),
                machineCount,
                zone.getCreatedAt(),
                zone.getUpdatedAt()
        );
    }
}
