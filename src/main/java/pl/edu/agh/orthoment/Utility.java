package pl.edu.agh.orthoment;

import lombok.NonNull;

public class Utility {
    public static String capitalizeFirst(@NonNull String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static String toMessageName(@NonNull String name, int id) {
        return name.toLowerCase() + "_" + id;
    }

    public static String toHumanName(@NonNull String messageName) {
        final String[] messageParts = messageName.split("_");
        final String name = capitalizeFirst(messageParts[0]);
        final int id = Integer.parseInt(messageParts[1]);
        return name + " " + id;
    }

    public static byte[] buildMessage(
        String testType,
        @NonNull String sender,
        String receiver,
        String message
    ) {
        if (testType == null) {
            testType = "";
        }
        if (receiver == null) {
            receiver = "";
        }
        if (message == null) {
            message = "";
        }

        return String.format(
                "%s:%s:%s:%s",
                testType,
                sender,
                receiver,
                message)
            .getBytes();
    }
}
