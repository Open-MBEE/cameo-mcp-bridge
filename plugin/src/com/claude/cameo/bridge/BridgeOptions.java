package com.claude.cameo.bridge;

import com.claude.cameo.bridge.util.BridgePort;
import com.nomagic.magicdraw.core.options.AbstractPropertyOptionsGroup;
import com.nomagic.magicdraw.properties.NumberProperty;
import com.nomagic.magicdraw.properties.StringProperty;
import com.nomagic.magicdraw.properties.BooleanProperty;
import com.nomagic.magicdraw.properties.Property;
import com.nomagic.magicdraw.icons.IconsFactory;
import javax.swing.Icon;

/** Per-user settings persisted by Cameo's System Options framework. */
public final class BridgeOptions extends AbstractPropertyOptionsGroup {
    public static final String ID = "com.claude.cameo.bridge.options";
    private static final String PORT = "listeningPort";

    public BridgeOptions() {
        super(ID);
    }

    @Override
    public void setDefaultValues() {
        NumberProperty port = new NumberProperty(PORT, BridgePort.DEFAULT, 1, 65535);
        port.setResourceProvider((key, property) -> PORT.equals(key) ? "Listening port" : key);
        port.setGroup("HTTP server");
        port.setDescription("Local HTTP listening port (1-65535). Restart Cameo after changing. "
                + "The cameo.mcp.port VM property overrides this setting. "
                + "Set the MCP client's CAMEO_BRIDGE_PORT to the same value.");
        addProperty(port, true);
        NumberProperty browserPort = new NumberProperty("browserPort", 18741, 1, 65535);
        browserPort.setResourceProvider((key, property) -> "Browser service port");
        browserPort.setGroup("Browser service");
        browserPort.setDescription("Protected loopback browser service. Must differ from the native listening port. Restart Cameo after changing.");
        addProperty(browserPort, true);
        StringProperty origins = new StringProperty("browserOrigins", "https://kotar.app,http://localhost:5173");
        origins.setResourceProvider((key, property) -> "Allowed browser origins");
        origins.setGroup("Browser service");
        origins.setDescription("Comma-separated exact HTTPS origins, or trusted HTTP localhost origins. No paths or trailing slashes. Each connection still requires native approval. Restart Cameo after changing.");
        addProperty(origins, true);
        BooleanProperty browserEnabled = new BooleanProperty("browserEnabled", true);
        browserEnabled.setResourceProvider((key, property) -> "Enable browser service");
        browserEnabled.setGroup("Browser service");
        browserEnabled.setDescription("Start the protected browser service with Cameo. Restart Cameo after changing.");
        addProperty(browserEnabled, true);
    }

    public int getPort() {
        Property property = getProperty(PORT);
        return property instanceof NumberProperty
                ? ((NumberProperty) property).getInteger() : BridgePort.DEFAULT;
    }

    public int getBrowserPort() {
        Property property = getProperty("browserPort");
        return property instanceof NumberProperty ? ((NumberProperty) property).getInteger() : 18741;
    }

    public boolean isBrowserEnabled() {
        Property property = getProperty("browserEnabled");
        return !(property instanceof BooleanProperty) || ((BooleanProperty) property).getBoolean();
    }

    public java.util.Set<String> getBrowserOrigins() {
        Property property = getProperty("browserOrigins");
        String value = property instanceof StringProperty ? ((StringProperty) property).getString() : "https://kotar.app,http://localhost:5173";
        java.util.Set<String> origins = new java.util.HashSet<>();
        for (String origin : value.split(",")) if (!origin.trim().isEmpty()) origins.add(com.claude.cameo.bridge.browser.BrowserProtocol.origin(origin.trim()));
        return origins;
    }

    @Override
    public String getName() {
        return "Cameo MCP Bridge";
    }

    @Override
    public Icon getGroupIcon() {
        return IconsFactory.getIcon("nonenode.png");
    }
}
