package org.mctourney.AutoReferee;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;
import org.mctourney.AutoReferee.AutoReferee.eAction;

public class PlayerVersusPlayerListener implements Listener
{
	AutoReferee plugin = null;

	public PlayerVersusPlayerListener(Plugin p)
	{
		plugin = (AutoReferee) p;
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void playerDeath(EntityDeathEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match != null && (event instanceof PlayerDeathEvent))
		{
			PlayerDeathEvent pdeath = (PlayerDeathEvent) event;
			Player victim = (Player) pdeath.getEntity();

			// get the player who killed this player (might be null)
			Player killer = victim.getKiller();
			String dmsg = pdeath.getDeathMessage();

			// if the death was due to intervention by the plugin
			// let's change the death message to reflect this fact
			eAction deathReason = plugin.getDeathReason(victim);
			if (deathReason != null) switch (deathReason)
			{
				// killed because they entered the void lane
				case ENTERED_VOIDLANE:
					dmsg = victim.getName() + " entered the void lane.";
					break;
			}

			// color the player's name with his team color
			dmsg = dmsg.replace(victim.getName(), match.getPlayerName(victim));

			// if the killer was a player, color their name as well
			if (killer != null) 
			{
				if (plugin.getConfig().getBoolean("server-mode.console.log", false))
					plugin.log.info("[DEATH] " + killer.getDisplayName() 
						+ " killed " + victim.getDisplayName());
				dmsg = dmsg.replace(killer.getName(), match.getPlayerName(killer));
			}

			// update the death message with the changes
			pdeath.setDeathMessage(dmsg);
			
			// register the death of the victim
			AutoRefPlayer vdata = match.getPlayer(victim);
			if (vdata != null) vdata.registerDeath(pdeath);
			
			// register the kill for the killer
			AutoRefPlayer kdata = match.getPlayer(killer); 
			if (kdata != null) kdata.registerKill(pdeath);
		}
	}

	public static Player entityToPlayer(Entity e)
	{
		// damaging entity is an actual player, easy!
		if ((e instanceof Player)) return (Player) e;

		// damaging entity is an arrow, then who was bow?
		if ((e instanceof Projectile))
		{
			LivingEntity shooter = ((Projectile) e).getShooter();
			if ((shooter instanceof Player)) return (Player) shooter;
		}
		return null;
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void damageDealt(EntityDamageEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null) return;

		if ((event instanceof EntityDamageByEntityEvent))
		{
			EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) event;
			Player damager = entityToPlayer(ed.getDamager());
			Player damaged = entityToPlayer(ed.getEntity());

			// if either of these aren't players, nothing to do here
			if (null == damager || null == damaged) return;

			// if the damaged entity was a player
			if (null != damaged)
			{
				// if the match is in progress and player is in start region
				// cancel any damage dealt to the player
				if (match.getCurrentState() == eMatchStatus.PLAYING && 
						match.inStartRegion(damaged.getLocation()))
				{ event.setCancelled(true); return; }
			}
			
			// get team affiliations of these players (maybe null)
			AutoRefTeam d1team = plugin.getTeam(damager);
			AutoRefTeam d2team = plugin.getTeam(damaged);
			if (d1team == null && d2team == null) return;

			// if the attacked isn't on a team, or same team (w/ no FF), cancel
			event.setCancelled(d2team == null ||
				(d1team == d2team && match.allowFriendlyFire));

			if (event.isCancelled()) return;
		}

		// save player data if the damaged entity was a player	
		if (event.getEntityType() == EntityType.PLAYER)
		{
			AutoRefPlayer pdata = match.getPlayer((Player) event.getEntity());	
			if (pdata != null) pdata.registerDamage(event);
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void explosionPrime(ExplosionPrimeEvent event)
	{
		//event.
	}
}
