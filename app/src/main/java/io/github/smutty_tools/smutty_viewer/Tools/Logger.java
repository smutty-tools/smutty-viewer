package io.github.smutty_tools.smutty_viewer.Tools;

public interface Logger {

    interface Level {
        int CRITICAL = 0;
        int ERROR = 1;
        int WARNING = 2;
        int INFO = 3;
        int DEBUG = 4;
    }

    void log(int level, Object... objects);
    void debug(Object... objects);
    void info(Object... objects);
    void warning(Object... objects);
    void error(Object... objects);
    void critical(Object... objects);
}
