import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.requests.HttpRequest;
import uk.co.periculo.burpsqlmap.*;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.co.periculo.burpsqlmap.TestStubs.*;

/**
 * Stands the extension's Swing UI up outside Burp, against stubbed Montoya
 * interfaces, so layout can be eyeballed or screenshotted without a licensed
 * Burp and a live target.
 *
 * This is a visual aid, not a test: the assertions live in src/test. Run it with
 * tools/ui-harness/run.sh.
 *
 * Modes:
 *   candidates  the Candidates table, populated with representative findings
 *   run         the Run pane with a queued request
 *   tamper      the Run pane with the tamper dropdown open
 */
public final class UiHarness {

    private static final String RAW_REQUEST = """
            POST /app/login.php?ref=1 HTTP/1.1\r
            Host: target.example.com\r
            User-Agent: Mozilla/5.0 (X11; Linux x86_64) Firefox/128.0\r
            Content-Type: application/x-www-form-urlencoded\r
            Cookie: PHPSESSID=9f2a1c8e4b; remember=0\r
            Content-Length: 29\r
            \r
            username=admin&password=hunter2""";

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "candidates";
        MontoyaApi api = apiWithStorage(new HashMap<>());

        CandidateStore store = new CandidateStore();
        TestedRegistry tested = new TestedRegistry(api);
        CandidateDetector detector = new CandidateDetector(api, store);

        if (mode.equals("candidates")) populate(store, tested);

        SwingUtilities.invokeAndWait(() -> {
            Settings settings = new Settings(api);
            SqlmapTab runTab = new SqlmapTab(api, settings, tested);
            CandidatesPanel candidates = new CandidatesPanel(api, store, tested, detector, runTab);

            JTabbedPane root = new JTabbedPane();
            root.addTab("Candidates", candidates);
            root.addTab("Run", runTab);
            if (!mode.equals("candidates")) root.setSelectedIndex(1);

            JFrame frame = new JFrame("sqlmap Launcher - UI harness (" + mode + ")");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(root);
            frame.setSize(1500, 900);
            frame.setVisible(true);

            if (mode.equals("run") || mode.equals("tamper")) {
                runTab.addTasks(List.of(sampleExchange()));
            }
        });

        Thread.sleep(1200);

        if (mode.equals("candidates")) {
            SwingUtilities.invokeAndWait(() -> {
                JTable table = find(JTable.class, JFrame.getWindows()[0]);
                if (table != null && table.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
            });
        }
        if (mode.equals("tamper")) {
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> picker = findCombo(JFrame.getWindows()[0]);
                if (picker != null) {
                    for (int i = 0; i < picker.getItemCount(); i++) {
                        String name = String.valueOf(picker.getItemAt(i));
                        if (name.equals("space2comment") || name.equals("between")) {
                            picker.setSelectedIndex(i);
                        }
                    }
                    picker.setPopupVisible(true);
                }
            });
        }

        System.out.println("UI is up in '" + mode + "' mode. Close the window to exit.");
        Thread.sleep(Long.MAX_VALUE);
    }

    /** Representative findings, scored by the real detector. */
    private static void populate(CandidateStore store, TestedRegistry tested) {
        feed(store, request("GET", "shop.target.co.uk", "/product.php",
                        param("id", "1337", HttpParameterType.URL)),
                "You have an error in your SQL syntax; check the manual that corresponds "
                        + "to your MySQL server version", 4);
        feed(store, request("POST", "shop.target.co.uk", "/login.php",
                param("username", "admin", HttpParameterType.BODY),
                param("password", "x", HttpParameterType.BODY)), "Invalid credentials", 2);
        feed(store, request("GET", "shop.target.co.uk", "/search.php",
                param("q", "shoes", HttpParameterType.URL),
                param("sort", "price", HttpParameterType.URL)), "<p>3 results</p>", 9);
        feed(store, request("GET", "shop.target.co.uk", "/orders/4821",
                param("view", "full", HttpParameterType.URL)), "<p>order</p>", 1);
        feed(store, request("GET", "api.target.co.uk", "/v2/users",
                param("limit", "50", HttpParameterType.JSON),
                param("offset", "0", HttpParameterType.JSON)), "{\"users\":[]}", 3);

        // One already done, so the tick and the duplicate guard are visible.
        tested.record(EndpointKey.of(request("GET", "shop.target.co.uk", "/search.php",
                        param("q", "shoes", HttpParameterType.URL),
                        param("sort", "price", HttpParameterType.URL))),
                "--level 3 --risk 3 --batch --dbs", "launched from Burp");
    }

    private static void feed(CandidateStore store, HttpRequest request, String body, int times) {
        var verdict = CandidateDetector.score(request, body);
        if (!verdict.worthTesting()) return;
        for (int i = 0; i < times; i++) {
            store.record(EndpointKey.of(request), verdict.score(), verdict.reasons(),
                    () -> stub(HttpRequestResponse.class, Map.of()), verdict.cookieCount());
        }
    }

    private static HttpRequestResponse sampleExchange() {
        var bytes = stub(burp.api.montoya.core.ByteArray.class,
                Map.of("getBytes", RAW_REQUEST.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
        var service = stub(burp.api.montoya.http.HttpService.class,
                Map.of("host", "target.example.com", "secure", true));
        var request = stub(HttpRequest.class, Map.of(
                "method", "POST",
                "url", "https://target.example.com/app/login.php?ref=1",
                "pathWithoutQuery", "/app/login.php",
                "parameters", List.of(param("username", "admin", HttpParameterType.BODY),
                        param("password", "hunter2", HttpParameterType.BODY)),
                "httpService", service,
                "toByteArray", bytes));
        return stub(HttpRequestResponse.class, Map.of("request", request));
    }

    private static <T> T find(Class<T> type, java.awt.Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof java.awt.Container inner) {
                T found = find(type, inner);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** The tamper picker is the only combo with a large number of entries. */
    private static JComboBox<?> findCombo(java.awt.Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JComboBox<?> combo && combo.getItemCount() > 50) return combo;
            if (child instanceof java.awt.Container inner) {
                JComboBox<?> found = findCombo(inner);
                if (found != null) return found;
            }
        }
        return null;
    }
}
