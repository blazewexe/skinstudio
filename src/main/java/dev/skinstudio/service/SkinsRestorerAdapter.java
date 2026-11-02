package dev.skinstudio.service;

import dev.skinstudio.model.SkinModel;

public final class SkinsRestorerAdapter implements SkinCommandAdapter {
    @Override public GeneratedCommand buildApplyCommand(String outputValue, SkinModel model, String template) {
        if (outputValue == null || !outputValue.startsWith("https://textures.minecraft.net/texture/")) {
            throw new IllegalArgumentException("The upload provider did not return a supported Minecraft texture URL.");
        }
        if (model == SkinModel.UNKNOWN) throw new IllegalArgumentException("Choose Classic or Slim before applying this skin.");
        String command = template.replace("{value}", outputValue).replace("{model}", model.commandValue()).trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (!command.contains(outputValue) || command.isBlank() || command.contains("\n") || command.contains("\r")) {
            throw new IllegalArgumentException("Command template must include {value} and fit on one line.");
        }
        if (command.length() > 256) throw new IllegalArgumentException("The generated command is longer than Minecraft's chat limit.");
        return new GeneratedCommand(command, "SkinsRestorer URL command; the server may deny permission or restrict this texture host.");
    }
}
