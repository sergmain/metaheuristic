package aiai.ai.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
public class StoreNewPartOfRawFileException extends RuntimeException {
    public String srcPath;
    public String trgPath;
}
