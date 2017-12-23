package io.github.smutty_tools.smutty_viewer.Tools;

public class LogProgressBundle {

    public final LogEntry logEntry;
    public final int progress;
    public final int maxProgress;

    public LogProgressBundle(LogEntry logEntry, int progress, int maxProgress) {
        this.logEntry = logEntry;
        this.progress = progress;
        this.maxProgress = maxProgress;
    }
}
