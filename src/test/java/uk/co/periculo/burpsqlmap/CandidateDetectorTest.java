package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.params.HttpParameterType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.periculo.burpsqlmap.TestStubs.param;
import static uk.co.periculo.burpsqlmap.TestStubs.request;

@DisplayName("Candidate scoring")
class CandidateDetectorTest {

    @Test
    @DisplayName("a classic numeric id is worth testing")
    void numericId() {
        var verdict = CandidateDetector.score(
                request("GET", "t.com", "/item.php", param("id", "7", HttpParameterType.URL)), "ok");

        assertTrue(verdict.worthTesting());
        assertTrue(verdict.reasons().stream().anyMatch(r -> r.contains("'id'")));
    }

    @Test
    @DisplayName("a leaked DBMS error outranks everything else")
    void leakedErrorScoresHigh() {
        var verdict = CandidateDetector.score(
                request("GET", "t.com", "/x.php", param("id", "1", HttpParameterType.URL)),
                "You have an error in your SQL syntax; check the manual for your MySQL server version");

        assertTrue(verdict.score() >= 60, "a leaked error should land in the High band");
        assertTrue(verdict.reasons().get(0).contains("MySQL"));
    }

    @Test
    @DisplayName("static assets and parameterless pages are dropped")
    void dropsNoise() {
        assertFalse(CandidateDetector.score(request("GET", "t.com", "/app.js"), "var x=1").worthTesting());
        assertFalse(CandidateDetector.score(request("GET", "t.com", "/style.css"), "body{}").worthTesting());
        assertFalse(CandidateDetector.score(request("GET", "t.com", "/about.html"), "<p>hi</p>").worthTesting());
    }

    @Test
    @DisplayName("a static asset that leaks a SQL error is still flagged")
    void staticAssetLeakingErrorIsKept() {
        var verdict = CandidateDetector.score(request("GET", "t.com", "/style.css"),
                "You have an error in your SQL syntax; check the manual for your MySQL server version");
        assertTrue(verdict.worthTesting());
    }

    @Test
    @DisplayName("a numeric path segment alone is worth testing")
    void numericPathSegment() {
        var verdict = CandidateDetector.score(request("GET", "t.com", "/product/42"), "<p>item</p>");
        assertTrue(verdict.worthTesting());
        assertTrue(verdict.reasons().contains("numeric path segment"));
    }

    @Test
    @DisplayName("a login form scores above a plain parameter")
    void authPathsRankHigher() {
        var login = CandidateDetector.score(request("POST", "t.com", "/login.php",
                param("username", "a", HttpParameterType.BODY),
                param("password", "b", HttpParameterType.BODY)), "bad creds");
        var plain = CandidateDetector.score(request("GET", "t.com", "/page.php",
                param("ref", "a", HttpParameterType.URL)), "ok");

        assertTrue(login.worthTesting());
        assertTrue(login.score() > plain.score());
    }

    @Test
    @DisplayName("cookies are counted but do not by themselves make a candidate")
    void cookiesCountedSeparately() {
        var verdict = CandidateDetector.score(request("GET", "t.com", "/x.php",
                param("id", "1", HttpParameterType.URL),
                param("PHPSESSID", "abc", HttpParameterType.COOKIE)), "ok");

        assertEquals(1, verdict.cookieCount());
    }
}
