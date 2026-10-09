package com.WynnRunica.gui;

public class Feature {
    public enum Category {
        TRANSLATION,
        TOOLS
    }

    private final String name;
    private final String title;
    private final String description;
    private final String icon;
    private final int color;
    private final Category category;
    private volatile boolean enabled = true;

    public Feature(String name, String title, String description, String icon, int color, Category category) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.color = color;
        this.category = category;
    }

    public void toggle() { enabled = !enabled; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public int getColor() { return color; }
    public Category getCategory() { return category; }
}
