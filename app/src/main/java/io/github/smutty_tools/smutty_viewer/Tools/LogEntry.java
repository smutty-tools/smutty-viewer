package io.github.smutty_tools.smutty_viewer.Tools;

public class LogEntry {

    private int level;
    private String message;

    public LogEntry(int level, String message) {
        this.level = level;
        this.message = message;
    }

    public int getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
