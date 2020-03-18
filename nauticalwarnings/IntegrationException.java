package fi.liikennevirasto.winvis.nauticalwarnings;

/**
 * This is our own custom exception to signal error in integration calls
 */
public class IntegrationException extends RuntimeException {
    public IntegrationException() {
    }

    public IntegrationException(String message) {
        super(message);
    }

    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public IntegrationException(Throwable cause) {
        super(cause);
    }

    public IntegrationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
