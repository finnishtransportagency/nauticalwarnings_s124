package fi.liikennevirasto.winvis.nauticalwarnings;

import java.util.Arrays;

/**
 * Warnings can be in a few states during their lifecycle. There's active -> expired -> deleted.
 * This enum represents two first states, deleted warnings are gone for good.
 */
public enum WarningStatus {
    EXPIRED("expired"), ACTIVE("active");

    private String value;

    WarningStatus(String value) {
        this.value = value;
    }

    public static WarningStatus fromValue(String value) {
        for (WarningStatus category : values()) {
            if (category.value.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException(
                "Unknown enum type " + value + ", Allowed values are " + Arrays.toString(values()));
    }

}
