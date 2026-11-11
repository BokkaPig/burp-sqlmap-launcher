package uk.co.periculo.burpsqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.params.HttpParameterType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.periculo.burpsqlmap.TestStubs.*;

@DisplayName("Tested registry: what has been run, and with which flags")
class TestedRegistryTest {

    private static EndpointKey key(String path, String... paramNames) {
        var params = new burp.api.montoya.http.message.params.ParsedHttpParameter[paramNames.length];
        for (int i = 0; i < paramNames.length; i++) {
            params[i] = param(paramNames[i], "1", HttpParameterType.URL);
        }
        return EndpointKey.of(request("GET", "t.com", path, params));
    }

    @Test
    @DisplayName("a run survives a restart, with its flags")
    void persistsAcrossRestart() {
        Map<String, String> projectFile = new HashMap<>();
        MontoyaApi api = apiWithStorage(projectFile);
        EndpointKey endpoint = key("/search.php", "q");

        TestedRegistry registry = new TestedRegistry(api);
        assertFalse(registry.isTested(endpoint));
        registry.record(endpoint, "--level 3 --risk 3 --batch", "launched from Burp");
        assertTrue(registry.isTested(endpoint));

        // A second registry over the same storage is what a Burp restart looks like.
        TestedRegistry reopened = new TestedRegistry(api);
        assertTrue(reopened.isTested(endpoint));
        assertEquals("--level 3 --risk 3 --batch", reopened.runsFor(endpoint).get(0).flags());
    }

    @Test
    @DisplayName("tabs and newlines in a note survive the flat-file encoding")
    void escapesControlCharacters() {
        Map<String, String> projectFile = new HashMap<>();
        MontoyaApi api = apiWithStorage(projectFile);
        EndpointKey endpoint = key("/x.php", "id");

        new TestedRegistry(api).record(endpoint, "--batch", "note with\ttab and\nnewline");

        assertEquals("note with\ttab and\nnewline",
                new TestedRegistry(api).runsFor(endpoint).get(0).note());
    }

    @Test
    @DisplayName("a different parameter set is not covered by a previous run")
    void parameterSetScopesTheRecord() {
        MontoyaApi api = apiWithStorage(new HashMap<>());
        TestedRegistry registry = new TestedRegistry(api);

        registry.record(key("/search.php", "q"), "--batch", "");

        assertTrue(registry.isTested(key("/search.php", "q")));
        assertFalse(registry.isTested(key("/search.php", "q", "sort")),
                "an extra parameter is untested surface");
    }

    @Test
    @DisplayName("repeat runs accumulate and can be forgotten")
    void tracksMultipleRunsAndForgets() {
        MontoyaApi api = apiWithStorage(new HashMap<>());
        TestedRegistry registry = new TestedRegistry(api);
        EndpointKey endpoint = key("/x.php", "id");

        registry.record(endpoint, "--level 3 --risk 3 --batch", "first");
        registry.record(endpoint, "--level 5 --risk 3 --batch", "second");
        assertEquals(2, registry.runCount(endpoint));

        registry.forget(endpoint);
        assertFalse(registry.isTested(endpoint));
        assertEquals(0, registry.runCount(endpoint));
    }

    @Test
    @DisplayName("an empty project file loads cleanly")
    void handlesEmptyStorage() {
        TestedRegistry registry = new TestedRegistry(apiWithStorage(new HashMap<>()));
        assertEquals(0, registry.all().size());
    }
}
