package com.ecostg.paper.gui;

import java.util.HashMap;
import java.util.Map;

public final class GuiSession {

    private final GuiType type;
    private final Map<String, Object> data = new HashMap<>();

    public GuiSession(GuiType type) {
        this.type = type;
    }

    public GuiType type() {
        return type;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }
}
