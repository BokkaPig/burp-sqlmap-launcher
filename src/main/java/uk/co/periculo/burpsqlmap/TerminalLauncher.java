package uk.co.periculo.burpsqlmap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawns sqlmap in a real terminal. Everything is funnelled through a generated
 * shell script so we only have to get the quoting right once, rather than once
 * per terminal emulator.
 */
public class TerminalLauncher {

    /** Terminal name -> how it takes a command. */
    private static final Map<String, String[]> TERMINALS = new LinkedHashMap<>();

    static {
        TERMINALS.put("qterminal", new String[]{"qterminal", "-e"});
        TERMINALS.put("x-terminal-emulator", new String[]{"x-terminal-emulator", "-e"});
        TERMINALS.put("gnome-terminal", new String[]{"gnome-terminal", "--"});
        TERMINALS.put("xfce4-terminal", new String[]{"xfce4-terminal", "-e"});
        TERMINALS.put("konsole", new String[]{"konsole", "-e"});
        TERMINALS.put("alacritty", new String[]{"alacritty", "-e"});
        TERMINALS.put("kitty", new String[]{"kitty"});
        TERMINALS.put("xterm", new String[]{"xterm", "-e"});
        TERMINALS.put("tmux", new String[]{"tmux"});
    }

    public static List<String> available() {
        List<String> found = new ArrayList<>();
        for (String name : TERMINALS.keySet()) {
            if (onPath(name)) found.add(name);
        }
        if (found.isEmpty()) found.add("x-terminal-emulator");
        return found;
    }

    public static String detectDefault() {
        List<String> found = available();
        return found.get(0);
    }

    private static boolean onPath(String binary) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, binary))) return true;
        }
        return false;
    }

    /**
     * Writes a runner script for this sqlmap invocation and opens it in a terminal.
     *
     * @return the script that was launched, for logging
     */
    public static Path launch(String terminal, Path taskDir, List<String> args, boolean holdOpen)
            throws IOException {

        Path script = taskDir.resolve("run.sh");
        String command = SqlmapOptions.toShellCommand(args);

        StringBuilder sh = new StringBuilder();
        sh.append("#!/bin/sh\n");
        sh.append("cd ").append(SqlmapOptions.shellQuote(taskDir.toString())).append(" || exit 1\n");
        sh.append("printf '%s\\n' ").append(SqlmapOptions.shellQuote("$ " + command)).append("\n");
        sh.append("printf '%s\\n\\n' ").append(
                SqlmapOptions.shellQuote("---------------------------------------------")).append("\n");
        sh.append(command).append("\n");
        sh.append("status=$?\n");
        sh.append("printf '\\n%s\\n' \"sqlmap exited with status $status\"\n");
        if (holdOpen) {
            sh.append("printf '%s' 'Press Enter to close this window... '\n");
            sh.append("read _ignored\n");
        }

        Files.writeString(script, sh.toString());
        script.toFile().setExecutable(true);

        ProcessBuilder pb = new ProcessBuilder(spawnCommand(terminal, script, taskDir));
        pb.directory(taskDir.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.to(taskDir.resolve("launcher.log").toFile()));
        pb.redirectErrorStream(true);
        pb.start();

        return script;
    }

    private static List<String> spawnCommand(String terminal, Path script, Path taskDir) {
        String[] prefix = TERMINALS.getOrDefault(terminal, new String[]{terminal, "-e"});
        List<String> cmd = new ArrayList<>(List.of(prefix));

        if ("tmux".equals(terminal)) {
            // A detached session keeps the window around even if nothing is attached,
            // so the run survives and can be attached to afterwards.
            cmd.add("new-session");
            cmd.add("-d");
            cmd.add("-s");
            cmd.add("sqlmap-" + taskDir.getFileName());
            cmd.add("/bin/sh");
            cmd.add(script.toString());
            return cmd;
        }

        if ("xfce4-terminal".equals(terminal)) {
            // xfce4-terminal's -e takes one string rather than an argv tail.
            cmd.add("/bin/sh " + script);
            return cmd;
        }

        cmd.add("/bin/sh");
        cmd.add(script.toString());
        return cmd;
    }
}
