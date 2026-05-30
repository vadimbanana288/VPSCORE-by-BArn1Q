package io.vpscore.bootstrap;

public enum Mode {
    STANDALONE("standalone", "Полноценный VPS без Minecraft"),
    ATTACH("attach", "Прикрепление к существующему Minecraft-процессу"),
    WRAPPER("wrapper", "Minecraft-сервер внутри VPS Core"),
    MINIMAL("minimal", "Минимальный режим (shell + файлы)");

    private final String key;
    private final String description;

    Mode(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public static Mode fromString(String s) {
        for (var m : values()) {
            if (m.key.equalsIgnoreCase(s)) return m;
        }
        return STANDALONE;
    }

    public String getKey() { return key; }
    public String getDescription() { return description; }
}
