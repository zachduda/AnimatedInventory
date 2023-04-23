package com.zachduda.animatedinventory.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerFortuneEndEvent extends Event {
	
	private final Player p;
	private boolean result;

    public PlayerFortuneEndEvent(Player p, boolean result) {
        this.p = p;
        this.result = result;
    }

    private static final HandlerList HANDLERS = new HandlerList();

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return this.p;
    }
    
    public boolean isYes() {
    	if(result) {
    	return true;
    	}
    	return false;
    }
    	
     public boolean isNo() {
        if(!result) {
        	return true;
        } 
        
        return false;
    }

}
