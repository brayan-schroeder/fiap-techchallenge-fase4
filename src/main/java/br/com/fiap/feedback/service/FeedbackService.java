
package br.com.fiap.feedback.service;

import br.com.fiap.feedback.dto.FeedbackRequest;
import br.com.fiap.feedback.enums.Urgencia;
import br.com.fiap.feedback.model.Feedback;
import br.com.fiap.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FeedbackService {

    private final FeedbackRepository repository;

    public FeedbackService(FeedbackRepository repository) {
        this.repository = repository;
    }


    public Feedback criar(FeedbackRequest request) {

        Urgencia urgencia = classificarUrgencia(request.nota());

        Instant dataEnvio = Instant.now();

        Feedback feedback = new Feedback(
                UUID.randomUUID(),
                request.descricao(),
                request.nota(),
                urgencia,
                dataEnvio,
                dataEnvio.toEpochMilli()
        );

        return repository.salvar(feedback);
    }

    public List<Feedback> listarTodos() {
        return repository.listarTodos();
    }

    private Urgencia classificarUrgencia(Integer nota) {

        if (nota <= 4) {
            return Urgencia.CRITICA;
        }

        if (nota <= 6) {
            return Urgencia.MEDIA;
        }

        return Urgencia.BAIXA;
    }
}
