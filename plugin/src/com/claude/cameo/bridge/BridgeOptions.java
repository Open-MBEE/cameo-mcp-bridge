package com.claude.cameo.bridge;

import com.claude.cameo.bridge.util.BridgePort;
import com.nomagic.magicdraw.core.options.AbstractPropertyOptionsGroup;
import com.nomagic.magicdraw.properties.NumberProperty;
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
    }

    public int getPort() {
        Property property = getProperty(PORT);
        return property instanceof NumberProperty
                ? ((NumberProperty) property).getInteger() : BridgePort.DEFAULT;
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
