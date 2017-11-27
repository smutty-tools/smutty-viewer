package io.github.smutty_tools.smutty_viewer.Tools;

public interface Logger {
    void debug(Object... objects);
    void info(Object... objects);
    void warning(Object... objects);
    void error(Object... objects);
    void critical(Object... objects);
}
