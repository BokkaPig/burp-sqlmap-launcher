package uk.co.periculo.burpsqlmap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Command building")
class SqlmapOptionsTest {

    @Test
    @DisplayName("defaults carry level and risk 3 and nothing else")
    void defaults() {
        SqlmapOptions options = new SqlmapOptions();
        assertEquals("sqlmap -r /tmp/req.txt --level 3 --risk 3 --batch",
                SqlmapOptions.toShellCommand(options.toArgs("sqlmap", "/tmp/req.txt")));
    }

    @Test
    @DisplayName("a DBMS name containing spaces stays a single quoted argument")
    void quotesSpacedValues() {
        SqlmapOptions options = new SqlmapOptions();
        options.dbms = "Microsoft SQL Server";
        options.testParams = "id";
        options.technique = "BEU";
        options.listDbs = true;
        options.forceSsl = true;

        assertEquals("sqlmap -r /tmp/req.txt --force-ssl --level 3 --risk 3 --technique BEU "
                        + "--dbms 'Microsoft SQL Server' -p id --batch --dbs",
                SqlmapOptions.toShellCommand(options.toArgs("sqlmap", "/tmp/req.txt")));
    }

    @Test
    @DisplayName("a comma-separated tamper list is one argument, not several")
    void tamperListStaysOneArgument() {
        SqlmapOptions options = new SqlmapOptions();
        options.tamper = "space2comment,between,randomcase";
        List<String> args = options.toArgs("sqlmap", "/tmp/req.txt");

        int index = args.indexOf("--tamper");
        assertTrue(index > 0, "--tamper should be present");
        assertEquals("space2comment,between,randomcase", args.get(index + 1));
    }

    @Test
    @DisplayName("free-text extra args are split like a shell would split them")
    void tokenisesExtraArgs() {
        assertEquals(List.of("--prefix", "')", "--suffix", "-- -", "--hex"),
                SqlmapOptions.tokenise("--prefix \"')\" --suffix '-- -' --hex"));
        assertEquals(List.of(), SqlmapOptions.tokenise("   "));
        assertEquals(List.of(), SqlmapOptions.tokenise(null));
    }

    @Test
    @DisplayName("a single quote inside a value is escaped safely")
    void escapesQuotes() {
        assertEquals("'it'\\''s'", SqlmapOptions.shellQuote("it's"));
    }

    @Test
    @DisplayName("the flag summary drops the binary and the request file")
    void flagSummaryIsJustFlags() {
        SqlmapOptions options = new SqlmapOptions();
        options.listDbs = true;
        assertEquals("--level 3 --risk 3 --batch --dbs", options.flagSummary());
    }
}
