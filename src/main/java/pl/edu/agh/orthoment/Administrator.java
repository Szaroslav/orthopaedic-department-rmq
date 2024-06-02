package pl.edu.agh.orthoment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

import lombok.NonNull;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;


public class Administrator {
    private static final String NAME = "Administrator";
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
                Configuration.ADMINISTRATION_EXCHANGE,
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
        String queueName = channel.queueDeclare(
            "Administration",
            false,
            false,
            false,
            null
        ).getQueue();
        channel.queueBind(
            queueName,
            Configuration.ADMINISTRATION_EXCHANGE,
            "administration"
        );

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            final String message = new String(
                delivery.getBody(),
                StandardCharsets.UTF_8
            );
            final String[] messageParts = message.split(":");
            final String testType = messageParts[0],
                         sender = messageParts[1],
                         receiver = messageParts[2],
                         body = messageParts[3];

            boolean unknownMessage = false;
            String messageLog = null;
            if (sender.matches("doctor_\\d+")) {
                messageLog = String.format(
                    "Received %s request " +
                        "ordered by %s " +
                        "for patient %s",
                    testType,
                    Utility.toHumanName(sender),
                    body
                );
            }
            else if (sender.matches("technician_\\d+")) {
                messageLog = String.format(
                    "Received processed %s request " +
                        "from %s " +
                        "ordered by %s " +
                        "for patient %s",
                    testType,
                    Utility.toHumanName(sender),
                    Utility.toHumanName(receiver),
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
            if (cargs.length < 2) {
                logger.warn("Invalid command, requires 2 arguments");
                continue;
            }
            final String receiver = cargs[0].toLowerCase();
            final String message = String.join(
                " ",
                Arrays.copyOfRange(cargs, 1, cargs.length)
            );

            final boolean isValidReceiver = receiver.matches("doctor_\\d+")
                || receiver.matches("technician_\\d+")
                || receiver.equals("all");
            if (!isValidReceiver) {
                logger.warn(
                    "Invalid receiver name (" + receiver + ")");
                continue;
            }

            info(message, receiver, channel);
        }
    }

    private static void info(
        @NonNull String messageBody,
        @NonNull String receiver,
        @NonNull Channel channel
    ) throws IOException {
        final List<String> receivers = new ArrayList<>();
        if (receiver.equals("all")) {
            receivers.add(Configuration.DOCTOR_0_KEY);
            receivers.add(Configuration.DOCTOR_1_KEY);
            receivers.add(Configuration.TECHNICIAN_0_KEY);
            receivers.add(Configuration.TECHNICIAN_1_KEY);
        }
        else {
            receivers.add(receiver);
        }

        for (String r : receivers) {
            byte[] message = Utility.buildMessage(
                null,
                messageName,
                r,
                messageBody
            );
            channel.basicPublish(
                Configuration.ADMINISTRATION_EXCHANGE,
                r,
                null,
                message
            );

            logger.log(String.format(
                "Sent info to %s",
                Utility.toHumanName(r)
            ));
        }

    }
}
