package com.claude.cameo.bridge.browser;

import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.*;

/** The browser can request a review, but only a native Cameo dialog authorizes it. */
public final class CameoBrowserApproval implements BrowserBridge.Approval {
    @Override public boolean confirm(String origin, JsonObject plan) throws Exception {
        return confirm(origin, plan, () -> true);
    }
    @Override public boolean confirm(String origin, JsonObject plan, java.util.function.BooleanSupplier pending) throws Exception {
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                String title = plan == null ? "Connect browser client to Cameo" : "Approve Cameo creation plan";
                String text = "Requesting browser client: " + origin + "\n\n" + (plan == null
                    ? "Allow this browser session to read this Cameo project and capture model data?\nModel changes require a separate approval. Access expires after 15 minutes."
                    : "Review this exact stored plan. Only additive packages, Blocks and properties are supported.\nNothing is saved automatically.\n\n" + new GsonBuilder().setPrettyPrinting().create().toJson(plan));
                JTextArea content = new JTextArea(text); content.setEditable(false); content.setLineWrap(true); content.setWrapStyleWord(true); content.setCaretPosition(0);
                JScrollPane scroll = new JScrollPane(content); scroll.setPreferredSize(new Dimension(720, plan == null ? 210 : 560));
                Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
                if (owner == null) for (Window candidate : Window.getWindows()) {
                    if (candidate instanceof Frame && candidate.isShowing()) { owner = candidate; break; }
                }
                JOptionPane pane = new JOptionPane(scroll, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                JDialog dialog = pane.createDialog(owner, title);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
                Timer timeout = new Timer(500, event -> {
                    if (!pending.getAsBoolean() || System.nanoTime() >= deadline) { pane.setValue(JOptionPane.CANCEL_OPTION); dialog.dispose(); }
                });
                timeout.start();
                try { dialog.setVisible(true); answer.complete(Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue())); }
                finally { timeout.stop(); dialog.dispose(); }
            } catch (Exception error) { answer.completeExceptionally(error); }
        });
        return answer.get(125, TimeUnit.SECONDS);
    }
}
