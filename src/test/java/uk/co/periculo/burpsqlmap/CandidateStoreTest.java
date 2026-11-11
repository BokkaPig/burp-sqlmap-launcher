package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.periculo.burpsqlmap.TestStubs.*;

@DisplayName("Candidate store: dedupe and lazy sample capture")
class CandidateStoreTest {

    private static HttpRequestResponse sample() {
        return stub(HttpRequestResponse.class, Map.of());
    }

    @Test
    @DisplayName("repeat sightings collapse to one candidate and capture one sample")
    void dedupesAndCapturesOnce() {
        CandidateStore store = new CandidateStore();
        AtomicInteger captures = new AtomicInteger();

        HttpRequest request = request("GET", "t.com", "/item.php",
                param("id", "5", HttpParameterType.URL));
        var verdict = CandidateDetector.score(request, "ok");

        for (int i = 0; i < 25; i++) {
            store.record(EndpointKey.of(request), verdict.score(), verdict.reasons(),
                    () -> {
                        captures.incrementAndGet();
                        return sample();
                    }, verdict.cookieCount());
        }

        assertEquals(1, store.size(), "25 identical requests are one endpoint");
        assertEquals(25, store.all().get(0).seen, "but the hit count still counts them");
        assertEquals(1, captures.get(), "capturing writes a temp file, so it must happen once");
    }

    @Test
    @DisplayName("a higher-scoring sighting replaces the stored sample")
    void upgradesToTheIncriminatingSample() {
        CandidateStore store = new CandidateStore();
        AtomicInteger captures = new AtomicInteger();

        HttpRequest request = request("GET", "t.com", "/item.php",
                param("id", "5", HttpParameterType.URL));
        var plain = CandidateDetector.score(request, "ok");
        store.record(EndpointKey.of(request), plain.score(), plain.reasons(),
                () -> { captures.incrementAndGet(); return sample(); }, 0);

        var leaking = CandidateDetector.score(request, "ORA-01756: quoted string not properly terminated");
        store.record(EndpointKey.of(request), leaking.score(), leaking.reasons(),
                () -> { captures.incrementAndGet(); return sample(); }, 0);

        assertEquals(2, captures.get(), "a better sample is worth capturing");
        assertTrue(store.all().get(0).score >= 60);
        assertEquals("High", store.all().get(0).severity());
    }

    @Test
    @DisplayName("reasons accumulate without duplicating")
    void mergesReasons() {
        CandidateStore store = new CandidateStore();
        HttpRequest request = request("GET", "t.com", "/item.php",
                param("id", "5", HttpParameterType.URL));
        var verdict = CandidateDetector.score(request, "ok");

        store.record(EndpointKey.of(request), verdict.score(), verdict.reasons(), CandidateStoreTest::sample, 0);
        store.record(EndpointKey.of(request), verdict.score(), verdict.reasons(), CandidateStoreTest::sample, 0);

        Candidate candidate = store.all().get(0);
        assertEquals(List.copyOf(verdict.reasons()), List.copyOf(candidate.reasons));
    }

    @Test
    @DisplayName("the dirty flag is consumed exactly once per change")
    void dirtyFlagIsEdgeTriggered() {
        CandidateStore store = new CandidateStore();
        assertTrue(store.consumeDirty(), "starts dirty so the first paint happens");
        assertFalse(store.consumeDirty());

        HttpRequest request = request("GET", "t.com", "/x.php", param("id", "1", HttpParameterType.URL));
        var verdict = CandidateDetector.score(request, "ok");
        store.record(EndpointKey.of(request), verdict.score(), verdict.reasons(), CandidateStoreTest::sample, 0);

        assertTrue(store.consumeDirty());
        assertFalse(store.consumeDirty());
    }

    @Test
    @DisplayName("clearing empties the list")
    void clears() {
        CandidateStore store = new CandidateStore();
        HttpRequest request = request("GET", "t.com", "/x.php", param("id", "1", HttpParameterType.URL));
        var verdict = CandidateDetector.score(request, "ok");
        store.record(EndpointKey.of(request), verdict.score(), verdict.reasons(), CandidateStoreTest::sample, 0);

        store.clear();
        assertEquals(0, store.size());
    }
}
