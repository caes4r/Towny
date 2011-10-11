package com.palmergames.bukkit.towny.event;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.painting.PaintingBreakEvent;
import org.bukkit.event.painting.PaintingBreakByEntityEvent;
import org.bukkit.event.painting.PaintingPlaceEvent;

import com.palmergames.bukkit.towny.NotRegisteredException;
import com.palmergames.bukkit.towny.PlayerCache;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyException;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.PlayerCache.TownBlockStatus;
import com.palmergames.bukkit.towny.object.BlockLocation;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.TownyUniverse;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.MobRemovalTimerTask;
import com.palmergames.bukkit.towny.tasks.ProtectionRegenTask;

public class TownyEntityListener extends EntityListener {
       
	private final Towny plugin;

	public TownyEntityListener(Towny instance) {
        plugin = instance;
	}
        
        
    @Override
    public void onEntityDamage(EntityDamageEvent event) {

        if (event.isCancelled())
            return;
                
        long start = System.currentTimeMillis();
                
        Entity attacker = null;
        Entity defender = null;

        if (event instanceof EntityDamageByEntityEvent) {
            //plugin.sendMsg("EntityDamageByEntityEvent");
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent)event;
            if (entityEvent.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile)entityEvent.getDamager();
                attacker = projectile.getShooter();
                defender = entityEvent.getEntity();
            } else {
                attacker = entityEvent.getDamager();
                defender = entityEvent.getEntity();
            }
        }
                                
        if (attacker != null) { 
        	//plugin.sendMsg("Attacker not null");
            
            TownyUniverse universe = plugin.getTownyUniverse();
            try {
                TownyWorld world = TownyUniverse.getWorld(defender.getWorld().getName());
                    
             // Wartime
                if (universe.isWarTime()) {
                        event.setCancelled(false);
                        throw new Exception();
                }
                
                Player a = null;
                Player b = null;
                
                if (attacker instanceof Player)
                        a = (Player) attacker;
                if (defender instanceof Player)
                        b = (Player) defender;
                
                if (preventDamageCall(world, attacker, defender, a, b))
                        event.setCancelled(true);
                
            } catch (Exception e) {
            }
            
            
            plugin.sendDebugMsg("onEntityDamagedByEntity took " + (System.currentTimeMillis() - start) + "ms");
        }
    }
        
    @Override
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity =  event.getEntity();
                
        if (entity instanceof Player) {
            Player player = (Player)entity;
            plugin.sendDebugMsg("onPlayerDeath: " + player.getName() + "[ID: " + entity.getEntityId() + "]");
        }
    }
        
    @Override
    public void onCreatureSpawn(CreatureSpawnEvent event) {
            if (event.getEntity() instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity)event.getEntity();
                    Location loc = event.getLocation();
                    Coord coord = Coord.parseCoord(loc);
                    TownyWorld townyWorld = null;
                    
                    try {
							townyWorld = TownyUniverse.getWorld(loc.getWorld().getName());
                    } catch (NotRegisteredException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                    }
                    
                    //remove from world if set to remove mobs globally
                    if (townyWorld.isUsingTowny())
                    if (!townyWorld.hasWorldMobs() && MobRemovalTimerTask.isRemovingWorldEntity(livingEntity)){
                                            plugin.sendDebugMsg("onCreatureSpawn world: Canceled " + event.getCreatureType() + " from spawning within "+coord.toString()+".");
                                            event.setCancelled(true);
                    }
                            
                    //remove from towns if in the list and set to remove            
                    try {
                            
                            TownBlock townBlock = townyWorld.getTownBlock(coord);
                            if (townyWorld.isUsingTowny() && !townyWorld.isForceTownMobs())
                            if (!townBlock.getTown().hasMobs() && MobRemovalTimerTask.isRemovingTownEntity(livingEntity)) {
                                    plugin.sendDebugMsg("onCreatureSpawn town: Canceled " + event.getCreatureType() + " from spawning within "+coord.toString()+".");
                                    event.setCancelled(true);
                            }
                    } catch (TownyException x) {
                    }       

            }
    }
        
    @Override
    public void onEntityInteract(EntityInteractEvent event) {
            
            if (event.isCancelled())
                    return;
            
            Block block = event.getBlock();
            Entity entity = event.getEntity();
            TownyWorld townyWorld = null;
            
            try {
					townyWorld = TownyUniverse.getWorld(block.getLocation().getWorld().getName());
            } catch (NotRegisteredException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
            }
            
            // Prevent creatures trampling crops
            if ((townyWorld.isUsingTowny()) && (townyWorld.isDisableCreatureTrample()))
                    if ((block.getType() == Material.SOIL) || (block.getType() == Material.CROPS)) {
                            if (entity instanceof Creature)
                                    event.setCancelled(true);
                                    return;
                    }
            
    }
        
    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
            
    	Location loc;
    	Coord coord;
    	List<Block> blocks = event.blockList();
    	Entity entity = event.getEntity();
            
    	int count = 0;
            
    	for (Block block : blocks) {
                    
    		loc = block.getLocation();
            coord = Coord.parseCoord(loc);
            count++;
            TownyWorld townyWorld;
            
			try {
				townyWorld = TownyUniverse.getWorld(loc.getWorld().getName());
			} catch (NotRegisteredException e) {
				// failed to get world so abort
				return;
			}
            
            //TODO: expand to protect neutrals during a war
            try {
            	TownBlock townBlock = townyWorld.getTownBlock(coord);
                
                // If explosions are off, or it's wartime and explosions are off and the towns has no nation
                if (townyWorld.isUsingTowny()  && !townyWorld.isForceExpl())
                if (!townBlock.getTown().isBANG() || (plugin.getTownyUniverse().isWarTime() && !townBlock.getTown().hasNation() && !townBlock.getTown().isBANG())) {
                        if (event.getEntity() != null) plugin.sendDebugMsg("onEntityExplode: Canceled " + event.getEntity().getEntityId() + " from exploding within "+coord.toString()+".");
                        event.setCancelled(true);
                }                    
            } catch (TownyException x) {
            	// Wilderness explosion regeneration
            	if ((townyWorld.isUsingTowny()) && (townyWorld.isUsingPlotManagementWildRevert()))
                	if (entity instanceof Creature)
                		if (!plugin.getTownyUniverse().hasProtectionRegenTask(new BlockLocation(block.getLocation()))) {
	        				ProtectionRegenTask task = new ProtectionRegenTask(plugin.getTownyUniverse(), block, false);
	        				task.setTaskId(plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, ((TownySettings.getPlotManagementWildRegenDelay() + count)*20)));
	        				plugin.getTownyUniverse().addProtectionRegenTask(task);
	        				event.setYield((float) 0.0);
				    	}
            }
    	}
            
    }
    
    @Override
    public void onPaintingBreak(PaintingBreakEvent event) {
            
    	if (event.isCancelled()) {
    		event.setCancelled(true);
    		return;
    	}
            
    	long start = System.currentTimeMillis();
        
        if (event instanceof PaintingBreakByEntityEvent) {
            PaintingBreakByEntityEvent evt = (PaintingBreakByEntityEvent) event;
            if (evt.getRemover() instanceof Player) {
            	Player player = (Player) evt.getRemover();
                Painting painting = evt.getPainting();

                WorldCoord worldCoord;
                try {
                	worldCoord = new WorldCoord(TownyUniverse.getWorld(painting.getWorld().getName()), Coord.parseCoord(painting.getLocation()));
                } catch (NotRegisteredException e1) {
                    plugin.sendErrorMsg(player, TownySettings.getLangString("msg_err_not_configured"));
                    event.setCancelled(true);
                     return;
                }
                
                //Get destroy permissions (updates if none exist)
    			boolean bDestroy = TownyUniverse.getCachePermissions().getCachePermission(player, painting.getLocation(), TownyPermission.ActionType.DESTROY);
    			
    			PlayerCache cache = plugin.getCache(player);
                cache.updateCoord(worldCoord);
                TownBlockStatus status = cache.getStatus();
                if (status == TownBlockStatus.UNCLAIMED_ZONE && plugin.hasWildOverride(worldCoord.getWorld(), player, painting.getEntityId(), TownyPermission.ActionType.DESTROY))
                        return;
                if (!bDestroy)
                    event.setCancelled(true);
                if (cache.hasBlockErrMsg())
                    plugin.sendErrorMsg(player, cache.getBlockErrMsg());
            }
        }
        
        plugin.sendDebugMsg("onPaintingBreak took " + (System.currentTimeMillis() - start) + "ms ("+event.getCause().name()+", "+event.isCancelled() +")");                
    }
    
    @Override
    public void onPaintingPlace(PaintingPlaceEvent event) {
            
    	if (event.isCancelled()) {
            event.setCancelled(true);
            return;
        }
            
    	long start = System.currentTimeMillis();
        
        Player player = event.getPlayer();
        Painting painting = event.getPainting();

        WorldCoord worldCoord;
        try {
        	worldCoord = new WorldCoord(TownyUniverse.getWorld(painting.getWorld().getName()), Coord.parseCoord(painting.getLocation()));
        } catch (NotRegisteredException e1) {
        	plugin.sendErrorMsg(player, TownySettings.getLangString("msg_err_not_configured"));
            event.setCancelled(true);
            return;
        }
            
        //Get build permissions (updates if none exist)
        boolean bBuild = TownyUniverse.getCachePermissions().getCachePermission(player, painting.getLocation(), TownyPermission.ActionType.BUILD);
			
        PlayerCache cache = plugin.getCache(player);
        TownBlockStatus status = cache.getStatus();
        if (status == TownBlockStatus.UNCLAIMED_ZONE && plugin.hasWildOverride(worldCoord.getWorld(), player, painting.getEntityId(), TownyPermission.ActionType.BUILD))
                return;
        if (!bBuild)
                event.setCancelled(true);
        if (cache.hasBlockErrMsg())
                plugin.sendErrorMsg(player, cache.getBlockErrMsg());
        
        plugin.sendDebugMsg("onPaintingBreak took " + (System.currentTimeMillis() - start) + "ms ("+event.getEventName()+", "+event.isCancelled() +")");                  
    }
    
    public boolean preventDamageCall(TownyWorld world, Entity a, Entity b, Player ap, Player bp) {
            // World using Towny
            if (!world.isUsingTowny())
                    return false;
            
            if (ap != null && bp != null)
                    if (preventDamagePvP(world, ap, bp) || preventFriendlyFire(ap, bp))
                            return true;
            
            
            try {
                    // Check Town PvP status
                    Coord key = Coord.parseCoord(b);
                    TownBlock townblock = world.getTownBlock(key);
                    //plugin.sendDebugMsg("is townblock");
                    if (!townblock.getTown().isPVP() && !world.isForcePVP()) {
                            if (bp != null && (ap != null || a instanceof Arrow))
                                return true;
                            
                            if (b instanceof Wolf) {
                                Wolf wolf = (Wolf)b;
                                if (wolf.isTamed() && !wolf.getOwner().equals((AnimalTamer)a)) {
                                	return true;
                                }
                            }      
                    }
                    /*
                            else if (!TownySettings.isPvEWithinNonPvPZones()) // TODO: Allow EvE >.>
                                    return true;
                    */
                    //plugin.sendDebugMsg("is pvp");
            } catch (NotRegisteredException e) {
            }
            
            return false;
    }
    
    public boolean preventDamagePvP(TownyWorld world, Player a, Player b) {
            // Universe is only PvP
            if (world.isForcePVP() || world.isPVP())
                    return false;
            //plugin.sendDebugMsg("is not forcing pvp");
            // World PvP
            if (!world.isPVP())
                    return true;
            //plugin.sendDebugMsg("world is pvp");
            return false;
    }
    
    public boolean preventFriendlyFire(Player a, Player b) {
            TownyUniverse universe = plugin.getTownyUniverse();
            if (!TownySettings.getFriendlyFire() && universe.isAlly(a.getName(), b.getName()))
                    return true;

            return false;
    }
}