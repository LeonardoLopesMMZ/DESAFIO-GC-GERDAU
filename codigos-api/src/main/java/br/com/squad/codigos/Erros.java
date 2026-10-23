package br.com.squad.codigos;

import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class Erros {

    private static final Logger LOG = LoggerFactory.getLogger(Erros.class);

    public record Erro(int status, String erro, String caminho, LocalDateTime quando) {
    }

    private static ResponseEntity<Erro> erro(HttpStatus status, String mensagem, HttpServletRequest req) {
        return ResponseEntity.status(status).body(new Erro(status.value(), mensagem, req.getRequestURI(), LocalDateTime.now()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Erro> status(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus s = HttpStatus.valueOf(e.getStatusCode().value());
        return erro(s, e.getReason() == null ? s.getReasonPhrase() : e.getReason(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Erro> validacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getDefaultMessage() == null || f.getDefaultMessage().startsWith("must")
                        ? "Campo inválido: " + f.getField() : f.getDefaultMessage())
                .orElse("Dados inválidos");
        return erro(HttpStatus.BAD_REQUEST, msg, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Erro> corpo(HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, "O corpo da requisição não é um JSON válido ou falta algum campo.", req);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<Erro> faltando(Exception e, HttpServletRequest req) {
        String nome = e instanceof MissingServletRequestPartException p ? p.getRequestPartName()
                : ((MissingServletRequestParameterException) e).getParameterName();
        return erro(HttpStatus.BAD_REQUEST, "Falta o campo '" + nome + "'.", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Erro> tipo(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, "Valor inválido em '" + e.getName() + "': " + e.getValue(), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Erro> grande(HttpServletRequest req) {
        return erro(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo maior que 20 MB. Divida a planilha em partes.", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Erro> formato(HttpServletRequest req) {
        return erro(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de envio não aceito neste endereço.", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Erro> metodo(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return erro(HttpStatus.METHOD_NOT_ALLOWED, "Método " + e.getMethod() + " não é aceito neste endereço.", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Erro> naoExiste(HttpServletRequest req) {
        return erro(HttpStatus.NOT_FOUND, "Endereço não existe. Veja a lista em /swagger.", req);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Erro> banco(DataAccessException e, HttpServletRequest req) {
        LOG.error("Falha no banco", e);
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "Falha ao acessar o banco de dados. Tente de novo; se continuar, reinicie o sistema.", req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Erro> inesperado(Exception e, HttpServletRequest req) {
        LOG.error("Erro inesperado", e);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado no sistema. Detalhe: " + e.getClass().getSimpleName(), req);
    }
}
