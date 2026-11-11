package uk.co.periculo.burpsqlmap;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Adds "Send to sqlmap" wherever Burp shows a request. */
public class SqlmapContextMenu implements ContextMenuItemsProvider {

    private final SqlmapTab tab;

    public SqlmapContextMenu(SqlmapTab tab) {
        this.tab = tab;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequestResponse> selected = collect(event);
        if (selected.isEmpty()) return null;

        String label = selected.size() == 1
                ? "Send to sqlmap"
                : "Send " + selected.size() + " requests to sqlmap";

        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> {
            tab.addTasks(selected);
            tab.bringToFront();
        });
        return List.of(item);
    }

    private static List<HttpRequestResponse> collect(ContextMenuEvent event) {
        List<HttpRequestResponse> found = new ArrayList<>(event.selectedRequestResponses());
        if (found.isEmpty()) {
            event.messageEditorRequestResponse()
                    .map(editor -> editor.requestResponse())
                    .ifPresent(found::add);
        }
        found.removeIf(rr -> rr == null || rr.request() == null);
        return found;
    }
}
