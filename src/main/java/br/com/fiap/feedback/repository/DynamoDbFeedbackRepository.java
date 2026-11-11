
package br.com.fiap.feedback.repository;

import br.com.fiap.feedback.enums.Urgencia;
import br.com.fiap.feedback.model.Feedback;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DynamoDbFeedbackRepository implements FeedbackRepository {

    private static final String TABLE_NAME = "fiap-feedbacks";

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbFeedbackRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Feedback salvar(Feedback feedback) {

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("id", AttributeValue.builder()
                .s(feedback.id().toString()).build());

        item.put("descricao", AttributeValue.builder()
                .s(feedback.descricao()).build());

        item.put("nota", AttributeValue.builder()
                .n(feedback.nota().toString()).build());

        item.put("urgencia", AttributeValue.builder()
                .s(feedback.urgencia().name()).build());

        item.put("dataEnvio", AttributeValue.builder()
                .s(feedback.dataEnvio().toString()).build());

        item.put("dataEnvioEpochMillis", AttributeValue.builder()
                .n(feedback.dataEnvioEpochMillis().toString()).build());

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());

        return feedback;
    }

    @Override
    public List<Feedback> listarTodos() {

        return dynamoDbClient.scanPaginator(
                ScanRequest.builder()
                        .tableName(TABLE_NAME)
                        .build()
        ).items().stream()
                .map(this::toFeedback)
                .toList();
    }

    private Feedback toFeedback(Map<String, AttributeValue> item) {

        return new Feedback(
                UUID.fromString(item.get("id").s()),
                item.get("descricao").s(),
                Integer.valueOf(item.get("nota").n()),
                Urgencia.valueOf(item.get("urgencia").s()),
                Instant.parse(item.get("dataEnvio").s()),
                Long.valueOf(item.get("dataEnvioEpochMillis").n())
        );
    }
}
