
package br.com.fiap.feedback.controller;

import br.com.fiap.feedback.dto.FeedbackRequest;
import br.com.fiap.feedback.model.Feedback;
import br.com.fiap.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/avaliacoes")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Feedback criar(@Valid @RequestBody FeedbackRequest request) {
        return service.criar(request);
    }

    @GetMapping
    public List<Feedback> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/teste-erro-500")
    public void testarErro500() {
        throw new RuntimeException("Erro 500 proposital para teste do CloudWatch");
    }
}
