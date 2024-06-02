package com.nh.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Properties;

/**
 * HTTP response.
 * Return one of these from serve().
 */
public class HttpResponse {

    /**
     * Some HTTP response status codes
     */
    public static final String HTTP_OK = "200 OK",  HTTP_REDIRECT = "301 Moved Permanently",  HTTP_FORBIDDEN = "403 Forbidden",  HTTP_NOTFOUND = "404 Not Found",  HTTP_BADREQUEST = "400 Bad Request",  HTTP_INTERNALERROR = "500 Internal Server Error",HTTP_SOAP_ERROR = "application/soap+xml",  HTTP_NOTIMPLEMENTED = "501 Not Implemented";
    /**
     * Common mime types for dynamic content
     */
    public static final String MIME_PLAINTEXT = "text/plain",  MIME_HTML = "text/html",  MIME_DEFAULT_BINARY = "application/octet-stream",  MIME_XML = "text/xml";

    /**
     * Default constructor: response = HTTP_OK, data = mime = 'null'
     */
    public HttpResponse() {
        this.status = HTTP_OK;
    }

    /**
     * Basic constructor.
     */
    public HttpResponse(String status, String mimeType, InputStream data) {
        try {
            this.status = status;
            this.mimeType = mimeType;
            this.data = data;
            addHeader("content-length", "" + data.available());
        } catch (IOException ex) {
            
        }
    }

    /**
     * Convenience method that makes an InputStream out of
     * given text.
     */
    public HttpResponse(String status, String mimeType, String txt) {
        this.status = status;
        this.mimeType = mimeType;
        this.data = new ByteArrayInputStream(txt.getBytes());
        int length = txt == null ? 0 : txt.getBytes().length;
        addHeader("content-length", "" + length);
    }

    /**
     * Adds given line to the header.
     */
    public void addHeader(String name, String value) {
        header.put(name, value);
    }
    /**
     * HTTP status code after processing, e.g. "200 OK", HTTP_OK
     */
    public String status;
    /**
     * MIME type of content, e.g. "text/html"
     */
    public String mimeType;
    /**
     * Data of the response, may be null.
     */
    public InputStream data;
    /**
     * Headers for the HTTP response. Use addHeader()
     * to add lines.
     */
    public Properties header = new Properties();
}
