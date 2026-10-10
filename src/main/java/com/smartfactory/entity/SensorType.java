package com.smartfactory.entity;

public enum SensorType {
    TEMPERATURE("°C"),
    VIBRATION("mm/s"),
    CURRENT("A"),
    RPM("RPM");

    private final String defaultUnit;

    SensorType(String defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }

    public static SensorType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (SensorType type : values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }

    public boolean isValidUnit(String unit) {
        if (unit == null || unit.trim().isEmpty()) {
            return false;
        }
        String u = unit.trim();
        switch (this) {
            case TEMPERATURE:
                return "°C".equalsIgnoreCase(u) || "C".equalsIgnoreCase(u) || "degC".equalsIgnoreCase(u) || "°c".equals(u);
            case VIBRATION:
                return "mm/s".equalsIgnoreCase(u) || "mm/sec".equalsIgnoreCase(u);
            case CURRENT:
                return "A".equalsIgnoreCase(u) || "amp".equalsIgnoreCase(u) || "ampere".equalsIgnoreCase(u);
            case RPM:
                return "RPM".equalsIgnoreCase(u) || "tr/min".equalsIgnoreCase(u);
            default:
                return false;
        }
    }
}
