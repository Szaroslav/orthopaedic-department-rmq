package pl.edu.agh.orthoment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

import lombok.NonNull;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;


public class Technician {
    private static final String NAME = "Technician";
    private static String messageName;
    private static final List<String> qualifications = new ArrayList<>();
    private static Logger logger;

    public static void main(
        String[] args
    ) throws IOException, TimeoutException {
        // CLI arguments: id test_0 [test_1, …]
        if (args.length < 2) {
            throw new IllegalArgumentException(NAME + " requires 2 arguments");
        }

        final int id = Integer.parseInt(args[0]);
        messageName = Utility.toMessageName(NAME, id);
        for (int i = 1; i < args.length; i++) {
            final String arg = args[i];
            final boolean isValidTestType = arg.equals("hip")
                || arg.equals("knee")
                || arg.equals("elbow");
            if (isValidTestType) {
                qualifications.add(arg);
            }
        }

        logger = new Logger(NAME + " " + id);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();

        Channel channel = connection.createChannel();
        channel.exchangeDeclare(
            Configuration.EXAMINATION_EXCHANGE,
            BuiltinExchangeType.DIRECT
        );

        for (String qualification : qualifications) {
            String queueName = bindQueue(qualification, channel);
            initRequestHandler(queueName, channel);
        }

        logger.log(
            "Listening test requests: " + String.join(", ", qualifications)
        );
    }

    private static String bindQueue(
        @NonNull String routingKey,
        @NonNull Channel channel
    ) throws IOException {
        String queueName = channel.queueDeclare(
                Utility.capitalizeFirst(routingKey),
                false,
                false,
                false,
                null)
            .getQueue();
        channel.queueBind(
            queueName,
            Configuration.EXAMINATION_EXCHANGE,
            routingKey
        );

        return queueName;
    }

    private static void initRequestHandler(
        @NonNull String queueName,
        @NonNull Channel channel
    ) throws IOException {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            final String message = new String(
                delivery.getBody(),
                StandardCharsets.UTF_8
            );
            final String[] messageParts = message.split(":");
            final String testType = messageParts[0],
                         doctor = messageParts[1],
                         patientFullName = messageParts[3];

            logger.log(String.format(
                "Received %s request for %s",
                testType,
                patientFullName
            ));

            try {
                final int sleepMsecs = ThreadLocalRandom
                    .current()
                    .nextInt(2000, 5001);
                Thread.sleep(sleepMsecs);

                sendTestResults(testType, doctor, patientFullName, channel);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.log(String.format(
                "Processed %s request for %s",
                testType,
                patientFullName
            ));
        };

        channel.basicConsume(
            queueName,
            true,
            deliverCallback,
            consumerTag -> {}
        );
    }

    private static void sendTestResults(
        @NonNull String testType,
        @NonNull String doctor,
        @NonNull String patientFullName,
        @NonNull Channel channel
    ) throws IOException {
        byte[] message = Utility.buildMessage(
            testType,
            messageName,
            doctor,
            patientFullName
        );

        channel.basicPublish(
            Configuration.EXAMINATION_EXCHANGE,
            doctor,
            null,
            message
        );
    }
}
