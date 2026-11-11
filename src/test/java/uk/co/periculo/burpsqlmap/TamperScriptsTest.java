package uk.co.periculo.burpsqlmap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These read the real sqlmap install, so they skip rather than fail where sqlmap
 * is not present.
 */
@DisplayName("Tamper script discovery")
class TamperScriptsTest {

    private static void requireSqlmap() {
        Assumptions.assumeTrue(TamperScripts.locate("sqlmap") != null,
                "sqlmap's tamper directory was not found on this machine");
    }

    @Test
    @DisplayName("the tamper directory is found from the sqlmap binary, following symlinks")
    void locatesTamperDirectory() {
        requireSqlmap();
        Path dir = TamperScripts.locate("sqlmap");
        assertNotNull(dir);
        assertTrue(Files.isDirectory(dir));
        assertTrue(dir.endsWith("tamper"), "expected a tamper directory, got " + dir);
    }

    @Test
    @DisplayName("every discovered script has a name and a description")
    void readsNamesAndDescriptions() {
        requireSqlmap();
        List<TamperScripts.Tamper> scripts = TamperScripts.discover("sqlmap");

        assertTrue(scripts.size() > 50, "expected a full tamper set, got " + scripts.size());
        assertTrue(scripts.stream().noneMatch(t -> t.name().endsWith(".py")));
        assertTrue(scripts.stream().noneMatch(t -> t.name().isBlank()));
        assertTrue(scripts.stream().allMatch(t -> !t.description().isBlank()),
                "descriptions come from each script's tamper() docstring");
    }

    @Test
    @DisplayName("the list is sorted, so the dropdown is predictable")
    void sorted() {
        requireSqlmap();
        List<TamperScripts.Tamper> scripts = TamperScripts.discover("sqlmap");
        List<String> names = scripts.stream().map(TamperScripts.Tamper::name).toList();
        assertEquals(names.stream().sorted().toList(), names);
    }

    @Test
    @DisplayName("space2comment is present and described")
    void wellKnownScript() {
        requireSqlmap();
        TamperScripts.Tamper script = TamperScripts.discover("sqlmap").stream()
                .filter(t -> t.name().equals("space2comment"))
                .findFirst().orElse(null);

        assertNotNull(script);
        assertTrue(script.description().toLowerCase().contains("comment"));
    }

    @Test
    @DisplayName("an unusable sqlmap path still yields a usable list")
    void fallsBack() {
        List<TamperScripts.Tamper> scripts = TamperScripts.discover("/nonexistent/sqlmap");
        assertTrue(scripts.size() > 30, "the built-in fallback should still be useful");
        assertTrue(scripts.stream().anyMatch(t -> t.name().equals("space2comment")));
    }

    @Test
    @DisplayName("toString is the bare name, which is what --tamper needs")
    void toStringIsTheFlagValue() {
        assertEquals("space2comment", new TamperScripts.Tamper("space2comment", "desc").toString());
    }
}
