package uk.co.periculo.burpsqlmap;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;

/**
 * Burp extension that finds requests worth testing for SQL injection, hands them
 * to sqlmap in a terminal, and remembers what has already been run.
 */
public class SqlmapExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("sqlmap Launcher");

        Settings settings = new Settings(api);
        TestedRegistry tested = new TestedRegistry(api);
        CandidateStore store = new CandidateStore();
        CandidateDetector detector = new CandidateDetector(api, store);

        // Passive only: this watches traffic Burp already has, it never sends anything.
        api.http().registerHttpHandler(detector);

        SwingUtilities.invokeLater(() -> {
            SqlmapTab runTab = new SqlmapTab(api, settings, tested);
            CandidatesPanel candidates = new CandidatesPanel(api, store, tested, detector, runTab);

            JTabbedPane root = new JTabbedPane();
            root.addTab("Candidates", candidates);
            root.addTab("Run", runTab);

            api.userInterface().applyThemeToComponent(root);
            api.userInterface().registerSuiteTab("sqlmap", root);
            api.userInterface().registerContextMenuItemsProvider(new SqlmapContextMenu(runTab));

            api.logging().logToOutput("sqlmap Launcher loaded."
                    + "\n  sqlmap:   " + settings.sqlmapPath
                    + "\n  terminal: " + settings.terminal
                    + "\n  work dir: " + settings.workDir
                    + "\nBrowse the target and watch the Candidates tab, or right-click"
                    + " any request and choose 'Send to sqlmap'.");
        });
    }
}
