package pl.edu.agh.orthoment;

import com.rabbitmq.client.*;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

public class Technician {
    private static Logger logger;
    private static final List<String> qualifications = new ArrayList<>();

    public static void main(
        String[] args
    ) throws IOException, TimeoutException {
        // CLI arguments: id test_0 [test_1, …]
        if (args.length < 2) {
            throw new IllegalArgumentException("Technician requires 2 arguments");
        }

        final int id = Integer.parseInt(args[0]);
        for (int i = 1; i < args.length; i++) {
            final String arg = args[i];
            final boolean isValidTest = arg.equals("hip")
                || arg.equals("knee")
                || arg.equals("elbow");
            if (isValidTest) {
                qualifications.add(arg);
            }
        }

        logger = new Logger("Technician " + id);

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
            initRequestHandler(qualification, queueName, channel);
        }
    }
    
    private static String bindQueue(
        @NonNull String routingKey,
        @NonNull Channel channel
    ) throws IOException {
        String queueName = channel.queueDeclare(
            routingKey.substring(0, 1).toUpperCase() +
                routingKey.substring(1).toLowerCase(),
            false,
            false,
            false,
            null
        ).getQueue();
        channel.queueBind(
            queueName,
            Configuration.EXAMINATION_EXCHANGE,
            routingKey
        );

        return queueName;
    }

    private static void initRequestHandler(
        @NonNull String qualification,
        @NonNull String queueName,
        @NonNull Channel channel
    ) throws IOException {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            final String message = new String(
                delivery.getBody(),
                StandardCharsets.UTF_8
            );
            final String[] messageParts = message.split(":");
            final String doctor = messageParts[0],
                         patientFullName = messageParts[1];

            logger.log(String.format(
                "Received %s request for %s",
                qualification,
                patientFullName
            ));

            try {
                final int sleepMsecs = ThreadLocalRandom
                    .current()
                    .nextInt(2000, 5001);
                Thread.sleep(sleepMsecs);

                channel.basicPublish(
                    Configuration.EXAMINATION_EXCHANGE,
                    doctor,
                    null,
                    (patientFullName + ":" + qualification).getBytes()
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.log(String.format(
                "Processed %s request for %s",
                qualification,
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
}
