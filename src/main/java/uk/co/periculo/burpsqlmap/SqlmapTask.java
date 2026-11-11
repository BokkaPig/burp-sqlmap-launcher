package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.nio.charset.StandardCharsets;

/**
 * One request queued for sqlmap.
 *
 * The raw request is held as an ISO-8859-1 string so it can be shown and edited
 * in a text area and written back out byte-for-byte, which matters for binary
 * bodies and for any encoding sqlmap needs to see exactly as Burp sent it.
 */
public class SqlmapTask {

    public final EndpointKey key;
    public final String method;
    public final String url;
    public final String host;
    public final boolean secure;

    /** Editable raw request; latin-1 so every byte round-trips. */
    public String rawRequest;

    public SqlmapTask(HttpRequestResponse rr) {
        var request = rr.request();
        this.key = EndpointKey.of(request);
        this.method = request.method();
        this.url = request.url();
        this.host = request.httpService().host();
        this.secure = request.httpService().secure();
        this.rawRequest = new String(request.toByteArray().getBytes(), StandardCharsets.ISO_8859_1);
    }

    public byte[] requestBytes() {
        return rawRequest.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Whether the user has marked an injection point with sqlmap's '*' syntax. */
    public boolean hasInjectionMarker() {
        return rawRequest.contains("*");
    }

    @Override
    public String toString() {
        String path = url;
        int schemeEnd = path.indexOf("://");
        if (schemeEnd > 0) {
            int pathStart = path.indexOf('/', schemeEnd + 3);
            path = pathStart > 0 ? path.substring(pathStart) : "/";
        }
        if (path.length() > 60) path = path.substring(0, 57) + "...";
        return method + " " + host + path;
    }
}
