
package br.com.fiap.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;

import java.util.Map;

public class StreamHandler implements RequestHandler<DynamodbEvent, Void> {

    private static final String SENDER_EMAIL =
            System.getenv("SENDER_EMAIL");

    private static final String ADMIN_EMAIL =
            System.getenv("ADMIN_EMAIL");

    private static final SesV2Client SES_CLIENT =
            SesV2Client.builder()
                    .region(Region.of(System.getenv("AWS_REGION")))
                    .build();

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {

        context.getLogger().log(
                "Processando eventos do DynamoDB Streams\n"
        );

        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {

            if (!"INSERT".equals(record.getEventName())) {
                continue;
            }

            Map<String, AttributeValue> image =
                    record.getDynamodb().getNewImage();

            if (image == null || image.isEmpty()) {
                continue;
            }

            String urgencia = getString(image, "urgencia");

            if (!"CRITICA".equals(urgencia)) {
                context.getLogger().log(
                        "Feedback ignorado. Urgencia: " + urgencia + "\n"
                );
                continue;
            }

            enviarNotificacao(image, context);
        }

        return null;
    }

    private void enviarNotificacao(
            Map<String, AttributeValue> image,
            Context context
    ) {

        String descricao = getString(image, "descricao");
        String dataEnvio = getString(image, "dataEnvio");
        String nota = getNumber(image, "nota");

        String assunto = "Alerta: feedback crítico recebido";

        String mensagem = """
                Um novo feedback crítico foi recebido.

                Descrição: %s

                Urgência: CRITICA

                Nota: %s

                Data de envio: %s

                Acesse a plataforma para analisar o feedback.
                """.formatted(descricao, nota, dataEnvio);

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(SENDER_EMAIL)
                .destination(Destination.builder()
                        .toAddresses(ADMIN_EMAIL)
                        .build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder()
                                        .data(assunto)
                                        .charset("UTF-8")
                                        .build())
                                .body(Body.builder()
                                        .text(Content.builder()
                                                .data(mensagem)
                                                .charset("UTF-8")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        SES_CLIENT.sendEmail(request);

        context.getLogger().log(
                "Notificação crítica enviada para: "
                        + ADMIN_EMAIL + "\n"
        );
    }

    private String getString(
            Map<String, AttributeValue> image,
            String attribute
    ) {
        AttributeValue value = image.get(attribute);

        return value != null ? value.getS() : "";
    }

    private String getNumber(
            Map<String, AttributeValue> image,
            String attribute
    ) {
        AttributeValue value = image.get(attribute);

        return value != null ? value.getN() : "";
    }
}
