package dev.skinstudio.service;

import dev.skinstudio.model.SkinModel;

public interface SkinCommandAdapter {
    record GeneratedCommand(String command, String explanation) {}
    GeneratedCommand buildApplyCommand(String outputValue, SkinModel model, String template);
}
