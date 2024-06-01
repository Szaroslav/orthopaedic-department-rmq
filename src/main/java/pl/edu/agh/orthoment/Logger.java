package pl.edu.agh.orthoment;

import lombok.NonNull;

public class Logger {
    public final static String HEADER_COLOR = "\33[1;94m";
    public final static String WARN_COLOR = "\33[93m";
    public final static String WHITE_BOLD = "\33[1;97m";
    public final static String RESET = "\33[0m";
    public final static String CLEAR_LINE = "\33[2K\r";
    public final static String INPUT_MESSAGE = WHITE_BOLD + ">>>" + RESET;
    public final String header;

    public Logger(@NonNull String header) {
        this.header = header;
    }

    public void log(@NonNull String message) {
        String messageLine = String.format(
            "%s[%s]%s %s", HEADER_COLOR, header, RESET, message
        );
        System.out.println(messageLine);
    }

    public void logWithInput(@NonNull String message) {
        String messageLine = String.format(
                "%s%s[%s]%s %s", CLEAR_LINE, HEADER_COLOR, header, RESET, message
        );
        System.out.println(messageLine);
        System.out.print(INPUT_MESSAGE + " ");
    }

    public void warn(@NonNull String message) {
        String messageLine = String.format(
            "%s%s%s", WARN_COLOR, message, RESET
        );
        System.out.println(messageLine);
    }
}
