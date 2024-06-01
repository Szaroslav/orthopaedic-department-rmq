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
    private static Logger logger;
    private static int id;

    public static void main(
        String[] args
    ) throws IOException, TimeoutException {
        // CLI arguments: id
        if (args.length < 1) {
            throw new IllegalArgumentException("Technician requires 1 argument");
        }

        id = Integer.parseInt(args[0]);
        logger = new Logger("Doctor " + id);

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

    private static void initResponseHandler(Channel channel) throws IOException {
        String queueName = channel.queueDeclare()
            .getQueue();
        channel.queueBind(
            queueName,
            Configuration.EXAMINATION_EXCHANGE,
            "doctor_" + id
        );

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            final String message = new String(
                delivery.getBody(),
                StandardCharsets.UTF_8
            );
            final String[] messageParts = message.split(":");
            final String patient = messageParts[0],
                         testType = messageParts[1];

            logger.logWithInput(String.format(
                "Received response of %s request for patient %s",
                testType,
                patient
            ));
        };

        channel.basicConsume(
            queueName,
            true,
            deliverCallback,
            consumerTag -> {}
        );
    }

    private static void handleInput(
        Scanner scanner,
        Channel channel
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
            final String patientFullName = cargs[0] + " " + cargs[1],
                         testType = cargs[2];

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
        @NonNull String test,
        @NonNull Channel channel
    ) throws IOException {
        channel.basicPublish(
            Configuration.EXAMINATION_EXCHANGE,
            test,
            null,
            ("doctor_" + id + ":" + fullName).getBytes(StandardCharsets.UTF_8)
        );
        logger.log(String.format(
            "Requested %s test for %s",
            test,
            fullName
        ));
    }
}
