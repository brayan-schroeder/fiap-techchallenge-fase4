
package br.com.fiap.feedback.model;

import br.com.fiap.feedback.enums.Urgencia;

import java.time.Instant;
import java.util.UUID;

public record Feedback(
        UUID id,
        String descricao,
        Integer nota,
        Urgencia urgencia,
        Instant dataEnvio,
        Long dataEnvioEpochMillis
) {}
