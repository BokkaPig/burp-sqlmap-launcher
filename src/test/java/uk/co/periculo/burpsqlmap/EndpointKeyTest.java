package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.periculo.burpsqlmap.TestStubs.param;
import static uk.co.periculo.burpsqlmap.TestStubs.request;

@DisplayName("Endpoint identity (the dedupe and already-tested key)")
class EndpointKeyTest {

    @Test
    @DisplayName("identifier-looking path segments are collapsed")
    void normalisesPaths() {
        assertEquals("/product/{id}/view", EndpointKey.normalisePath("/product/42/view"));
        assertEquals("/u/{uuid}", EndpointKey.normalisePath("/u/3f2504e0-4f89-11d3-9a0c-0305e82c3301"));
        assertEquals("/t/{hex}", EndpointKey.normalisePath("/t/a1b2c3d4e5f60718"));
        assertEquals("/admin", EndpointKey.normalisePath("/admin/"));
        assertEquals("/search.php", EndpointKey.normalisePath("/search.php"));
        assertEquals("/", EndpointKey.normalisePath(""));
        assertEquals("/", EndpointKey.normalisePath(null));
    }

    @Test
    @DisplayName("the same endpoint on a different row id is one key")
    void collapsesRowIds() {
        ParsedHttpParameter id = param("id", "1", HttpParameterType.URL);
        ParsedHttpParameter cat = param("cat", "x", HttpParameterType.URL);

        EndpointKey first = EndpointKey.of(request("GET", "t.com", "/item/1", id, cat));
        EndpointKey second = EndpointKey.of(request("GET", "t.com", "/item/99", cat, id));

        assertEquals(first.id(), second.id(), "row id and parameter order must not matter");
    }

    @Test
    @DisplayName("a different parameter set is a different, untested endpoint")
    void parameterSetIsPartOfIdentity() {
        ParsedHttpParameter id = param("id", "1", HttpParameterType.URL);
        ParsedHttpParameter sort = param("sort", "price", HttpParameterType.URL);

        EndpointKey justId = EndpointKey.of(request("GET", "t.com", "/search", id));
        EndpointKey idAndSort = EndpointKey.of(request("GET", "t.com", "/search", id, sort));

        assertNotEquals(justId.id(), idAndSort.id(),
                "a new parameter is new attack surface and must show as untested");
    }

    @Test
    @DisplayName("cookies are excluded, so analytics churn cannot fragment an endpoint")
    void cookiesExcludedFromIdentity() {
        EndpointKey key = EndpointKey.of(request("GET", "t.com", "/x",
                param("id", "1", HttpParameterType.URL),
                param("PHPSESSID", "abc", HttpParameterType.COOKIE),
                param("_ga", "GA1.2.3", HttpParameterType.COOKIE)));

        assertEquals("id", key.params());
    }

    @Test
    @DisplayName("method and host are part of the identity")
    void methodAndHostMatter() {
        ParsedHttpParameter id = param("id", "1", HttpParameterType.URL);
        assertNotEquals(EndpointKey.of(request("GET", "t.com", "/x", id)).id(),
                EndpointKey.of(request("POST", "t.com", "/x", id)).id());
        assertNotEquals(EndpointKey.of(request("GET", "a.com", "/x", id)).id(),
                EndpointKey.of(request("GET", "b.com", "/x", id)).id());
    }
}
