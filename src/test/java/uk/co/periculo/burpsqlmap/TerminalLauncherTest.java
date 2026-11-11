package uk.co.periculo.burpsqlmap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The runner script is where quoting either survives or breaks, so it is checked
 * by actually running it with a stand-in sqlmap that echoes its argv.
 *
 * "echo" is used as the terminal: the script still gets written, and the spawn
 * is harmless rather than opening a window during a test run.
 */
@DisplayName("Runner script")
class TerminalLauncherTest {

    private static Path standInSqlmap(Path dir) throws IOException {
        Path fake = dir.resolve("fake-sqlmap");
        Files.writeString(fake, "#!/bin/sh\nfor arg in \"$@\"; do echo \"ARG:$arg\"; done\n");
        assertTrue(fake.toFile().setExecutable(true));
        return fake;
    }

    private static String runScript(Path script) throws Exception {
        Process process = new ProcessBuilder("sh", script.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return output;
    }

    @Test
    @DisplayName("is valid shell and passes awkward arguments through intact")
    void quotingSurvivesTheRoundTrip(@TempDir Path dir) throws Exception {
        Path fake = standInSqlmap(dir);
        Path requestFile = dir.resolve("request.txt");
        Files.writeString(requestFile, "GET /x?id=1 HTTP/1.1\r\nHost: t\r\n\r\n");

        SqlmapOptions options = new SqlmapOptions();
        options.dbms = "Microsoft SQL Server";
        options.tamper = "space2comment,randomcase";
        options.extraArgs = "--prefix \"')\"";

        List<String> args = options.toArgs(fake.toString(), requestFile.toString());
        Path script = TerminalLauncher.launch("echo", dir, args, false);

        assertEquals(0, new ProcessBuilder("sh", "-n", script.toString()).start().waitFor(),
                "generated script must be valid sh");

        String output = runScript(script);
        assertTrue(output.contains("ARG:Microsoft SQL Server"), "spaced value must stay one argument");
        assertTrue(output.contains("ARG:space2comment,randomcase"), "tamper list must stay one argument");
        assertTrue(output.contains("ARG:')"), "quoted extra arg must survive");
        assertTrue(output.contains("ARG:" + requestFile), "request path must be passed");
    }

    @Test
    @DisplayName("holding the terminal open waits for input")
    void holdOpenAddsAWait(@TempDir Path dir) throws Exception {
        Path fake = standInSqlmap(dir);
        List<String> args = new SqlmapOptions().toArgs(fake.toString(), dir.resolve("r.txt").toString());

        Path script = TerminalLauncher.launch("echo", dir, args, true);
        assertTrue(Files.readString(script).contains("read "), "should wait before closing");

        Path noHold = TerminalLauncher.launch("echo", dir, args, false);
        assertFalse(Files.readString(noHold).contains("read "));
    }

    @Test
    @DisplayName("at least one terminal is always offered")
    void alwaysOffersATerminal() {
        assertFalse(TerminalLauncher.available().isEmpty());
        assertNotNull(TerminalLauncher.detectDefault());
    }
}
