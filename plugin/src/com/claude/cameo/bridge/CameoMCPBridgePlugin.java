package com.claude.cameo.bridge;

import com.nomagic.magicdraw.plugins.Plugin;
import com.nomagic.magicdraw.core.Application;
import com.claude.cameo.bridge.util.BridgeCapabilities;
import com.claude.cameo.bridge.util.BridgeDiagnostics;
import com.claude.cameo.bridge.util.BridgePort;
import com.claude.cameo.bridge.util.BridgePlatform;
import javax.swing.SwingUtilities;
import java.util.logging.Logger;

public class CameoMCPBridgePlugin extends Plugin {

    private static final Logger LOG = Logger.getLogger(CameoMCPBridgePlugin.class.getName());
    private HttpBridgeServer server;
    private com.claude.cameo.bridge.browser.BrowserHttpServer browser;

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
            if ("2022x".equals(BridgePlatform.target())) {
                String hostVersion = com.nomagic.magicdraw.emfuml2xmi.v5.EmfUml2XmiPlugin
                        .getInstance().getDescriptor().getVersion();
                if (!BridgePlatform.acceptsHost(hostVersion)) {
                    throw new IllegalStateException("The 2022x bridge build requires a 2022x host; found " + hostVersion);
                }
            }
            BridgeOptions options = new BridgeOptions();
            Application.getInstance().getEnvironmentOptions().addGroup(options);
            BridgeDiagnostics.log("Registered Cameo MCP Bridge Environment Options (plugin "
                    + BridgeCapabilities.pluginVersion() + ")");
            int port = BridgePort.resolve(System.getProperty("cameo.mcp.port"), options.getPort());
            server = new HttpBridgeServer(port);
            server.start();
            // Browser failures must not take down the proven native/desktop bridge.
            if (options.isBrowserEnabled()) {
                try {
                    int browserPort = options.getBrowserPort();
                    if (browserPort == port) throw new IllegalArgumentException("Browser and native ports must differ");
                    com.claude.cameo.bridge.browser.BrowserBridge service = new com.claude.cameo.bridge.browser.BrowserBridge(
                        options.getBrowserOrigins(), new com.claude.cameo.bridge.browser.NativeBrowserBackend(port),
                        new com.claude.cameo.bridge.browser.CameoBrowserApproval(options.getBrowserSessionMinutes()), options.getBrowserSessionMinutes());
                    try { browser = new com.claude.cameo.bridge.browser.BrowserHttpServer(browserPort, service); }
                    catch (Exception error) { service.close(); throw error; }
                    browser.start();
                    Application.getInstance().getGUILog().log("CameoMCPBridge: Browser service started on port " + browserPort);
                } catch (Exception error) {
                    Application.getInstance().getGUILog().showError("CameoMCPBridge: Browser service unavailable: " + error.getMessage()
                        + ". Native/desktop bridge remains available. Stop the old Python connector if it owns the browser port.");
                }
            }
            Application.getInstance().getGUILog().log(
                "CameoMCPBridge: HTTP server started on port " + port);
            LOG.info("CameoMCPBridge: HTTP server started on port " + port);
            BridgeDiagnostics.log("plugin=" + BridgeCapabilities.pluginVersion()
                    + " target=" + BridgePlatform.target()
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
        if (browser != null) browser.close();
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
