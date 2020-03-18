package fi.liikennevirasto.winvis.common;

/**
 * Enum for delivery to center statuses
 * <p>
 * NOTE: ENUM VALUES SHOULD BE MAXIMUM OF 10 CHARS LONG!
 */
public enum DeliveryStatus {
    /**
     * New, undelivered to IBNext center
     */
    NEW("NEW"),
    /**
     * Received by IBNext center
     */
    RECEIVED("RECEIVED"),
    /**
     * Route modified as a draft
     */
    MODIFIED("MODIFIED"),
    /**
     * Route has been set as recommended route, ready to be send
     */
    RECOMMEND("RECOMMEND"),
    /**
     * Delivered back to STM and SeaSwim
     */
    DELIVERED("DELIVERED");

    private final String status;

    DeliveryStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return status;
    }
}
