package os.assurance.eu.api.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Returns the reason of a 4xx {@link ResponseStatusException} as {@code message}, in Spring's usual error shape, so
 * people see "Only compliance or an admin can resolve a data contract breach" instead of "Forbidden". Reasons are
 * written by us for the caller; 5xx reasons and every other exception's message stay hidden
 * ({@code server.error.include-message} remains off). Lowest precedence, so the specific advices still answer first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ResponseStatusMessageAdvice {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", ex.getStatusCode().value());
    body.put("error", status == null ? "" : status.getReasonPhrase());
    if (ex.getStatusCode().is4xxClientError() && ex.getReason() != null && !ex.getReason().isBlank()) {
      body.put("message", ex.getReason());
    }
    body.put("path", request.getRequestURI());
    return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(body);
  }
}
