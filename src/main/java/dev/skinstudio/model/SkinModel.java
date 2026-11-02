package dev.skinstudio.model;

public enum SkinModel {
    CLASSIC("classic"), SLIM("slim"), UNKNOWN("unknown");

    private final String commandValue;

    SkinModel(String commandValue) {
        this.commandValue = commandValue;
    }

    public String commandValue() {
        return commandValue;
    }
}
