package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** One endpoint the detector thinks is worth putting through sqlmap. */
public class Candidate {

    public final EndpointKey key;
    public final List<String> reasons = new ArrayList<>();

    /** A sample exchange, held on disk rather than in heap. */
    public volatile HttpRequestResponse sample;
    public volatile int score;
    public volatile int seen;
    public volatile Instant firstSeen = Instant.now();
    public volatile Instant lastSeen = Instant.now();
    public volatile int cookieCount;

    public Candidate(EndpointKey key) {
        this.key = key;
    }

    public String severity() {
        if (score >= 60) return "High";
        if (score >= 30) return "Medium";
        return "Low";
    }

    public String evidence() {
        return String.join("; ", reasons);
    }

    /**
     * Merges a later sighting of the same endpoint into this candidate. The
     * sample is a supplier because capturing one writes a temp file, and under
     * normal browsing most sightings are repeats whose sample is discarded.
     */
    public synchronized void merge(int newScore, List<String> newReasons,
                                   Supplier<HttpRequestResponse> newSample) {
        seen++;
        lastSeen = Instant.now();
        // Keep the most incriminating sample: that is the one worth sending to sqlmap.
        if (newScore > score || sample == null) {
            score = newScore;
            sample = newSample.get();
        }
        for (String reason : newReasons) {
            if (!reasons.contains(reason)) reasons.add(reason);
        }
    }
}
