package com.enderdevelopment.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Main extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Long> lastMove = new HashMap<>();
    private final Map<UUID, String> jobs = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, String> games = new HashMap<>();
    private final Set<UUID> afk = new HashSet<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, String> ranks = new HashMap<>();
    private String activeEvent = "";
    private int eventGeneration;

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String name : List.of("spawn","wild","shop","buy","balance","kills","job","afk","mg","rank","serveradmin"))
            Objects.requireNonNull(getCommand(name)).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::afkTick, 20L, 20L * Math.max(1, getConfig().getLong("afk.interval-seconds", 60)));
        scheduleEvent();
        Bukkit.getScheduler().runTaskTimer(this, this::chunkEffects, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::spawnBoss, 1200L, 20L * 60 * Math.max(1, getConfig().getInt("boss.interval-minutes",45)));
        Bukkit.getScheduler().runTaskTimer(this, this::spawnStructure, 1800L, 20L * 60 * Math.max(1, getConfig().getInt("structure.interval-minutes",30)));
    }

    @Override public void onDisable() { saveData(); }

    private void saveData() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String id=p.getUniqueId().toString();
            getConfig().set("data."+id+".balance", balances.getOrDefault(p.getUniqueId(),0.0));
            getConfig().set("data."+id+".kills", kills.getOrDefault(p.getUniqueId(),0));
            getConfig().set("data."+id+".job", jobs.getOrDefault(p.getUniqueId(),"none"));
            getConfig().set("data."+id+".rank", ranks.getOrDefault(p.getUniqueId(),"member"));
        }
        saveConfig();
    }
    private void load(Player p) {
        String id=p.getUniqueId().toString();
        balances.put(p.getUniqueId(),getConfig().getDouble("data."+id+".balance",0));
        kills.put(p.getUniqueId(),getConfig().getInt("data."+id+".kills",0));
        jobs.put(p.getUniqueId(),getConfig().getString("data."+id+".job","none"));
        ranks.put(p.getUniqueId(),getConfig().getString("data."+id+".rank","member"));
        lastMove.put(p.getUniqueId(),System.currentTimeMillis());
    }
    @EventHandler public void join(PlayerJoinEvent e){load(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){saveData(); UUID id=e.getPlayer().getUniqueId();lastMove.remove(id);afk.remove(id);games.remove(id);}
    @EventHandler public void move(PlayerMoveEvent e){if(e.getTo()!=null&&(e.getFrom().getBlockX()!=e.getTo().getBlockX()||e.getFrom().getBlockZ()!=e.getTo().getBlockZ())){lastMove.put(e.getPlayer().getUniqueId(),System.currentTimeMillis());afk.remove(e.getPlayer().getUniqueId());}}
    @EventHandler public void blockBreak(BlockBreakEvent e) {
        Player p=e.getPlayer(); String job=jobs.getOrDefault(p.getUniqueId(),"none");
        if(job.equals("lumberjack") && isLog(e.getBlock().getType())) { e.setDropItems(true); p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,60,1,true,false)); scores.merge(p.getUniqueId(),1,Integer::sum); }
        if(games.getOrDefault(p.getUniqueId(),"").equals("spleef") && e.getBlock().getType()==Material.SNOW_BLOCK){e.setDropItems(false);scores.merge(p.getUniqueId(),1,Integer::sum);}
        if(games.getOrDefault(p.getUniqueId(),"").equals("buildbattle"))scores.merge(p.getUniqueId(),1,Integer::sum);
    }
    private boolean isLog(Material m){return m.name().endsWith("_LOG")||m.name().endsWith("_WOOD");}
    @EventHandler public void death(EntityDeathEvent e){
        Player killer=e.getEntity().getKiller();
        if(killer!=null){UUID id=killer.getUniqueId();kills.merge(id,1,Integer::sum);earn(killer,2);killer.sendMessage(Component.text("Kills: "+kills.get(id)+" | +$2",NamedTextColor.GOLD));}
        if(e.getEntity().getScoreboardTags().contains("serverworld_boss")){e.getDrops().clear();for(String item:getConfig().getStringList("boss.loot")){Material m=Material.matchMaterial(item);if(m!=null)e.getDrops().add(new ItemStack(m));}e.getDrops().add(new ItemStack(Material.DIAMOND,3));}
        if(!activeEvent.isEmpty()&&activeEvent.equals("DOUBLE_DROPS")&&e.getEntity() instanceof Animals)for(ItemStack drop:e.getDrops())drop.setAmount(Math.min(drop.getMaxStackSize(),drop.getAmount()*2));
        String game=games.get(killer==null?new UUID(0,0):killer.getUniqueId());
        if("pvp".equals(game)&&killer!=null){games.remove(e.getEntity().getUniqueId());scores.merge(killer.getUniqueId(),1,Integer::sum);}
    }
    @EventHandler public void damage(EntityDamageByEntityEvent e){if(e.getDamager() instanceof Player p&&jobs.getOrDefault(p.getUniqueId(),"").equals("assassin")&&isNight(p.getWorld()))e.setDamage(e.getDamage()+getConfig().getDouble("jobs.assassin.night-damage",2));if(activeEvent.equals("GLASS_CANNON")){e.setDamage(e.getDamage()*2);if(e.getEntity() instanceof Player)e.setDamage(e.getDamage()*2);}}
    private boolean isNight(World w){long t=w.getTime();return t>=13000&&t<=23000;}
    @EventHandler public void interact(PlayerInteractEvent e){if(e.getClickedBlock()==null)return;Location l=e.getClickedBlock().getLocation();if(near(l,"portals.wild-radius")){e.setCancelled(true);teleportConfigured(e.getPlayer(),"wild");}else if(near(l,"portals.minigame-radius")){e.setCancelled(true);e.getPlayer().sendMessage(Component.text("Use /mg spleef, /mg pvp, /mg buildbattle or /mg leave",NamedTextColor.AQUA));}}
    private boolean near(Location l,String path){Location s=location("spawn");return s!=null&&s.getWorld()==l.getWorld()&&s.distanceSquared(l)<=Math.pow(getConfig().getDouble(path,3),2);}
    private Location location(String path){String world=getConfig().getString(path+".world");World w=world==null?null:Bukkit.getWorld(world);if(w==null)return null;return new Location(w,getConfig().getDouble(path+".x"),getConfig().getDouble(path+".y"),getConfig().getDouble(path+".z"), (float)getConfig().getDouble(path+".yaw"),(float)getConfig().getDouble(path+".pitch"));}
    private void teleportConfigured(Player p,String path){Location l=location(path);if(l==null){p.sendMessage(Component.text("Configured destination world is unavailable.",NamedTextColor.RED));return;}p.teleportAsync(l);}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] a){
        if(command.getName().equals("serveradmin")){if(!sender.hasPermission("serverworld.admin"))return true;if(a.length>=2&&a[0].equalsIgnoreCase("set")&&sender instanceof Player p){Location l=p.getLocation();String path=a[1].equalsIgnoreCase("spawn")?"spawn":a[1].equalsIgnoreCase("wild")?"wild":null;if(path==null){sender.sendMessage("Use set spawn or set wild.");return true;}getConfig().set(path+".world",l.getWorld().getName());getConfig().set(path+".x",l.getX());getConfig().set(path+".y",l.getY());getConfig().set(path+".z",l.getZ());getConfig().set(path+".yaw",l.getYaw());getConfig().set(path+".pitch",l.getPitch());saveConfig();sender.sendMessage("Saved "+path+" location.");return true;}if(a.length>=2&&a[0].equalsIgnoreCase("event")){startEvent(a[1].toUpperCase(Locale.ROOT));sender.sendMessage("Started event "+a[1]);return true;}sender.sendMessage("/serveradmin set spawn|wild, /serveradmin event EVENT, /serveradmin save");if(a.length>0&&a[0].equalsIgnoreCase("save")){saveData();sender.sendMessage("Saved data.");}return true;}
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}UUID id=p.getUniqueId();if(!balances.containsKey(id))load(p);
        switch(command.getName()){
            case "spawn"->teleportConfigured(p,"spawn");
            case "wild"->teleportConfigured(p,"wild");
            case "balance"->p.sendMessage(Component.text("Balance: $"+fmt(balance(p)),NamedTextColor.GOLD));
            case "kills"->p.sendMessage(Component.text("Kills: "+kills.getOrDefault(id,0),NamedTextColor.YELLOW));
            case "afk"->{if(afk.remove(id))p.sendMessage(Component.text("AFK earning disabled.",NamedTextColor.YELLOW));else{afk.add(id);lastMove.put(id,System.currentTimeMillis());p.sendMessage(Component.text("AFK earning enabled. Stay still to qualify.",NamedTextColor.GREEN));}}
            case "shop"->{p.sendMessage(Component.text("Shop: use /buy <item>. Balance: $"+fmt(balance(p)),NamedTextColor.GOLD));ConfigurationSection sec=getConfig().getConfigurationSection("shop.items");if(sec!=null)for(String key:sec.getKeys(false))p.sendMessage(Component.text(key+" — $"+sec.getDouble(key+".price"),NamedTextColor.YELLOW));}
            case "buy"->buy(p,a);
            case "rank"->rank(p,a);
            case "job"->job(p,a);
            case "mg"->minigame(p,a);
        }return true;
    }
    private void buy(Player p,String[] a){if(a.length<1){p.sendMessage("Use /buy <item>.");return;}ConfigurationSection s=getConfig().getConfigurationSection("shop.items."+a[0].toLowerCase());if(s==null){p.sendMessage("Unknown shop item.");return;}Material m=Material.matchMaterial(s.getString("material",""));int amount=s.getInt("amount",1);double price=s.getDouble("price");if(m==null||amount<1||!take(p,price)){p.sendMessage("Invalid item or insufficient funds.");return;}Map<Integer,ItemStack> rest=p.getInventory().addItem(new ItemStack(m,amount));rest.values().forEach(i->p.getWorld().dropItemNaturally(p.getLocation(),i));p.sendMessage("Purchased "+amount+" "+m.name()+" for $"+fmt(price));}
    private void rank(Player p,String[] a){ConfigurationSection s=getConfig().getConfigurationSection("ranks");if(a.length==0){p.sendMessage("Ranks: "+(s==null?"none":String.join(", ",s.getKeys(false)))+". Use /rank buy <rank>.");return;}if(a[0].equalsIgnoreCase("buy")&&a.length>1&&s!=null){String name=a[1].toLowerCase();ConfigurationSection r=s.getConfigurationSection(name);if(r==null){p.sendMessage("Unknown rank.");return;}if(!take(p,r.getDouble("price"))){p.sendMessage("Insufficient funds.");return;}ranks.put(p.getUniqueId(),name);String perm=r.getString("permission","");if(!perm.isBlank())p.addAttachment(this,perm,true);p.sendMessage("Rank upgraded to "+name+".");}}
    private void job(Player p,String[] a){if(a.length==0){p.sendMessage("Current job: "+jobs.getOrDefault(p.getUniqueId(),"none")+". Choose assassin, lumberjack, miner or none.");return;}String j=a[0].toLowerCase();if(!List.of("assassin","lumberjack","miner","none").contains(j)){p.sendMessage("Unknown job.");return;}jobs.put(p.getUniqueId(),j);p.sendMessage("Job selected: "+j);}
    private void minigame(Player p,String[] a){if(a.length==0){p.sendMessage("Use /mg spleef|pvp|buildbattle|leave");return;}if(a[0].equalsIgnoreCase("leave")){games.remove(p.getUniqueId());teleportConfigured(p,"spawn");return;}String game=a[0].toLowerCase();if(!List.of("spleef","pvp","buildbattle").contains(game)){p.sendMessage("Unknown minigame.");return;}String path="minigames."+game;World w=Bukkit.getWorld(getConfig().getString(path+".world","world"));if(w==null){p.sendMessage("Minigame world is unavailable.");return;}int x1=getConfig().getInt(path+".min-x"),x2=getConfig().getInt(path+".max-x"),z1=getConfig().getInt(path+".min-z"),z2=getConfig().getInt(path+".max-z"),y=getConfig().getInt(path+".y",100);games.put(p.getUniqueId(),game);scores.put(p.getUniqueId(),0);Location loc=new Location(w,(x1+x2)/2.0+0.5,y,(z1+z2)/2.0+0.5);p.teleportAsync(loc);p.sendMessage("Joined "+game+". Use /mg leave to return. Survive or score to win.");if(game.equals("spleef"))buildSpleef(w,x1,x2,z1,z2,y);if(game.equals("buildbattle"))p.sendMessage("Build battle: build within your configured plot; use /mg submit when finished.");}
    private void buildSpleef(World w,int x1,int x2,int z1,int z2,int y){for(int x=Math.min(x1,x2);x<=Math.max(x1,x2);x++)for(int z=Math.min(z1,z2);z<=Math.max(z1,z2);z++)w.getBlockAt(x,y-1,z).setType(Material.SNOW_BLOCK);}
    private double balance(Player p){return balances.getOrDefault(p.getUniqueId(),0.0);}private boolean take(Player p,double n){if(n<0||balance(p)<n)return false;balances.put(p.getUniqueId(),balance(p)-n);return true;}private void earn(Player p,double n){balances.merge(p.getUniqueId(),n,Double::sum);}private String fmt(double d){return String.format(Locale.US,"%.2f",d);}
    private void afkTick(){int interval=Math.max(1,getConfig().getInt("afk.interval-seconds",60));for(UUID id:new HashSet<>(afk)){Player p=Bukkit.getPlayer(id);if(p==null){afk.remove(id);continue;}long idle=(System.currentTimeMillis()-lastMove.getOrDefault(id,0L))/1000;if(idle<getConfig().getLong("afk.require-no-movement-seconds",90)){p.sendMessage(Component.text("Move less to qualify for AFK pay.",NamedTextColor.RED));continue;}int max=getConfig().getInt("afk.max-balance-payouts",120);if(max>0&&balance(p)>=max*getConfig().getDouble("afk.payout",5)){p.sendMessage("AFK balance cap reached.");afk.remove(id);continue;}double pay=getConfig().getDouble("afk.payout",5);earn(p,pay);p.sendMessage(Component.text("AFK pay: +$"+fmt(pay),NamedTextColor.GREEN));}}
    private void scheduleEvent(){long min=Math.max(1,getConfig().getLong("events.minimum-minutes",15)),max=Math.max(min,getConfig().getLong("events.maximum-minutes",30));long delay=ThreadLocalRandom.current().nextLong(min,max+1)*1200;Bukkit.getScheduler().runTaskLater(this,()->{List<String> ev=getConfig().getStringList("events.effects");if(!ev.isEmpty())startEvent(ev.get(ThreadLocalRandom.current().nextInt(ev.size())));scheduleEvent();},delay);}
    private void startEvent(String type){activeEvent=type;int generation=++eventGeneration;for(Player p:Bukkit.getOnlinePlayers()){switch(type){case "SPEED"->p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*getConfig().getInt("events.duration-seconds",180),1));case "WEAKNESS"->p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,20*getConfig().getInt("events.duration-seconds",180),0));case "HASTE"->p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,20*getConfig().getInt("events.duration-seconds",180),1));case "HAZARD"->{if(p.getWorld().getEnvironment()!=World.Environment.NETHER)p.getWorld().strikeLightningEffect(p.getLocation());}case "GLASS_CANNON"->{} }}Bukkit.broadcast(Component.text("World event: "+type.replace('_',' ')+"!",NamedTextColor.LIGHT_PURPLE));Bukkit.getScheduler().runTaskLater(this,()->{if(eventGeneration==generation)activeEvent="";},20L*getConfig().getInt("events.duration-seconds",180));}
    private void chunkEffects(){if(!getConfig().getBoolean("chunk-effects.enabled",true))return;for(Player p:Bukkit.getOnlinePlayers()){if(!getConfig().getStringList("chunk-effects.worlds").contains(p.getWorld().getName())||Math.random()>getConfig().getDouble("chunk-effects.chance",.08))continue;List<String> es=getConfig().getStringList("chunk-effects.effects");if(es.isEmpty())continue;String ef=es.get(ThreadLocalRandom.current().nextInt(es.size()));switch(ef){case "SPEED"->p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*getConfig().getInt("chunk-effects.duration-seconds",120),1));case "HAZARD"->p.getWorld().strikeLightningEffect(p.getLocation());case "GLASS_CANNON"->{p.sendMessage("Glass-cannon chunk: damage is doubled.");}case "EXTRA_DROPS"->p.sendMessage("Extra drops chunk activated.");}}}
    private void spawnBoss(){String wn=getConfig().getString("boss.world","world");World w=Bukkit.getWorld(wn);if(w==null||w.getPlayers().isEmpty())return;int x=ThreadLocalRandom.current().nextInt(getConfig().getInt("boss.x-min",-500),getConfig().getInt("boss.x-max",500)+1),z=ThreadLocalRandom.current().nextInt(getConfig().getInt("boss.z-min",-500),getConfig().getInt("boss.z-max",500)+1);Location l=w.getHighestBlockAt(x,z).getLocation().add(.5,1,.5);Zombie b=(Zombie)w.spawnEntity(l,EntityType.ZOMBIE);b.customName(Component.text(ChatColor.translateAlternateColorCodes('&',getConfig().getString("boss.name","&5Worldbreaker"))));b.setCustomNameVisible(true);b.getScoreboardTags().add("serverworld_boss");b.setPersistent(true);if(b.getAttribute(Attribute.MAX_HEALTH)!=null)b.getAttribute(Attribute.MAX_HEALTH).setBaseValue(getConfig().getDouble("boss.health",120));b.setHealth(Math.min(b.getHealth(),getConfig().getDouble("boss.health",120)));b.setGlowing(true);b.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));b.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));Bukkit.broadcast(Component.text("A Worldbreaker has appeared at "+x+", "+z,NamedTextColor.DARK_PURPLE));}
    private void spawnStructure(){World w=Bukkit.getWorld(getConfig().getString("structure.world","world"));if(w==null||w.getPlayers().isEmpty())return;Player p=w.getPlayers().get(ThreadLocalRandom.current().nextInt(w.getPlayers().size()));int radius=Math.max(50,getConfig().getInt("structure.radius",1000));int x=p.getLocation().getBlockX()+ThreadLocalRandom.current().nextInt(-radius,radius+1),z=p.getLocation().getBlockZ()+ThreadLocalRandom.current().nextInt(-radius,radius+1),y=w.getHighestBlockYAt(x,z)+1;for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){w.getBlockAt(x+dx,y-1,z+dz).setType((Math.abs(dx)==2||Math.abs(dz)==2)?Material.MOSSY_COBBLESTONE:Material.COBBLESTONE);}for(int dy=0;dy<3;dy++){w.getBlockAt(x-2,y+dy,z).setType(Material.MOSSY_COBBLESTONE);w.getBlockAt(x+2,y+dy,z).setType(Material.MOSSY_COBBLESTONE);w.getBlockAt(x,y+dy,z-2).setType(Material.MOSSY_COBBLESTONE);w.getBlockAt(x,y+dy,z+2).setType(Material.MOSSY_COBBLESTONE);}w.getBlockAt(x,y,z).setType(Material.CHEST);Bukkit.broadcast(Component.text("An ancient ruin has appeared near "+x+", "+z,NamedTextColor.GREEN));}
}
