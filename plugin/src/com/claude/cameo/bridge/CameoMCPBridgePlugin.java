package com.claude.cameo.bridge;

import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.core.Application;
import com.claude.cameo.bridge.util.BridgeCapabilities;
import com.claude.cameo.bridge.util.BridgeDiagnostics;
import com.claude.cameo.bridge.util.BridgePort;
import javax.swing.SwingUtilities;
import java.util.logging.Logger;

public class CameoMCPBridgePlugin extends Plugin {

    private static final Logger LOG = Logger.getLogger(CameoMCPBridgePlugin.class.getName());
    private HttpBridgeServer server;

    @Override
    public void init() {
        BridgeDiagnostics.setGuiSink(message -> {
            Runnable append = () -> {
                try {
                    Application.getInstance().getGUILog().log(message, false);
                } catch (RuntimeException error) {
                    LOG.fine("Could not append bridge diagnostics to GUI log");
                }
            };
            if (SwingUtilities.isEventDispatchThread()) append.run();
            else SwingUtilities.invokeLater(append);
        });
        try {
            BridgeOptions options = new BridgeOptions();
            Application.getInstance().getEnvironmentOptions().addGroup(options);
            BridgeDiagnostics.log("Registered Cameo MCP Bridge Environment Options (plugin "
                    + BridgeCapabilities.pluginVersion() + ")");
            int port = BridgePort.resolve(System.getProperty("cameo.mcp.port"), options.getPort());
            server = new HttpBridgeServer(port);
            server.start();
            Application.getInstance().getGUILog().log(
                "CameoMCPBridge: HTTP server started on port " + port);
            LOG.info("CameoMCPBridge: HTTP server started on port " + port);
            BridgeDiagnostics.log("plugin=" + BridgeCapabilities.pluginVersion()
                    + " api=" + BridgeCapabilities.API_VERSION
                    + " diagnostics=" + BridgeDiagnostics.enabled()
                    + " java=" + System.getProperty("java.version")
                    + " (diagnostic records omit request bodies and model contents)");
        } catch (Exception e) {
            LOG.severe("CameoMCPBridge: Failed to start HTTP server: " + e.getMessage());
            Application.getInstance().getGUILog().showError(
                "CameoMCPBridge: Failed to start HTTP server: " + e.getMessage());
        }
    }

    @Override
    public boolean close() {
        if (server != null) {
            server.stop();
            LOG.info("CameoMCPBridge: HTTP server stopped");
        }
        BridgeDiagnostics.setGuiSink(null);
        return true;
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
