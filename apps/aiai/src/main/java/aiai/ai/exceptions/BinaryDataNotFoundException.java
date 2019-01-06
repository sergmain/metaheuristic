package aiai.ai.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class BinaryDataNotFoundException extends RuntimeException {
    public BinaryDataNotFoundException() {
    }

    public BinaryDataNotFoundException(String message) {
        super(message);
    }
}
