package com.github.nija123098.tipbot.commands;

import com.github.nija123098.tipbot.AbstractCommand;
import com.github.nija123098.tipbot.Command;
import com.github.nija123098.tipbot.Main;

public class PingCommand extends AbstractCommand {
    //TODO: this is an example command, not needed for a tip bot!
    @Override
    public String getHelp() {
        return "Pong.";
    }

    @Override
    public Command getCommand() {
        return (invoker, arguments, channel) -> Main.PONG;
    }
}
