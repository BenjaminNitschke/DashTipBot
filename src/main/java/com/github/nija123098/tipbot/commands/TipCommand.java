package com.github.nija123098.tipbot.commands;

import com.github.nija123098.tipbot.AbstractCommand;
import com.github.nija123098.tipbot.Command;
import com.github.nija123098.tipbot.Database;
import com.github.nija123098.tipbot.Main;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import com.github.nija123098.tipbot.utility.TransactionLog;
import com.github.nija123098.tipbot.utility.Unit;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IReaction;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.RequestBuffer;

import java.io.IOException;
import java.util.*;

import static com.github.nija123098.tipbot.Database.BALANCES_TABLE;

public class TipCommand extends AbstractCommand {
    //TODO: just makes it confusing, all this bot should support is !tip !deposit !balance and !withdrawal
    @Override
    public List<String> getNames() {
        return Arrays.asList("tip", "give", "gift", "donate", "grant");
    }

    @Override
    public String getHelp() {
        return "Tip a user an amount of Dash which they may withdraw.";
    }

    @Override
    public String getFullHelp() {
        return "Tip a user an amount of Dash which they may withdraw.\n" +
                "You may specify a Dash, USD, beer, or other amount.\n" +
                "Examples:\n" +
                "    " + Main.PREFIX + "tip " + Main.MAINTAINER.mention() + " a " + Unit.COFFEE.name().toLowerCase() + "\n" +
                "    " + Main.PREFIX + "tip " + Main.MAINTAINER.mention() + " 2 " + Unit.GBP + "\n" +
                "    " + Main.PREFIX + "tip " + Main.MAINTAINER.mention() + " 1 " + Unit.USD + "\n" +
                "    " + Main.PREFIX + "tip " + Main.MAINTAINER.mention() + " .01 Dash";
    }

    //TODO: Code issue: The method getCommand() has an NPath complexity of 2160
    @Override
    public Command getCommand() {
        return ((invoker, arguments, channel) -> {
            BalanceCommand.update(invoker);
            List<IUser> recipients = new ArrayList<>();
            IUser user;
            for (String argument : arguments) {
                //TODO: no idea how the user should figure this out, only works if <@username> is used, not simple mention and not @username (default in discord)
                //TODO: also wasteful to check all arguments, this should be a regex
                user = Main.getUserFromMention(argument);//TODO: only format that works is <@id>
                //TODO: why break the loop? couldn't the user be mentioned later?
                //TODO: Code issue: Avoid using a branching statement as the last in a loop.
                if (user == null) break;
                recipients.add(user);
            }
            for (IUser recipient : recipients){
                //TODO: doesn't work, our user is the user id, not the name typed in chat
                if (recipient.equals(invoker)) return "You can't tip yourself.";
                if (recipient.isBot()) return "You can't tip a bot";
            }
            if (recipients.isEmpty()) return "Please specify a user by mentioning him or her, then a number and unit.";
            String combined = arguments[1] + " ";
            if (arguments.length == 3) combined += arguments[2];
            combined = combined.toLowerCase();
            Unit unit = null;
            int index;
            String lowerName;
            for (String unitName : Unit.getNames()){
                lowerName = unitName.toLowerCase();
                index = combined.indexOf(lowerName);
                if (index == -1) continue;
                unit = Unit.getUnitForName(unitName);
                combined = combined.replace(lowerName, "").replace(" ", "");
                break;
            }
            if (unit == null) return "I was unable to find a unit to tip in.";
            double amount;
            if (combined.isEmpty()) amount = 1;
            else try {
                amount = Double.parseDouble(combined);
            } catch (NumberFormatException e){
                return "Please specify an amount to tip.";
            }
            String ret = completeTransaction(invoker, channel.getGuild(), amount, unit, recipients);
            if (ret != null) return ret;
            return Main.OK_HAND;
        });
    }
    //TODO: Code issue: Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes.
    private static final Map<Long, Double> TIP_AMOUNT = new HashMap<>();
    private static final Map<Long, IMessage> TALLY_MESSAGE = new HashMap<>();
    public static void handleReaction(ReactionAddEvent event) throws IOException, InterruptedException {
        String[] name = event.getReaction().getEmoji().getName().split("_");
        double amount;
        try {
            amount = Double.parseDouble(name[1]);
        } catch (NumberFormatException ignored){
            return;
        }
        Unit unit = Unit.getUnitForName(name[2]);
        if (unit == null) return;
        if (completeTransaction(event.getAuthor(), event.getGuild(), amount, unit, Collections.singleton(event.getMessage().getAuthor())) != null){
            TIP_AMOUNT.compute(event.getMessageID(), (aLong, aDouble) -> (aDouble == null ? 0 : aDouble) + amount * unit.getDashAmount());
            if (event.getMessage().getReactions().stream().map(IReaction::getCount).reduce((integer, integer2) -> integer + integer2).orElse(0) > 3){
                RequestBuffer.request(() -> {
                    TALLY_MESSAGE.computeIfAbsent(event.getMessageID(), aLong -> event.getChannel().sendMessage("Tallying tips for " + event.getAuthor().mention() + "'s message"));
                    RequestBuffer.request(() -> TALLY_MESSAGE.get(event.getMessageID()).edit("A total of " + TIP_AMOUNT.get(event.getMessageID()) + " Dash has been tipped to " + event.getAuthor().mention()));
                });
            }
            TIP_AMOUNT.compute(event.getMessageID(), (aLong, aDouble) -> (aDouble == null ? 0 : aDouble) + amount * unit.getDashAmount());
        }
    }
    private static String completeTransaction(IUser invoker, IGuild guild, double amount, Unit unit, Collection<IUser> recipients) throws IOException, InterruptedException {
        double currentWallet = Double.parseDouble(Database.getValue(BALANCES_TABLE, invoker, "0"));
        double tipAmount = unit.getDashAmount() * amount;
        if (tipAmount * recipients.size() > currentWallet) return "You don't have enough for that.";
        Database.setValue(BALANCES_TABLE, invoker, String.valueOf(currentWallet - tipAmount * recipients.size()));
        for (IUser recipient : recipients){
            Database.setValue(BALANCES_TABLE, recipient, String.valueOf(Double.valueOf(Database.getValue(BALANCES_TABLE, recipient, "0")) + tipAmount));
            TransactionLog.log("tip of " + tipAmount + " from " + invoker.getStringID() + " to " + recipient.getStringID());
            tipAnnounce(invoker, recipient, guild, amount, unit);
        }
        return null;
    }
    //TODO: would be better if all this is done in a public channel, not special some announcement channel
    private static void tipAnnounce(IUser invoker, IUser recipient, IGuild guild, double amount, Unit unit){
        String channel = Database.getValue(Database.ANNOUNCEMENT_CHANNEL, guild, "NULL");
        //TODO: code issue: Position literals first in String comparisons
        if (channel.equals("NULL")) return;
        IChannel dest = guild.getChannelByID(Long.parseLong(channel));
        if (dest != null) dest.sendMessage(invoker.mention() + " tipped " + recipient.mention() + " " + unit.display(amount));
    }
}
