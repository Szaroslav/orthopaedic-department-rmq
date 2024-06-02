package pl.edu.agh.orthoment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

import lombok.NonNull;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;


public class Doctor {
    private static final String NAME = "Doctor";
    private static String messageName;
    private static Logger logger;

    public static void main(
        String[] args
    ) throws IOException, TimeoutException {
        // CLI arguments: id
        if (args.length < 1) {
            throw new IllegalArgumentException(NAME + " requires 1 argument");
        }

        final int id = Integer.parseInt(args[0]);
        messageName = Utility.toMessageName(NAME, id);

        logger = new Logger(NAME + " " + id);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel();
             Scanner scanner = new Scanner(System.in);
        ) {
            channel.basicQos(1);
            channel.exchangeDeclare(
                Configuration.EXAMINATION_EXCHANGE,
                BuiltinExchangeType.DIRECT
            );

            initResponseHandler(channel);
            handleInput(scanner, channel);

            System.out.println("See you next time! :)");
        }
    }

    private static void initResponseHandler(
        @NonNull Channel channel
    ) throws IOException {
        String queueName = channel.queueDeclare()
            .getQueue();
        channel.queueBind(
            queueName,
            Configuration.EXAMINATION_EXCHANGE,
            messageName
        );
        channel.queueBind(
            queueName,
            Configuration.ADMINISTRATION_EXCHANGE,
            messageName
        );

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            final String message = new String(
                delivery.getBody(),
                StandardCharsets.UTF_8
            );
            final String[] messageParts = message.split(":");
            final String testType = messageParts[0],
                         sender = messageParts[1],
                         body = messageParts[3];

            boolean unknownMessage = false;
            String messageLog = null;
            if (sender.matches("technician_\\d+")) {
                messageLog = String.format(
                    "Received response of %s request for patient %s",
                    testType,
                    body
                );
            }
            else if (sender.matches("administrator_\\d+")) {
                messageLog = String.format(
                    "Received info message from %s: %s",
                    Utility.toHumanName(sender),
                    body
                );
            }
            else {
                unknownMessage = true;
                messageLog = "Unknown message: " + message;
            }

            if (unknownMessage) {
                logger.warn(messageLog);
            }
            else {
                logger.logWithInput(messageLog);
            }
        };

        channel.basicConsume(
            queueName,
            true,
            deliverCallback,
            consumerTag -> {}
        );
    }

    private static void handleInput(
        @NonNull Scanner scanner,
        @NonNull Channel channel
    ) throws IOException {
        while (true) {
            System.out.print(Logger.INPUT_MESSAGE + " ");
            String command = scanner.nextLine();
            if (command.equalsIgnoreCase("exit")) {
                break;
            }

            if (command.contains(":")) {
                logger.warn("Invalid command, colon is forbidden");
                continue;
            }

            String[] cargs = command.split(" ");
            if (cargs.length < 3) {
                logger.warn("Invalid command, requires 3 arguments");
                continue;
            }
            final String testType = cargs[0],
                         patientFullName = cargs[1] + " " + cargs[2];

            final boolean isValidTestType = testType.equals("hip")
                || testType.equals("knee")
                || testType.equals("elbow");
            if (!isValidTestType) {
                logger.warn(
                    "Invalid type of test, needs to one of hip, knee, elbow");
                continue;
            }

            requestTest(patientFullName, testType, channel);
        }
    }

    private static void requestTest(
        @NonNull String fullName,
        @NonNull String testType,
        @NonNull Channel channel
    ) throws IOException {
        byte[] message = Utility.buildMessage(
            testType, messageName, null, fullName
        );

        channel.basicPublish(
            Configuration.EXAMINATION_EXCHANGE,
            testType,
            null,
            message
        );
        logger.log(String.format(
            "Requested %s test for %s",
            testType,
            fullName
        ));

        channel.basicPublish(
            Configuration.ADMINISTRATION_EXCHANGE,
            Configuration.ADMINISTRATION_KEY,
            null,
            message
        );
    }
}
