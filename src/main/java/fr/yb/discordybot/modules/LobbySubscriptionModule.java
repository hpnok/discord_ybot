package fr.yb.discordybot.modules;

import fr.yb.discordybot.BotModule;
import fr.yb.discordybot.gg2.LobbyData;
import fr.yb.discordybot.gg2.LobbyReader;
import fr.yb.discordybot.gg2.LobbyServerData;
import fr.yb.discordybot.model.LobbySubModel;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Nicolas
 */
public class LobbySubscriptionModule extends BotModule {
    
    private int previousPlayerCount = 0;
    private Timer timer;
    
    public static final String COMMAND_ROOT = "sub";
    public static final String COMMAND_ADD = COMMAND_ROOT + " add";
    public static final String COMMAND_ADD_CHANNEL = COMMAND_ROOT + " addchan";
    public static final String COMMAND_REMOVE = COMMAND_ROOT + " remove";
    public static final String COMMAND_REMOVE_CHANNEL = COMMAND_ROOT + " removechan";
    public static final String COMMAND_LIST = COMMAND_ROOT + " show";
    public static final String COMMAND_LIST_CHANNEL = COMMAND_ROOT + " showchan";
    public static final String COMMAND_COOLDOWN = COMMAND_ROOT + " cool";
    public static final String COMMAND_COOLDOWN_CHANNEL = COMMAND_ROOT + " coolchan";
    
    public static final String COMMAND_STATUS = COMMAND_ROOT + " status";
    public static final String COMMAND_START = COMMAND_ROOT + " start";
    public static final String COMMAND_STOP = COMMAND_ROOT + " stop";
    
    public static final String NOTIFY_TEXT = "Gang Garrison 2 is alive with %d players online!";
    
    public class LobbyTask extends TimerTask {
    
        protected LobbySubscriptionModule module;

        public LobbyTask(LobbySubscriptionModule module) {
            this.module = module;
        }

        @Override
        public void run() {
            try {
                this.module.updateCountFromLobby();
            } catch (IOException ex) {
                Logger.getLogger(LobbySubscriptionModule.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void stop() {
        this.stopRequests();
        super.stop();
    }

    @Override
    public void start() {
        super.start();
        this.startRequests();
    }
    
    
    
    public void startRequests() {
        if (this.timer != null) {
            return;
        }
        this.timer = new Timer();
        this.timer.schedule(new LobbyTask(this), 0, 30 * 1000);
    }
    
    public void stopRequests() {
        if (this.timer != null) {        
            this.timer.cancel();
            this.timer = null;
        }
    }
    
    public void updateCountFromLobby() throws IOException {
        Socket s = LobbyReader.sendLobbyRequest();
        LobbyData datagram = LobbyReader.readResponse(s);
        int nbPlayers = 0;
        for (LobbyServerData serverDatagram : datagram.getServerData()) {
            nbPlayers += serverDatagram.getPlayers();
        }
        
        
        final int prevNb = this.previousPlayerCount;
        final int currNb = nbPlayers;
        Date now = new Date();
        this.getBot().getModel().getLobbySubscriptions().forEach((k,v) -> {
            try {
                // cooldown
                Instant cooldownOver = v.getLastPost().toInstant().plusSeconds(v.getCooldown().getSeconds());
                if (now.toInstant().isBefore(cooldownOver)) {
                    return;
                }
                // if previously was under threshold, but now is above, notify
                if ((prevNb < v.getLevel()) && (currNb > v.getLevel())) {
                    long longID = Long.parseLong(v.getId());
                    String notification = String.format(NOTIFY_TEXT, currNb);
                    if (v.isChannel()) {
                        IChannel chan = this.getBot().getClient().getChannelByID(longID);
                        if (chan != null) {
                            chan.sendMessage(notification);
                            v.setLastPost(now);
                        } else {
                            Logger.getLogger(LobbySubscriptionModule.class.getName()).log(Level.WARNING, "Unknown channel ID " + longID);
                        }
                    } else {
                        IUser target = this.getBot().getClient().getUserByID(longID);
                        if (target != null) {
                            IChannel chan = target.getOrCreatePMChannel();
                            if (chan != null) {
                                chan.sendMessage(notification);
                                v.setLastPost(now);
                            } else {
                                Logger.getLogger(LobbySubscriptionModule.class.getName()).log(Level.WARNING, "Cannot get channel for user ID {0}", longID);
                            }
                        } else {
                            Logger.getLogger(LobbySubscriptionModule.class.getName()).log(Level.WARNING, "Unknown user ID {0}", longID);
                        }
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(LobbySubscriptionModule.class.getName()).log(Level.SEVERE, "Uncaught exception in the lobby subscription loop", ex);
            }
        });
        
        this.previousPlayerCount = nbPlayers;
    }

    @Override
    public boolean handle(MessageReceivedEvent t) {
        String cmd = t.getMessage().getContent().toLowerCase();
        String reply = this.help();
        
        // do the chans first because they're longer
        if (cmd.contains(COMMAND_ADD_CHANNEL)) {
            if (!t.getAuthor().getPermissionsForGuild(t.getGuild()).contains(Permissions.ADMINISTRATOR)) {
                reply = "You need to be administrator to run this command!";
            } else {
                String secondPart = cmd.substring(cmd.indexOf(COMMAND_ADD_CHANNEL) + COMMAND_ADD_CHANNEL.length() + 1);
                try {
                    String longID = String.valueOf(t.getChannel().getLongID());
                    int value = Integer.parseUnsignedInt(secondPart);

                    LobbySubModel mod = new LobbySubModel();
                    mod.setChannel(true);
                    mod.setLevel(value);
                    mod.setId(longID);

                    this.getBot().getModel().putLobbySubscription(mod);

                    reply = String.format(
                        "Subscribed this channel to gg2 lobby. I will send a message here in #%s when there are at least %d people online on GG2!",
                        t.getChannel().getName(),
                        value
                    );

                } catch(NumberFormatException ex) {
                    reply = "Wrong number format. Expected command: " + this.getBot().getConfig().getPrefix() + COMMAND_ADD_CHANNEL + " <nbPlayers>";
                }
            }
        } else if (cmd.contains(COMMAND_COOLDOWN_CHANNEL)) {
            if (!t.getAuthor().getPermissionsForGuild(t.getGuild()).contains(Permissions.ADMINISTRATOR)) {
                reply = "You need to be administrator to run this command!";
            } else {
                try {
                    String secondPart = cmd.substring(cmd.indexOf(COMMAND_COOLDOWN_CHANNEL) + COMMAND_COOLDOWN_CHANNEL.length() + 1);
                    String longID = String.valueOf(t.getChannel().getLongID());
                    LobbySubModel mod = this.getBot().getModel().getLobbySubscriptions().get(longID);
                    // make sure a subscription exists
                    if (mod == null) {
                        reply = "This channel does not have a gg2lobby subscription set. "
                                + "Please run `"+ this.getBot().getConfig().getPrefix() + COMMAND_ADD_CHANNEL+"`"
                                + " before updating cooldown settings.";
                    } else {
                        // all good, do the thing, start parsing
                        boolean isHours = false;
                        Character lastChar = secondPart.toLowerCase().charAt(secondPart.length()-1);
                        if (Character.isAlphabetic(lastChar)) {
                            switch (lastChar) {
                                case 'h':
                                    isHours = true;
                                    break;
                                case 'm':
                                    break;
                                default:
                                    throw new NumberFormatException();
                            }
                            secondPart = secondPart.substring(0, secondPart.length() - 1);
                        }
                        int value = Integer.parseUnsignedInt(secondPart);

                        // hours offset
                        if (isHours) {
                            mod.setCooldown(Duration.ofHours(value));
                        } else {
                            mod.setCooldown(Duration.ofMinutes(value));
                        }
                        
                        // update
                        this.getBot().getModel().putLobbySubscription(mod);

                        reply = String.format(
                            "Set notification cooldown for #%s to %d %s!",
                            t.getChannel().getName(),
                            value,
                            (isHours ? "hours" : "minutes")
                        );
                    }

                } catch(NumberFormatException ex) {
                    reply = "Wrong number format. Expected command: " + this.getBot().getConfig().getPrefix() + COMMAND_COOLDOWN_CHANNEL + " <minutes> (or <hours>h)";
                } catch(StringIndexOutOfBoundsException ex) {
                    reply = "Wrong format. Expected command: " + this.getBot().getConfig().getPrefix() + COMMAND_COOLDOWN_CHANNEL + " <minutes> (or <hours>h)";
                }
            }
        } else if (cmd.contains(COMMAND_REMOVE_CHANNEL)) {
            if (!t.getAuthor().getPermissionsForGuild(t.getGuild()).contains(Permissions.ADMINISTRATOR)) {
                reply = "You need to be administrator to run this command!";
            } else {
                String longID = String.valueOf(t.getChannel().getLongID());
                this.getBot().getModel().getLobbySubscriptions().remove(longID);

                reply = String.format(
                    "This channel (%s) will no longer receive messages about gg2 players.",
                    t.getChannel().getName()
                );
            }
        } else if (cmd.contains(COMMAND_LIST_CHANNEL)) {
            String longID = String.valueOf(t.getChannel().getLongID());
            LobbySubModel mod = this.getBot().getModel().getLobbySubscriptions().get(longID);

            reply = String.format(
                "This channel (%s) will receive messages when there are at least %d players on gg2. Cooldown is %d hours %d minutes.",
                t.getChannel().getName(),
                mod.getLevel(),
                mod.getCooldown().getSeconds() / 3600,
                (mod.getCooldown().getSeconds() / 60) % 60
            );
        }
        
        // user commands now
        else 
        if (cmd.contains(COMMAND_ADD)) {
            String secondPart = cmd.substring(cmd.indexOf(COMMAND_ADD) + COMMAND_ADD.length() + 1);
            Logger.getLogger(LobbySubscriptionModule.class.getName()).log(Level.INFO, "secondPart " + secondPart);
            try {
                String longID = String.valueOf(t.getAuthor().getLongID());
                int value = Integer.parseUnsignedInt(secondPart);
                
                LobbySubModel mod = new LobbySubModel();
                mod.setChannel(false);
                mod.setLevel(value);
                mod.setId(longID);
                
                this.getBot().getModel().putLobbySubscription(mod);
                
                reply = String.format(
                    "You subscribed to gg2 lobby. I will send you a DM when there are at least %d people online on GG2!",
                    value
                );
                
            } catch(NumberFormatException ex) {
                reply = "Wrong number format. Expected command: " + COMMAND_ADD + " <nbPlayers>";
            }
        } else if (cmd.contains(COMMAND_COOLDOWN)) {
            try {
                String secondPart = cmd.substring(cmd.indexOf(COMMAND_COOLDOWN) + COMMAND_COOLDOWN.length() + 1);
                String longID = String.valueOf(t.getAuthor().getLongID());
                LobbySubModel mod = this.getBot().getModel().getLobbySubscriptions().get(longID);
                // make sure a subscription exists
                if (mod == null) {
                    reply = "You do not have a gg2lobby subscription set. "
                            + "Please run `"+ this.getBot().getConfig().getPrefix() + COMMAND_ADD+"`"
                            + " before updating your personal cooldown settings.";
                } else {
                    // all good, do the thing, start parsing
                    boolean isHours = false;
                    Character lastChar = secondPart.toLowerCase().charAt(secondPart.length()-1);
                    if (Character.isAlphabetic(lastChar)) {
                        switch (lastChar) {
                            case 'h':
                                isHours = true;
                                break;
                            case 'm':
                                break;
                            default:
                                throw new NumberFormatException();
                        }
                        secondPart = secondPart.substring(0, secondPart.length() - 1);
                    }
                    int value = Integer.parseUnsignedInt(secondPart);

                    // hours offset
                    if (isHours) {
                        mod.setCooldown(Duration.ofHours(value));
                    } else {
                        mod.setCooldown(Duration.ofMinutes(value));
                    }

                    // update
                    this.getBot().getModel().putLobbySubscription(mod);

                    reply = String.format(
                        "Set notification cooldown for #%s to %d %s!",
                        t.getChannel().getName(),
                        value,
                        (isHours ? "hours" : "minutes")
                    );
                }

            } catch(NumberFormatException ex) {
                reply = "Wrong number format. Expected command: " + this.getBot().getConfig().getPrefix() + COMMAND_COOLDOWN + " <minutes> (or <hours>h)";
            } catch(StringIndexOutOfBoundsException ex) {
                reply = "Wrong format. Expected command: " + this.getBot().getConfig().getPrefix() + COMMAND_COOLDOWN + " <minutes> (or <hours>h)";
            }
        } else if (cmd.contains(COMMAND_REMOVE)) {
            String longID = String.valueOf(t.getAuthor().getLongID());
            this.getBot().getModel().getLobbySubscriptions().remove(longID);

            reply = "You will no longer receive messages about gg2 players.";
        } else if (cmd.contains(COMMAND_LIST)) {
            String longID = String.valueOf(t.getAuthor().getLongID());
            LobbySubModel mod = this.getBot().getModel().getLobbySubscriptions().get(longID);
            
            if (mod == null) {
                reply = "You did not subscribe to the gg2lobby. Use `"+this.getFullCommand()+" add <nbPlayers>` to get messages when there are at least <nbPlayers> online on gg2!";
            } else {
                reply = String.format(
                    "You will receive messages when there are at least %d players on gg2. Cooldown is %d hours %d minutes.",
                    mod.getLevel(),
                    mod.getCooldown().getSeconds() / 3600,
                    (mod.getCooldown().getSeconds() / 60) % 60
                );
            }
        }
        
        else if (this.getUtil().isMessageFromOwner(t)) {
            if (cmd.contains(COMMAND_STATUS)) {
                if (this.timer == null) {
                    reply = "Timer null, must have stopped";
                } else {
                    reply = "Timer running";
                }
            } else if (cmd.contains(COMMAND_START)) {
                this.startRequests();
                reply = "Restarted timer";
            } else if (cmd.contains(COMMAND_STOP)) {
                this.stopRequests();
                reply = "Stopped timer";
            } else {
                reply = "I didn't get that. I can understand one of the following: `"
                    + COMMAND_ADD + ", " + COMMAND_LIST + ", " + COMMAND_REMOVE + ", " + COMMAND_COOLDOWN + ", "
                    + COMMAND_ADD_CHANNEL + ", " + COMMAND_LIST_CHANNEL + ", " + COMMAND_REMOVE_CHANNEL + ", " + COMMAND_COOLDOWN_CHANNEL + ", "
                    + COMMAND_STATUS + ", " + COMMAND_START + ", " + COMMAND_STOP
                    + "`.";
            }
        }
        
        // else uhhh
        else {
            reply = "I didn't get that. I can understand one of the following: `"
                    + COMMAND_ADD + ", " + COMMAND_LIST + ", " + COMMAND_REMOVE + ", " + COMMAND_COOLDOWN + ", "
                    + COMMAND_ADD_CHANNEL + ", " + COMMAND_LIST_CHANNEL + ", " + COMMAND_REMOVE_CHANNEL + ", " + COMMAND_COOLDOWN_CHANNEL
                + "`.";
        }
        t.getChannel().sendMessage(reply);
        return false;
    }

    @Override
    public String help() {
        return "**LobbySubscriptionModule**: Makes "+this.getUtil().getName()+" notify you when there are "
                + "at least X people playing Gang Garrison 2. Commands: `"
                + COMMAND_ADD + "`, `" + COMMAND_LIST + "`, `" + COMMAND_REMOVE
                + "`. Comes with a default cooldown of 60 minutes, change it with `" + COMMAND_COOLDOWN
                + "`. Add an extra `chan` to the command to notify a channel "
                + "instead of getting a DM, if you're the server admin (ie. `"
                + COMMAND_ADD_CHANNEL + "`).\n";
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public String getCommand() {
        return COMMAND_ROOT;
    }
}
