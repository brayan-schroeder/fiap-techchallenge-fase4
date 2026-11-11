
package br.com.fiap.report;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class WeeklyReportHandler
        implements RequestHandler<Map<String, Object>, Void> {

    private static final String TABLE_NAME = "fiap-feedbacks";

    private static final String INDEX_NAME =
            "urgencia-dataEnvio-index";

    private static final String SENDER_EMAIL =
            System.getenv("SENDER_EMAIL");

    private static final String ADMIN_EMAIL =
            System.getenv("ADMIN_EMAIL");

    private static final ZoneId TIME_ZONE =
            ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<String> URGENCIAS =
            List.of("CRITICA", "MEDIA", "BAIXA");

    private static final Region REGION =
            Region.of(System.getenv("AWS_REGION"));

    private static final DynamoDbClient DYNAMODB =
            DynamoDbClient.builder()
                    .region(REGION)
                    .httpClientBuilder(
                            UrlConnectionHttpClient.builder()
                    )
                    .build();

    private static final SesV2Client SES =
            SesV2Client.builder()
                    .region(REGION)
                    .httpClientBuilder(
                            UrlConnectionHttpClient.builder()
                    )
                    .build();

    @Override
    public Void handleRequest(
            Map<String, Object> event,
            Context context
    ) {

        context.getLogger().log(
                "Iniciando geração do relatório semanal\n"
        );

        Instant end = Instant.now();
        Instant start = end.minusSeconds(7 * 24 * 60 * 60);

        Map<String, Integer> urgencyCounts =
                new LinkedHashMap<>();

        Map<LocalDate, Integer> dailyCounts =
                new TreeMap<>();

        URGENCIAS.forEach(
                urgency -> urgencyCounts.put(urgency, 0)
        );

        LocalDate firstDay =
                start.atZone(TIME_ZONE).toLocalDate();

        LocalDate lastDay =
                end.atZone(TIME_ZONE).toLocalDate();

        for (LocalDate day = firstDay;
             !day.isAfter(lastDay);
             day = day.plusDays(1)) {

            dailyCounts.put(day, 0);
        }

        long totalRatings = 0;
        long totalScore = 0;

        for (String urgency : URGENCIAS) {

            QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName(INDEX_NAME)
                    .keyConditionExpression(
                            "#urgencia = :urgencia AND "
                                    + "#data BETWEEN :inicio AND :fim"
                    )
                    .expressionAttributeNames(Map.of(
                            "#urgencia", "urgencia",
                            "#data", "dataEnvioEpochMillis"
                    ))
                    .expressionAttributeValues(Map.of(
                            ":urgencia",
                            AttributeValue.builder()
                                    .s(urgency)
                                    .build(),
                            ":inicio",
                            AttributeValue.builder()
                                    .n(String.valueOf(
                                            start.toEpochMilli()
                                    ))
                                    .build(),
                            ":fim",
                            AttributeValue.builder()
                                    .n(String.valueOf(
                                            end.toEpochMilli()
                                    ))
                                    .build()
                    ))
                    .build();

            for (QueryResponse response :
                    DYNAMODB.queryPaginator(request)) {

                for (Map<String, AttributeValue> item :
                        response.items()) {

                    int nota = Integer.parseInt(
                            item.get("nota").n()
                    );

                    long timestamp = Long.parseLong(
                            item.get("dataEnvioEpochMillis").n()
                    );

                    LocalDate date = Instant
                            .ofEpochMilli(timestamp)
                            .atZone(TIME_ZONE)
                            .toLocalDate();

                    urgencyCounts.merge(
                            urgency, 1, Integer::sum
                    );

                    dailyCounts.merge(
                            date, 1, Integer::sum
                    );

                    totalRatings++;
                    totalScore += nota;
                }
            }
        }

        BigDecimal average = totalRatings == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalScore)
                        .divide(
                                BigDecimal.valueOf(totalRatings),
                                2,
                                RoundingMode.HALF_UP
                        );

        String report = buildReport(
                start,
                end,
                totalRatings,
                average,
                urgencyCounts,
                dailyCounts
        );

        sendEmail(report);

        context.getLogger().log(
                "Relatório semanal enviado com sucesso. "
                        + "Total de avaliações: "
                        + totalRatings + "\n"
        );

        return null;
    }

    private String buildReport(
            Instant start,
            Instant end,
            long totalRatings,
            BigDecimal average,
            Map<String, Integer> urgencyCounts,
            Map<LocalDate, Integer> dailyCounts
    ) {

        StringBuilder report = new StringBuilder();

        report.append("""
                RELATÓRIO SEMANAL DE AVALIAÇÕES
                =================================

                Período: %s até %s

                RESUMO GERAL
                ---------------------------------
                Total de avaliações: %d
                Média das notas: %s

                DISTRIBUIÇÃO POR URGÊNCIA
                ---------------------------------
                """.formatted(
                DATE_FORMAT.format(start.atZone(TIME_ZONE)),
                DATE_FORMAT.format(end.atZone(TIME_ZONE)),
                totalRatings,
                average.toPlainString()
        ));

        urgencyCounts.forEach((urgency, count) ->
                report.append(
                        "%s: %d\n".formatted(urgency, count)
                )
        );

        report.append("""

                AVALIAÇÕES POR DIA
                ---------------------------------
                """);

        dailyCounts.forEach((date, count) ->
                report.append(
                        "%s: %d\n".formatted(
                                date.format(DATE_FORMAT),
                                count
                        )
                )
        );

        report.append("""

                =================================
                Relatório gerado automaticamente
                pela plataforma FIAP Tech Challenge.
                """);

        return report.toString();
    }

    private void sendEmail(String report) {

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(SENDER_EMAIL)
                .destination(
                        Destination.builder()
                                .toAddresses(ADMIN_EMAIL)
                                .build()
                )
                .content(
                        EmailContent.builder()
                                .simple(
                                        Message.builder()
                                                .subject(
                                                        Content.builder()
                                                                .data(
                                                                        "Relatório semanal de avaliações"
                                                                )
                                                                .charset("UTF-8")
                                                                .build()
                                                )
                                                .body(
                                                        Body.builder()
                                                                .text(
                                                                        Content.builder()
                                                                                .data(report)
                                                                                .charset("UTF-8")
                                                                                .build()
                                                                )
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        SES.sendEmail(request);
    }
}
