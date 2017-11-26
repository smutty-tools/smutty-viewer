package io.github.smutty_tools.smutty_viewer;

import android.widget.TextView;

import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.util.Date;

public class UiLogger {

    interface LogLevels {
        int CRITICAL = 0;
        int ERROR = 1;
        int WARNING = 2;
        int INFO = 3;
        int DEBUG = 4;
    }

    private static final String LEVELS[] = {
            "CRITICAL",
            "ERROR",
            "WARNING",
            "INFO",
            "DEBUG"
    };

    private TextView textViewLogger;
    private StringBuilder stringBuilder;
    private DateFormat dateFormat;

    public UiLogger(TextView textViewLogger) {
        this.textViewLogger = textViewLogger;
        this.stringBuilder = new StringBuilder();
        this.dateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
    }

    public void clear() {
        textViewLogger.setText("");
    }

    public void log(int level, String message) {
        if (level < LogLevels.CRITICAL || level > LogLevels.DEBUG) {
            throw new InvalidParameterException("Log level is outside allowed range");
        }
        Date now = new Date();
        stringBuilder.setLength(0);
        stringBuilder.append(dateFormat.format(now));
        stringBuilder.append(" ");
        stringBuilder.append(LEVELS[level]);
        stringBuilder.append(" ");
        stringBuilder.append(message);
        stringBuilder.append("\n");
        textViewLogger.append(stringBuilder.toString());
    }

    public void critical(String message) {
        log(LogLevels.CRITICAL, message);
    }

    public void error(String message) {
        log(LogLevels.ERROR, message);
    }

    public void warning(String message) {
        log(LogLevels.WARNING, message);
    }

    public void info(String message) {
        log(LogLevels.INFO, message);
    }

    public void debug(String message) {
        log(LogLevels.DEBUG, message);
    }
}
