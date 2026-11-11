package uk.co.periculo.burpsqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Watches traffic passing through Burp and scores each request for how worthwhile
 * it would be to run through sqlmap. Entirely passive: it never sends a request
 * of its own, it only reads what Burp already saw.
 */
public class CandidateDetector implements HttpHandler {

    /** Parameter names that usually end up in a WHERE clause. */
    private static final Set<String> INTERESTING_NAMES = Set.of(
            "id", "uid", "userid", "user_id", "pid", "productid", "product_id",
            "cat", "catid", "cat_id", "category", "cid", "item", "itemid", "item_id",
            "order", "orderby", "order_by", "sort", "sortby", "sort_by", "dir",
            "filter", "search", "q", "query", "keyword", "kw", "term",
            "page", "pageid", "page_id", "limit", "offset", "start", "num", "count",
            "table", "column", "field", "select", "where", "sql", "key",
            "ref", "view", "show", "news", "newsid", "article", "articleid",
            "report", "from", "to", "date", "year", "month", "day",
            "type", "status", "group", "groupid", "role", "roleid",
            "user", "username", "email", "login", "name",
            "file", "fileid", "doc", "docid", "index", "list",
            "value", "val", "code", "no", "number", "uuid", "guid", "token");

    private static final Pattern STATIC_ASSET = Pattern.compile(
            "\\.(js|mjs|css|png|jpe?g|gif|svg|ico|webp|bmp|woff2?|ttf|eot|otf|map|mp4|webm|mp3|avi|zip|gz)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMERIC = Pattern.compile("-?\\d{1,15}");
    private static final Pattern AUTH_PATH = Pattern.compile(
            "login|signin|sign-in|auth|session|register|account|password|admin",
            Pattern.CASE_INSENSITIVE);

    /** Bodies larger than this are almost certainly downloads, not error pages. */
    private static final int MAX_BODY_SCAN = 2 * 1024 * 1024;

    private final MontoyaApi api;
    private final CandidateStore store;

    public volatile boolean enabled = true;
    public volatile boolean inScopeOnly = true;
    public volatile boolean proxyOnly = true;

    public CandidateDetector(MontoyaApi api, CandidateStore store) {
        this.api = api;
        this.store = store;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        return RequestToBeSentAction.continueWith(request);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        try {
            if (enabled) analyse(response);
        } catch (Exception ex) {
            // This runs on Burp's networking threads; never let it break traffic.
            api.logging().logToError("[sqlmap] detector error: " + ex);
        }
        return ResponseReceivedAction.continueWith(response);
    }

    private void analyse(HttpResponseReceived response) {
        HttpRequest request = response.initiatingRequest();
        if (request == null) return;

        if (proxyOnly && response.toolSource().toolType() != ToolType.PROXY) return;
        if (inScopeOnly && !api.scope().isInScope(request.url())) return;

        // Never feed sqlmap's own traffic back into the candidate list.
        String userAgent = request.headerValue("User-Agent");
        if (userAgent != null && userAgent.toLowerCase().contains("sqlmap")) return;

        String body = response.body().length() <= MAX_BODY_SCAN ? response.bodyToString() : null;

        Verdict verdict = score(request, body);
        if (!verdict.worthTesting()) return;

        // Captured lazily: copyToTempFile() costs a file write, and the store
        // only keeps a sample for a new or higher-scoring sighting.
        store.record(EndpointKey.of(request), verdict.score(), verdict.reasons(),
                () -> HttpRequestResponse.httpRequestResponse(request, response).copyToTempFile(),
                verdict.cookieCount());
    }

    /** How a request scored, and why. */
    public record Verdict(boolean worthTesting, int score, List<String> reasons, int cookieCount) {
        static Verdict skip() {
            return new Verdict(false, 0, List.of(), 0);
        }
    }

    /**
     * The scoring itself: pure, so it can be reasoned about and tested without
     * a running Burp.
     */
    public static Verdict score(HttpRequest request, String body) {
        String path = request.pathWithoutQuery();
        List<ParsedHttpParameter> params = request.parameters();

        List<String> reasons = new ArrayList<>();
        int score = 0;

        // A leaked DBMS error outweighs every other signal here.
        String dbms = SqlErrors.detect(body);
        if (dbms != null) {
            score += 60;
            reasons.add(dbms + " error message in response");
        }

        int testable = 0;
        int interesting = 0;
        int numeric = 0;
        int cookies = 0;
        for (ParsedHttpParameter p : params) {
            if (p.type() == HttpParameterType.COOKIE) {
                cookies++;
                continue;
            }
            if (!EndpointKey.TESTABLE.contains(p.type())) continue;
            testable++;
            String name = p.name().toLowerCase();
            if (INTERESTING_NAMES.contains(name) && interesting < 3) {
                interesting++;
                reasons.add("parameter '" + p.name() + "'");
            }
            String value = p.value();
            if (value != null && NUMERIC.matcher(value).matches() && numeric < 2) {
                numeric++;
                reasons.add("numeric value in '" + p.name() + "'");
            }
        }

        boolean numericPathSegment = hasNumericSegment(path);
        if (numericPathSegment) {
            score += 15;
            reasons.add("numeric path segment");
        }

        // Nothing to inject into, and not a static asset worth remembering either.
        if (testable == 0 && !numericPathSegment && dbms == null) return Verdict.skip();
        if (STATIC_ASSET.matcher(path).find() && dbms == null) return Verdict.skip();

        if (testable > 0) score += 10;
        score += interesting * 12;
        score += numeric * 8;
        if ("POST".equalsIgnoreCase(request.method())) score += 3;
        if (AUTH_PATH.matcher(path).find() && testable > 0) {
            score += 10;
            reasons.add("authentication-related path");
        }
        if (testable > 0 && reasons.isEmpty()) reasons.add(testable + " testable parameter(s)");

        return new Verdict(true, score, reasons, cookies);
    }

    private static boolean hasNumericSegment(String path) {
        for (String segment : path.split("/")) {
            if (!segment.isEmpty() && segment.chars().allMatch(Character::isDigit)) return true;
        }
        return false;
    }
}
