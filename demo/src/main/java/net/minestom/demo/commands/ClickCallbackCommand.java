package net.minestom.demo.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.condition.Conditions;

public class ClickCallbackCommand extends Command {

    private final static ClickCallback.Options CLICK_CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .lifetime(ClickCallback.DEFAULT_LIFETIME) // 12 hours
            .uses(ClickCallback.UNLIMITED_USES) // -1 for Unlimited
            .build();
    private final static Component CLICK_CALLBACK_COMPONENT = Component.text("Click me!")
            .clickEvent(ClickEvent.callback(audience -> audience.sendMessage(Component.text("Callback executed!")), CLICK_CALLBACK_OPTIONS));

    public ClickCallbackCommand() {
        super("callback");

        setCondition(Conditions::playerOnly);
        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(CLICK_CALLBACK_COMPONENT);
        });
    }
}
