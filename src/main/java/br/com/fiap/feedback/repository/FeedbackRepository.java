
package br.com.fiap.feedback.repository;

import br.com.fiap.feedback.model.Feedback;

import java.util.List;

public interface FeedbackRepository {

    Feedback salvar(Feedback feedback);

    List<Feedback> listarTodos();

}
