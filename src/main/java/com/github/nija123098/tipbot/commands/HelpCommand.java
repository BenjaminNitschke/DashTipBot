package com.github.nija123098.tipbot.commands;

import com.github.nija123098.tipbot.AbstractCommand;
import com.github.nija123098.tipbot.Command;
import com.github.nija123098.tipbot.Main;

public class HelpCommand extends AbstractCommand {
    @Override
    public String getHelp() {
        return "Gets general help or more help on a specific command";
    }

    @Override
    public Command getCommand() {
        return (invoker, arguments, channel) -> {
            if (arguments.length == 0){
                StringBuilder builder = new StringBuilder();
                Main.HELP_MAP.forEach((name, help) -> builder.append(name).append(": ").append(help).append("\n"));
                return builder.toString();
            }else return Main.FULL_HELP_MAP.get(arguments[0].toLowerCase());
        };
    }
}
