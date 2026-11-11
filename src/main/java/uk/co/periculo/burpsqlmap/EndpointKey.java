package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Identity of a testable endpoint: method, host, normalised path and the set of
 * parameter names. Two requests share a key when running sqlmap against one
 * would tell you what the other would, so it is also the dedupe and
 * already-tested key.
 */
public record EndpointKey(String method, String host, String path, String params) {

    /**
     * Parameter types sqlmap tests by default. Cookies are deliberately left out
     * of the identity: analytics cookies come and go between requests and would
     * split one endpoint into a dozen keys. They are still shown and still
     * tested at --level 2+.
     */
    static final Set<HttpParameterType> TESTABLE = EnumSet.of(
            HttpParameterType.URL, HttpParameterType.BODY, HttpParameterType.JSON,
            HttpParameterType.XML, HttpParameterType.XML_ATTRIBUTE,
            HttpParameterType.MULTIPART_ATTRIBUTE);

    private static final Pattern UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_HEX = Pattern.compile("[0-9a-f]{16,}", Pattern.CASE_INSENSITIVE);

    public static EndpointKey of(HttpRequest request) {
        TreeSet<String> names = new TreeSet<>();
        for (ParsedHttpParameter p : request.parameters()) {
            if (TESTABLE.contains(p.type())) names.add(p.name());
        }
        StringJoiner joiner = new StringJoiner(",");
        names.forEach(joiner::add);

        return new EndpointKey(
                request.method(),
                request.httpService().host(),
                normalisePath(request.pathWithoutQuery()),
                joiner.toString());
    }

    /**
     * Collapses identifier-looking path segments so /product/41 and /product/42
     * count as one endpoint rather than one per row in the database.
     */
    public static String normalisePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String[] segments = path.split("/", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (i > 0) out.append('/');
            if (seg.isEmpty()) continue;
            if (seg.chars().allMatch(Character::isDigit)) out.append("{id}");
            else if (UUID.matcher(seg).matches()) out.append("{uuid}");
            else if (LONG_HEX.matcher(seg).matches()) out.append("{hex}");
            else out.append(seg);
        }
        String result = out.toString();
        if (result.length() > 1 && result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.isEmpty() ? "/" : result;
    }

    /** Stable identity string, used as the persistence key. */
    public String id() {
        return method + "|" + host + "|" + path + "|" + params;
    }

    public String paramList() {
        return params.isEmpty() ? "(none)" : params;
    }

    @Override
    public String toString() {
        return method + " " + host + path + " [" + paramList() + "]";
    }
}
