package fr.yb.discordybot.modules;

import fr.yb.discordybot.BotModule;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage.Attachment;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.Image;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Nicolas
 */
public class ProfileModule extends BotModule {
    
    @Override
    public boolean handle(MessageReceivedEvent t) {
        try {
            String item, msg, msgLower, reply = null;
            msg = t.getMessage().getContent();
            msgLower = msg.toLowerCase();
            
            String cmdNick = this.getFullCommand() + " nick";
            String cmdPlay = this.getFullCommand() + " play";
            
            if (msgLower.startsWith(cmdNick)) {
                item = msg.substring(cmdNick.length()).trim();
                t.getGuild().setUserNickname(this.getClient().getOurUser(), item);
                reply = String.format("Set nickname to `%s`!", item);
            }
            else if (msgLower.startsWith(cmdPlay)) {
                item = msg.substring(cmdPlay.length()).trim();
                this.getClient().changePlayingText(item);
                reply = String.format("Set playing text to `%s`!", item);
            }
            t.getChannel().sendMessage(reply);
        } catch (MissingPermissionsException | RateLimitException | DiscordException ex) {
            Logger.getLogger(ProfileModule.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    public String help() {
        return "**ProfileModule**: Changes "+this.getUtil().getName()+"'s nickname or playing status. `"+this.getFullCommand()+" (nick|play)`\n";
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public boolean isInterestedIn(MessageReceivedEvent t) {
        return this.getUtil().isMessageFromOwner(t) && super.isInterestedIn(t);
    }

    @Override
    public String getCommand() {
        return "profile";
    }
}
