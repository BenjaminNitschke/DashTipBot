package com.github.nija123098.tipbot.commands;

import com.github.nija123098.tipbot.AbstractCommand;
import com.github.nija123098.tipbot.Command;
import com.github.nija123098.tipbot.Database;
import com.github.nija123098.tipbot.utility.Config;
import com.github.nija123098.tipbot.utility.TransactionLog;
import com.github.nija123098.tipbot.utility.Unit;
import sx.blah.discord.handle.obj.IUser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.github.nija123098.tipbot.Database.BALANCES_TABLE;
import static com.github.nija123098.tipbot.Database.RECEIVED_TABLE;
import static com.github.nija123098.tipbot.Database.RECEIVING_ADDRESSES_TABLE;

public class BalanceCommand extends AbstractCommand {
    @Override
    public String getHelp() {
        return "Gets your balance.";
    }
    @Override
    public Command getCommand() {
        return (invoker, arguments, channel) -> {
            update(invoker);
            //TODO: again, should be BigDecimal
            double amount = Double.parseDouble(Database.getValue(BALANCES_TABLE, invoker, "0"));
            //TODO: due to no caching of the usd api call this command is slow
            Unit displayUnit = Unit.getUnitForName(Database.getValue(Database.PREFERRED_CURRENCY, invoker, "USD"));
            return Unit.displayAmount(amount, 4) + " Dash which is worth " + displayUnit.display(amount / displayUnit.getDashAmount());
        };
    }

    //TODO: Use explicit scoping instead of the default package private level
    static void update(IUser user) throws IOException, InterruptedException {
        String receivingAddress = Database.getValue(RECEIVING_ADDRESSES_TABLE, user, null);
        if (receivingAddress == null) return;
        //TODO: waiting for 6 confirmation is madness, thought this thing is broken at first, horrible user experience and for testing
        Process process = new ProcessBuilder("dash-cli", "-rpcuser="+ Config.RPC_USER, "-rpcpassword="+ Config.RPC_PASS, "getreceivedbyaddress", receivingAddress, "1").start();//"6").start();
        String s = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
        if (s == null || s.startsWith("e")) return;//TODO: bad way of error checking
        String previous = Database.getValue(BALANCES_TABLE, user, "0");
        //TODO: all amounts should be BigDecimal, using double is not safe for crypto!
        Double addToBalance = Double.parseDouble(s) - Double.parseDouble(previous);
        //TODO: we should display mDASH as 1 DASH is too much for most purposes, even 1 mDASH is almost $1 by now ..
        if (addToBalance < .000001D) return;
        TransactionLog.log("adding " + addToBalance + " to balance for user " + user.getStringID());
        Database.setValue(RECEIVED_TABLE, user, s);
        Database.setValue(BALANCES_TABLE, user, String.valueOf(Double.valueOf(previous) + addToBalance));
    }
}
