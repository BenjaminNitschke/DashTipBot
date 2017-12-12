package com.github.nija123098.tipbot;

import com.github.nija123098.tipbot.commands.TipCommand;
import org.reflections.Reflections;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;
//TODO: unused import
import sx.blah.discord.util.Image;
import sx.blah.discord.util.PermissionUtils;
import sx.blah.discord.util.RequestBuffer;
import com.github.nija123098.tipbot.utility.Config;
import com.github.nija123098.tipbot.utility.InputException;
import com.github.nija123098.tipbot.utility.WrappingException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    //TODO: before we start: this code is super hard to test, debugging is the only option after reading errors in the log
    //TODO: also setup and first user experience is not very thought out, many strange quirks users won't understand at first (e.g. how to mention a user for a tip seems broken)
    //TODO: general problems: if something is not setup (config, db, dash-cli, etc.), it just crashes with NullReferenceException or things stay null and are not working!
    //TODO: tons of global variables, very unclean code
    public static final String OK_HAND = "👌", PONG = "🏓";
    public static final String PREFIX = "~";
    private static final String BOT_MENTION;
    public static final IUser MAINTAINER;
    public static final IDiscordClient DISCORD_CLIENT;
    public static final AtomicInteger COMMANDS_OCCURRING = new AtomicInteger();
    public static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean();
    private static final Map<String, Command> COMMAND_MAP = new HashMap<>();
    public static final Map<String, String> HELP_MAP = new HashMap<>();
    public static final Map<String, String> FULL_HELP_MAP = new HashMap<>();
    private static final ScheduledThreadPoolExecutor SCHEDULED_THREAD_EXECUTOR = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "Scheduled-Executor-Thread");
        thread.setDaemon(true);
        return thread;
    });
    //TODO: Why is this in the static constructor? Crashes here crash the whole runtime
    static {
        try {
            Config.setUp();
            DISCORD_CLIENT = new ClientBuilder().withToken(Config.TOKEN).login();
            DISCORD_CLIENT.getDispatcher().registerListener(Main.class);
            DISCORD_CLIENT.getDispatcher().waitFor(ReadyEvent.class);
            MAINTAINER = DISCORD_CLIENT.getUserByID(302494387366264832L);//TODO: would be better in config //Jack: 191677220027236352L, DeltaEngine: 302494387366264832L
            BOT_MENTION = DISCORD_CLIENT.getOurUser().mention();
            new Reflections("com.github.nija123098.tipbot.commands").getSubTypesOf(AbstractCommand.class).stream().map((clazz) -> {
                try{return clazz.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    e.printStackTrace();
                }
                return null;
            }).filter(Objects::nonNull).forEach((command) -> command.getNames().forEach(name -> {
                COMMAND_MAP.put(name, command.getCommand());
                HELP_MAP.put(name, command.getHelp());
                FULL_HELP_MAP.put(name, command.getFullHelp());
            }));
        } catch (Exception e){
            //TODO: Code issue: Avoid throwing raw exception types.
            throw new RuntimeException(e);
        }
    }

    //TODO: Code issue: Document empty method body
    public static void main(String[] args) {}

    //TODO: code issue: The method handle() has an NPath complexity of 5760
    @EventSubscriber
    public static void handle(MessageReceivedEvent event){
        if (event.getAuthor().isBot()) return;
        String content = event.getMessage().getContent();
        if (content.startsWith(PREFIX)) content = content.substring(PREFIX.length());
        else if (content.startsWith(BOT_MENTION)) content = content.substring(BOT_MENTION.length());
        //TODO: why this restriction? can we only talk privately to bot via pm? why not in channel? also no error message?
        else if (!event.getChannel().isPrivate()) return;
        if (!PermissionUtils.hasPermissions(event.getChannel(), DISCORD_CLIENT.getOurUser(), Permissions.SEND_MESSAGES, Permissions.READ_MESSAGES)) return;
        if (!PermissionUtils.hasPermissions(event.getChannel(), DISCORD_CLIENT.getOurUser(), Permissions.ADD_REACTIONS)) {
            RequestBuffer.request(() -> event.getChannel().sendMessage("I need to be able to send reactions in this channel in order to operate."));
            return;
        }
        //TODO: code issue: Use one line for each declaration, it enhances code readability.
        int length, newLength;
        do {
            length = content.length();
            content = content.replace("  ", " ");
            newLength = content.length();
        } while (length != newLength);
        if (content.startsWith(" ")) content = content.substring(1);
        String[] split = content.split(" ");
        Command command = COMMAND_MAP.get(split[0].toLowerCase());
        synchronized (SHUTTING_DOWN) {
            if (command == null || SHUTTING_DOWN.get()) return;
            COMMANDS_OCCURRING.incrementAndGet();
        }
        //TODO: if anything goes wrong here, user is not informed and won't know why a command failed
        try {
            String ret = command.invoke(event.getAuthor(), Arrays.copyOfRange(split, 1, split.length), event.getChannel());
            if (ret == null || ret.isEmpty()) return;
            if (ret.equals(OK_HAND) || ret.equals(PONG)) RequestBuffer.request(() -> event.getMessage().addReaction(ReactionEmoji.of(ret)));
            else RequestBuffer.request(() -> event.getChannel().sendMessage(ret));
        } catch (Exception e){
            //TODO: code issue: An instanceof check is being performed on the caught exception. Create a separate catch clause for this exception type.
            if (e instanceof InputException) event.getChannel().sendMessage(e.getMessage());
            else issueReport(event, e instanceof WrappingException ? (Exception) e.getCause() : e);
        }
        COMMANDS_OCCURRING.decrementAndGet();
    }

    @EventSubscriber
    public static void handle(ReactionAddEvent event){
        if (!event.getReaction().getEmoji().getName().startsWith("tip_")) return;
        SCHEDULED_THREAD_EXECUTOR.schedule(() -> {
            if (event.getReaction().getUserReacted(event.getUser())) try {
                TipCommand.handleReaction(event);
            } catch (IOException | InterruptedException e) {
                issueReport(event, e);
            }
        }, 30, TimeUnit.SECONDS);
    }

    public static IUser getUserFromMention(String mention){
        if (!(mention.startsWith("<@") && mention.endsWith(">"))) return null;
        //TODO: Code issue: Avoid reassigning parameters such as 'mention'
        mention = mention.replace("!", "");
        try {
            //TODO: only works for users on server I guess, pretty bad way to find user, parsing id, back to IUser object, why?
            return DISCORD_CLIENT.getUserByID(Long.parseLong(mention.substring(2, mention.length() - 1)));
        } catch (NumberFormatException e){
            throw new InputException("Please mention the user.");
        }
    }

    private static void issueReport(MessageEvent event, Exception e){
        RequestBuffer.request(() -> event.getAuthor().getOrCreatePMChannel().sendMessage("Something went wrong while executing your command, I am notifying my maintainer now."));
        //TODO: all this funny language is not very helpful: You moron, I just caught ..
        RequestBuffer.request(() -> MAINTAINER.getOrCreatePMChannel().sendMessage("Caught a " + e.getClass().getSimpleName() + " due to " + e.getMessage()));
        e.printStackTrace();
    }
}
