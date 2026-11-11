package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Deduplicated candidates, keyed by endpoint. Written from Burp's networking
 * threads and read from the Swing thread, so it is backed by a concurrent map.
 */
public class CandidateStore {

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();

    /**
     * Set on every change and cleared by the UI when it repaints. Polling a flag
     * keeps busy proxy traffic off the Swing thread entirely, where notifying a
     * listener per request would queue one event per request.
     */
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    /** True if anything changed since the last call. */
    public boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    public void record(EndpointKey key, int score, List<String> reasons,
                       Supplier<HttpRequestResponse> sample, int cookieCount) {
        Candidate candidate = candidates.computeIfAbsent(key.id(), id -> new Candidate(key));
        candidate.cookieCount = cookieCount;
        candidate.merge(score, reasons, sample);
        dirty.set(true);
    }

    public List<Candidate> all() {
        return new ArrayList<>(candidates.values());
    }

    public int size() {
        return candidates.size();
    }

    public void remove(Candidate candidate) {
        candidates.remove(candidate.key.id());
        dirty.set(true);
    }

    public void clear() {
        candidates.clear();
        dirty.set(true);
    }
}
