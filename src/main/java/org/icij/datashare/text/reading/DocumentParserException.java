package org.icij.datashare.text.reading;

/**
 * Created by julien on 3/9/16.
 */
public class DocumentParserException extends Exception {

    public DocumentParserException(String message) {
        super(message);
    }

    public DocumentParserException(String message, Throwable cause) {
        super(message, cause);
    }

}
