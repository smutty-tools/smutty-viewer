package io.github.smutty_tools.smutty_viewer.Exceptions;

public class SmuttyException extends Exception {

    public SmuttyException() {
    }

    public SmuttyException(String message) {
        super(message);
    }

    public SmuttyException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmuttyException(Throwable cause) {
        super(cause);
    }
}
