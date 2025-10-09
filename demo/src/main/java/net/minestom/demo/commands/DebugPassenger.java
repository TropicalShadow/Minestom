package net.minestom.demo.commands;

import net.minestom.demo.entity.ChickenCreature;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;

public class DebugPassenger extends Command {
    public DebugPassenger() {
        super("debugpassenger");
        setCondition(Conditions::playerOnly);
        setDefaultExecutor(this::execute);
    }

    private void execute(CommandSender sender, CommandContext context) {
        Player player = (Player) sender;
        ChickenCreature chicken = new ChickenCreature();
        chicken.setInstance(((Player) sender).getInstance(), player.getPosition()).join();
        chicken.addPassenger(player);
    }
}
