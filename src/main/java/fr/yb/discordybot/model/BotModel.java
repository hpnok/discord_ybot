/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.yb.discordybot.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import sx.blah.discord.handle.obj.IUser;

/**
 *
 * @author Nicolas
 */
public class BotModel implements Serializable {
    
    private Map<String, UserModel> users;
    private Map<String, LobbySubModel> lobbySubscriptions;
    
    public BotModel() {
        this.users = new HashMap<>();
        this.lobbySubscriptions = new HashMap<>();
    }

    public Map<String, LobbySubModel> getLobbySubscriptions() {
        return lobbySubscriptions;
    }
    
    public LobbySubModel putLobbySubscription(LobbySubModel lobbySubscription) {
        this.lobbySubscriptions.put(lobbySubscription.getId(), lobbySubscription);
        return lobbySubscription;
    }

    public Map<String, UserModel> getUsers() {
        return users;
    }
    
    public UserModel put(IUser user) {
        String id = user.getStringID();
        UserModel um = new UserModel();
        um.setId(id);
        um.setName(user.getName());
        this.users.put(id, um);
        return um;
    }
    
    public UserModel find(String name) {
        if (this.users.containsKey(name)) {
            return this.users.get(name);
        }
        for (UserModel u : this.users.values()) {
            if (u.is(name)) {
                return u;
            }
        }
        return null;
    }
    
    public UserModel find(IUser user) {
        UserModel um = this.find(user.getStringID());
        if (um == null) {
            um = this.put(user);
        }
        return um;
    }
    
}
