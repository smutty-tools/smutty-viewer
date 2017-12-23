package io.github.smutty_tools.smutty_viewer.Tools;

public class LogEntry {

    public final int level;
    public final String message;

    public LogEntry(int level, String message) {
        this.level = level;
        this.message = message;
    }
}
