package com.ekszz.ezzpatcher;

import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;

public class Log {

    static boolean color;

    public static java.util.logging.Level LEVEL = java.util.logging.Level.CONFIG;

    private static final String RESET = "\033[0m";

    private static final int BLACK = 30;
    private static final int RED = 31;
    private static final int GREEN = 32;
    private static final int YELLOW = 33;
    private static final int BLUE = 34;
    private static final int MAGENTA = 35;
    private static final int CYAN = 36;
    private static final int WHITE = 37;

    private static final String TRACE_PREFIX = "[T]";
    private static final String TRACE_COLOR_PREFIX = colorStr("[T]", GREEN);

    private static final String DEBUG_PREFIX = "[D]";
    private static final String DEBUG_COLOR_PREFIX = colorStr("[D]", GREEN);

    private static final String INFO_PREFIX = "[I]";
    private static final String INFO_COLOR_PREFIX = colorStr("[I]", GREEN);

    private static final String WARN_PREFIX = "[W]";
    private static final String WARN_COLOR_PREFIX = colorStr("[W]", YELLOW);

    private static final String ERROR_PREFIX = "[E]";
    private static final String ERROR_COLOR_PREFIX = colorStr("[E]", RED);

    static {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (System.console() != null) {
            color = !osName.startsWith("windows");
        }
        if (osName.startsWith("windows")
                && ((System.getenv("MSYSTEM") != null && System.getenv("MSYSTEM").startsWith("MINGW"))
                || "/bin/bash".equals(System.getenv("SHELL")))) {
            color = true;
        }
    }

    private Log() {
    }

    public static String black(String msg) {
        if (color) {
            return colorStr(msg, BLACK);
        } else {
            return msg;
        }
    }

    public static String red(String msg) {
        if (color) {
            return colorStr(msg, RED);
        } else {
            return msg;
        }
    }

    public static String green(String msg) {
        if (color) {
            return colorStr(msg, GREEN);
        } else {
            return msg;
        }
    }

    public static String yellow(String msg) {
        if (color) {
            return colorStr(msg, YELLOW);
        } else {
            return msg;
        }
    }

    public static String blue(String msg) {
        if (color) {
            return colorStr(msg, BLUE);
        } else {
            return msg;
        }
    }

    public static String magenta(String msg) {
        if (color) {
            return colorStr(msg, MAGENTA);
        } else {
            return msg;
        }
    }

    public static String cyan(String msg) {
        if (color) {
            return colorStr(msg, CYAN);
        } else {
            return msg;
        }
    }

    public static String white(String msg) {
        if (color) {
            return colorStr(msg, WHITE);
        } else {
            return msg;
        }
    }

    private static String colorStr(String msg, int colorCode) {
        return "\033[" + colorCode + "m" + msg + RESET;
    }

    public static void trace(String msg) {
        if (canLog(Level.FINEST)) {
            if (color) {
                System.out.println(TRACE_COLOR_PREFIX + msg);
            } else {
                System.out.println(TRACE_PREFIX + msg);
            }
        }
    }

    public static void trace(String format, Object... arguments) {
        if (canLog(Level.FINEST)) {
            trace(format(format, arguments));
        }
    }

    public static void trace(Throwable t) {
        if (canLog(Level.FINEST)) {
            t.printStackTrace(System.out);
        }
    }

    public static void debug(String msg) {
        if (canLog(Level.FINER)) {
            if (color) {
                System.out.println(DEBUG_COLOR_PREFIX + msg);
            } else {
                System.out.println(DEBUG_PREFIX + msg);
            }
        }
    }

    public static void debug(String format, Object... arguments) {
        if (canLog(Level.FINER)) {
            debug(format(format, arguments));
        }
    }

    public static void debug(Throwable t) {
        if (canLog(Level.FINER)) {
            t.printStackTrace(System.out);
        }
    }

    public static void info(String msg) {
        if (canLog(Level.CONFIG)) {
            if (color) {
                System.out.println(INFO_COLOR_PREFIX + msg);
            } else {
                System.out.println(INFO_PREFIX + msg);
            }
        }
    }

    public static void info(String format, Object... arguments) {
        if (canLog(Level.CONFIG)) {
            info(format(format, arguments));
        }
    }

    public static void info(Throwable t) {
        if (canLog(Level.CONFIG)) {
            t.printStackTrace(System.out);
        }
    }

    public static void warn(String msg) {
        if (canLog(Level.WARNING)) {
            if (color) {
                System.out.println(WARN_COLOR_PREFIX + msg);
            } else {
                System.out.println(WARN_PREFIX + msg);
            }
        }
    }

    public static void warn(String format, Object... arguments) {
        if (canLog(Level.WARNING)) {
            warn(format(format, arguments));
        }
    }

    public static void warn(Throwable t) {
        if (canLog(Level.WARNING)) {
            t.printStackTrace(System.out);
        }
    }

    public static void error(String msg) {
        if (canLog(Level.SEVERE)) {
            if (color) {
                System.out.println(ERROR_COLOR_PREFIX + msg);
            } else {
                System.out.println(ERROR_PREFIX + msg);
            }
        }
    }

    public static void error(String format, Object... arguments) {
        if (canLog(Level.SEVERE)) {
            error(format(format, arguments));
        }
    }

    public static void error(Throwable t) {
        if (canLog(Level.SEVERE)) {
            t.printStackTrace(System.out);
        }
    }

    private static String format(String from, Object... arguments) {
        if (from != null) {
            String computed = from;
            if (arguments != null) {
                for (Object argument : arguments) {
                    computed = computed.replaceFirst("\\{\\}",
                            Matcher.quoteReplacement(String.valueOf(argument)));
                }
            }
            return computed;
        }
        return null;
    }

    private static boolean canLog(Level level) {
        return level.intValue() >= LEVEL.intValue();
    }

    public static void fromString(String level) {
        switch (level) {
            case "DEBUG":
                LEVEL = Level.FINER;
                break;
            case "INFO":
                LEVEL = Level.CONFIG;
                break;
            case "WARNING":
                LEVEL = Level.WARNING;
                break;
            case "ERROR":
                LEVEL = Level.SEVERE;
                break;
            case "NONE":
                LEVEL = Level.OFF;
                break;
            default:
        }
    }
}