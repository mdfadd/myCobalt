package it.auties.whatsapp.api;

import it.auties.whatsapp.exception.HmacValidationException;
import it.auties.whatsapp.util.Exceptions;

import java.nio.file.Path;
import java.util.function.Consumer;

import static it.auties.whatsapp.api.ErrorHandler.Location.*;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

/**
 * This interface allows to handle a socket error and provides a default way to do so
 */
@SuppressWarnings("unused")
public interface ErrorHandler {
    /**
     * Handles an error that occurred inside the api
     *
     * @param type      the type of client experiencing the error
     * @param location  the location where the error occurred
     * @param throwable a stacktrace of the error, if available
     * @return a newsletters determining what should be done
     */
    Result handleError(ClientType type, Location location, Throwable throwable);

    /**
     * Default error handler. Prints the exception on the terminal.
     *
     * @return a non-null error handler
     */
    static ErrorHandler toTerminal() {
        return defaultErrorHandler(Throwable::printStackTrace);
    }

    /**
     * Default error handler. Saves the exception locally.
     * The file will be saved in $HOME/.cobalt/errors
     *
     * @return a non-null error handler
     */
    static ErrorHandler toFile() {
        return defaultErrorHandler(Exceptions::save);
    }

    /**
     * Default error handler. Saves the exception locally.
     * The file will be saved in {@code directory}.
     *
     * @param directory the directory where the error should be saved
     * @return a non-null error handler
     */
    static ErrorHandler toFile(Path directory) {
        return defaultErrorHandler(throwable -> Exceptions.save(directory, throwable));
    }

    /**
     * Default error handler
     *
     * @param printer a consumer that handles the printing of the throwable, can be null
     * @return a non-null error handler
     */
    static ErrorHandler defaultErrorHandler(Consumer<Throwable> printer) {
        return (type, location, throwable) -> {
            var logger = System.getLogger("ErrorHandler");
            logger.log(ERROR, "Socket failure at %s".formatted(location));
            if (printer != null) {
                printer.accept(throwable);
            }

            if (location == CRYPTOGRAPHY && type == ClientType.MOBILE) {
                logger.log(WARNING, "Reconnecting");
                return Result.RECONNECT;
            }

            if (location == INITIAL_APP_STATE_SYNC
                    || location == CRYPTOGRAPHY
                    || (location == MESSAGE && throwable instanceof HmacValidationException)) {
                logger.log(WARNING, "Socket failure at %s".formatted(location));
                return Result.RESTORE;
            }

            logger.log(WARNING, "Ignored failure");
            return Result.DISCARD;
        };
    }

    /**
     * The constants of this enumerated type describe the various locations where an error can occur
     * in the socket
     */
    enum Location {
        /**
         * Unknown
         */
        UNKNOWN,
        /**
         * Called when an error is thrown while logging in
         */
        LOGIN,
        /**
         * Cryptographic error
         */
        CRYPTOGRAPHY,
        /**
         * Called when the media connection cannot be renewed
         */
        MEDIA_CONNECTION,
        /**
         * Called when an error arrives from the stream
         */
        STREAM,
        /**
         * Called when an error is thrown while pulling app data
         */
        PULL_APP_STATE,
        /**
         * Called when an error is thrown while pushing app data
         */
        PUSH_APP_STATE,
        /**
         * Called when an error is thrown while pulling initial app data
         */
        INITIAL_APP_STATE_SYNC,
        /**
         * Called when an error occurs when serializing or deserializing a Whatsapp message
         */
        MESSAGE,
        /**
         * Called when syncing messages after first QR scan
         */
        HISTORY_SYNC
    }

    /**
     * The constants of this enumerated type describe the various types of actions that can be
     * performed by an error handler in newsletters to a throwable
     */
    enum Result {
        /**
         * Ignores an error that was thrown by the socket
         */
        DISCARD,
        /**
         * Deletes the current session and creates a new one instantly
         */
        RESTORE,
        /**
         * Disconnects from the current session without deleting it
         */
        DISCONNECT,
        /**
         * Disconnects from the current session without deleting it and reconnects to it
         */
        RECONNECT,
        /**
         * Deletes the current session
         */
        LOG_OUT
    }
}