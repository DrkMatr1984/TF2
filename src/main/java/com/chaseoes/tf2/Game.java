package com.chaseoes.tf2;

import com.chaseoes.tf2.capturepoints.CapturePoint;
import com.chaseoes.tf2.capturepoints.CapturePointUtilities;
import com.chaseoes.tf2.classes.TF2Class;
import com.chaseoes.tf2.commands.SpectateCommand;
import com.chaseoes.tf2.lobbywall.LobbyWall;
import com.chaseoes.tf2.localization.Localizers;
import com.chaseoes.tf2.utilities.Container;
import com.chaseoes.tf2.utilities.GeneralUtilities;
import com.chaseoes.tf2.utilities.WorldEditUtilities;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Game {

    TF2 plugin;
    Map map;
    GameStatus status = GameStatus.WAITING;
    public boolean redHasBeenTeleported = false;
    public int time = 0;
    GameScoreboard scoreboard;

    public HashMap<String, GamePlayer> playersInGame = new HashMap<String, GamePlayer>();

    public Game(Map m, TF2 plugin) {
        map = m;
        this.plugin = plugin;
        scoreboard = new GameScoreboard(this);
    }

    public GamePlayer getPlayer(Player player) {
        return playersInGame.get(player.getName());
    }

    public void setStatus(GameStatus s) {
        status = s;
    }

    public GameStatus getStatus() {
        return status;
    }

    public GameScoreboard getScoreboard() {
        return scoreboard;
    }

    public String getMapName() {
        return map.getName();
    }

    public void updateTime(int time) {
        this.time = time;
    }

    public Integer getTimeLeftSeconds() {
        return map.getTimelimit() - time;
    }

    public void setExpOfPlayers(double expOfPlayers) {
        for (GamePlayer gp : playersInGame.values()) {
            gp.getPlayer().setExp((float) expOfPlayers);
        }
    }

    public List<String> getPlayersIngame() {
        List<String> l = new ArrayList<String>();
        for (GamePlayer gp : playersInGame.values()) {
            l.add(gp.getName());
        }
        return l;
    }

    public void startMatch() {
        setStatus(GameStatus.INGAME);
        CapturePointUtilities.getUtilities().uncaptureAll(map);
        for (Container container : map.getContainers()) {
            container.applyItems();
        }
        Schedulers.getSchedulers().startTimeLimitCounter(map);
        Schedulers.getSchedulers().startRedTeamCountdown(map);

        for (GamePlayer gp : playersInGame.values()) {
            Player player = gp.getPlayer();
            if (gp.getTeam() == Team.BLUE) {
                if (gp.getCurrentClass() != null) {
                    gp.setInLobby(false);
                    player.teleport(map.getBlueSpawn());
                    gp.getCurrentClass().apply(gp);
                    gp.setUsingChangeClassButton(false);
                } else {
                    gp.setUsingChangeClassButton(true);
                    Localizers.getDefaultLoc().TELEPORT_AFTER_CHOOSE_CLASS.sendPrefixed(player);
                }
            }
        }

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (GamePlayer gp : playersInGame.values()) {
                    Player player = gp.getPlayer();
                    if (gp.getTeam() == Team.RED) {
                        if (gp.getCurrentClass() != null) {
                            gp.setInLobby(false);
                            player.teleport(map.getRedSpawn());
                            gp.getCurrentClass().apply(gp);
                            gp.setUsingChangeClassButton(false);
                        } else {
                            gp.setUsingChangeClassButton(true);
                            Localizers.getDefaultLoc().TELEPORT_AFTER_CHOOSE_CLASS.sendPrefixed(player);
                        }
                    }
                }

                redHasBeenTeleported = true;
                Schedulers.getSchedulers().stopRedTeamCountdown(map.getName());
            }
        }, map.getRedTeamTeleportTime() * 20L);

        for (GamePlayer gp : playersInGame.values()) {
            scoreboard.addPlayer(gp);
        }
        scoreboard.updateBoard();
    }

    public void stopMatch(boolean queueCheck) { // TODO: This may make players
        // in queue join on a disabled
        // match
        setStatus(GameStatus.WAITING);
        scoreboard.remove();
        Schedulers.getSchedulers().stopRedTeamCountdown(map.getName());
        Schedulers.getSchedulers().stopTimeLimitCounter(map.getName());
        Schedulers.getSchedulers().stopCountdown(map.getName());
        for (Container container : map.getContainers()) {
            container.applyItems();
        }

        CapturePointUtilities.getUtilities().uncaptureAll(map);

        for (Entity e : map.getP1().getWorld().getEntities()) {
            if (e instanceof Arrow) {
                if (WorldEditUtilities.getWEUtilities().isInMap(e, map)) {
                    e.remove();
                }
            }
        }

        for (CapturePoint cp : map.getCapturePoints()) {
            cp.stopCapturing();
        }
        SpectateCommand.getCommand().stopSpectating(this);
        redHasBeenTeleported = false;
        HashMap<String, GamePlayer> hmap = new HashMap<String, GamePlayer>(playersInGame);
        playersInGame.clear();
        for (GamePlayer gp : hmap.values()) {
            gp.leaveCurrentGame();
            Localizers.getDefaultLoc().GAME_END.sendPrefixed(gp.getPlayer());
            if (plugin.getConfig().getBoolean("map-rotation")) {
                Game nextGame = GameUtilities.getUtilities().getNextGame(this);
                if (nextGame != null && !nextGame.isFull() && nextGame.getStatus() == GameStatus.WAITING) {
                    gp.getPlayer().performCommand("tf2 join " + nextGame.getMapName());
                }
            }
        }
    }

    public void winMatch(Team team) {
        List<String> inGameOld = new ArrayList<String>();
        for (GamePlayer gp : playersInGame.values()) {
            if (gp.getTeam() == team) {
                inGameOld.add(gp.getName());
            }
        }

        if (TF2.getInstance().getConfig().getBoolean("stats-database.enabled")) {
            for (GamePlayer gp : playersInGame.values()) {
                StatCollector sc = gp.getStatCollector();
                int highest_killstreak = gp.getHighestKillstreak();
                int points_captured = gp.getPointsCaptured();
                int time_ingame = gp.getTotalTimeIngame();
                int arrows_fired = gp.getArrowsFired();
                sc.addStatsFromGame(gp.getTotalKills(), highest_killstreak, points_captured, gp.getTeam(), time_ingame, team, arrows_fired, gp.getDeaths());
                sc.submit();
            }
        }

        String[] winlines = new String[4];
        winlines[0] = " ";
        winlines[1] = "" + ChatColor.DARK_RED + ChatColor.BOLD + Localizers.getDefaultLoc().RED_TEAM.getString();

        if (team == Team.BLUE) {
            winlines[1] = ChatColor.BLUE + "" + ChatColor.BOLD + Localizers.getDefaultLoc().BLUE_TEAM.getString();
        }

        winlines[2] = ChatColor.GREEN + "" + ChatColor.BOLD + Localizers.getDefaultLoc().WINS.getString();
        winlines[3] = " ";
        String te = ChatColor.DARK_RED + "" + ChatColor.BOLD + Localizers.getDefaultLoc().RED_TEAM.getString() + "" + ChatColor.RESET + ChatColor.YELLOW;

        if (team == Team.BLUE) {
            te = ChatColor.BLUE + "" + ChatColor.BOLD + ChatColor.BOLD + Localizers.getDefaultLoc().BLUE.getString() + "" + ChatColor.RESET + ChatColor.YELLOW;
        }

        LobbyWall.getWall().setAllLines(map.getName(), null, winlines, false, true);

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                String[] creditlines = new String[4];
                creditlines[0] = " ";
                creditlines[1] = ChatColor.BOLD + "TF2 Plugin By:";
                creditlines[2] = ChatColor.BLUE + "chaseoes";
                creditlines[3] = " ";
                LobbyWall.getWall().setAllLines(map.getName(), 4, creditlines, false, true);
            }
        }, 120L);


        CapturePointUtilities.getUtilities().uncaptureAll(map);
        if (TF2.getInstance().getConfig().getBoolean("broadcast-winning-team")) {
            Localizers.getDefaultLoc().GAME_WIN.broadcast(te, ChatColor.BOLD + map.getName() + ChatColor.RESET + "" + ChatColor.YELLOW);
        }
        stopMatch(true);
        Player[] playerList = TF2.getInstance().getServer().getOnlinePlayers().toArray (new Player[TF2.getInstance().getServer().getOnlinePlayers().size ()]);
        if (team == Team.RED) {
            GeneralUtilities.runCommands("on-red-win", playerList[0], playerList[0], getMap());
        } else {
            GeneralUtilities.runCommands("on-blue-win", playerList[0], playerList[0], getMap());
        }
    }

    @SuppressWarnings("deprecation")
    public void joinGame(GamePlayer player, Team team) {
        GameQueue q = map.getQueue();
        boolean full = q.gameHasRoom();
        if (!q.gameHasRoom()) {
            if (!player.getPlayer().hasPermission("tf2.create")) {
                q.add(player.getPlayer());
                Localizers.getDefaultLoc().IN_LINE.sendPrefixed(player.getPlayer(), q.getPosition(player.getPlayer()) + 1);
                return;
            }
        }

        if (!full && player.getPlayer().hasPermission("tf2.create")) {
            Localizers.getDefaultLoc().PLAYER_JOIN_FULL_MAP.sendPrefixed(player.getPlayer(), ChatColor.BOLD + map.getName() + ChatColor.RESET + "" + ChatColor.YELLOW);
        } else {
            Localizers.getDefaultLoc().PLAYER_JOIN_MAP.sendPrefixed(player.getPlayer(), ChatColor.BOLD + map.getName() + ChatColor.RESET + "" + ChatColor.YELLOW);
        }

        for (Game g : GameUtilities.getUtilities().games.values()) {
            Map gm = TF2.getInstance().getMap(g.getMapName());
            gm.getQueue().remove(player.getPlayer());
        }

        if (SpectateCommand.getCommand().isSpectating(player.getPlayer())) {
            SpectateCommand.getCommand().stopSpectating(player.getPlayer());
        }
        
        TF2Class c = new TF2Class("NONE");
        playersInGame.put(player.getName(), player);
        player.setTimeEnteredGame();
        player.setMap(getMapName());
        player.setInLobby(true);
        player.setTeam(team);
        player.saveInventory();
        c.clearInventory(player.getPlayer());
        player.getPlayer().setHealth(20);
        player.getPlayer().setFoodLevel(20);
        player.getPlayer().setGameMode(GameMode.valueOf(plugin.getConfig().getString("gamemode").toUpperCase()));
        player.getPlayer().setLevel(0);
        player.getPlayer().setExp(0);
        
        switch (team) {
            case BLUE:
                player.getPlayer().teleport(map.getBlueLobby());
                break;
            case RED:
                player.getPlayer().teleport(map.getRedLobby());
                break;
        }

        double currentpercent = (double) playersInGame.size() / map.getPlayerlimit() * 100;
        if (getStatus().equals(GameStatus.WAITING)) {
            if (currentpercent >= plugin.getConfig().getInt("autostart-percent")) {
                Schedulers.getSchedulers().startCountdown(map);
                setStatus(GameStatus.STARTING);
            }
        }

        if (getStatus().equals(GameStatus.INGAME)) {
            scoreboard.addPlayer(player);
            scoreboard.updateBoard();
        }

        if (getStatus() == GameStatus.WAITING) {
            Localizers.getDefaultLoc().PERCENT_JOIN.sendPrefixed(player.getPlayer(), plugin.getConfig().getInt("autostart-percent"));
        } else if (getStatus() == GameStatus.INGAME) {
            switch (player.getTeam()) {
                case RED:
                    if (redHasBeenTeleported) {
                        player.setUsingChangeClassButton(true);
                    }
                    break;
                case BLUE:
                    player.setUsingChangeClassButton(true);
                    break;
            }
        }

        player.getPlayer().updateInventory();
    }

    public void leaveGame(Player player) {
        GamePlayer gp = getPlayer(player);
        playersInGame.remove(gp.getName());
        if (status == GameStatus.INGAME) {
            boolean redEmpty = getSizeOfTeam(Team.RED) == 0;
            boolean blueEmpty = getSizeOfTeam(Team.BLUE) == 0;
            if (redEmpty && !blueEmpty) {
                winMatch(Team.BLUE);
            } else if (blueEmpty && !redEmpty) {
                winMatch(Team.RED);
            }
            scoreboard.removePlayer(gp);
        }
        gp.leaveCurrentGame();
    }

    public Team decideTeam() {
        int red = getSizeOfTeam(Team.RED);
        int blue = getSizeOfTeam(Team.BLUE);

        if (red > blue) {
            return Team.BLUE;
        }

        return Team.RED;
    }

    public Integer getSizeOfTeam(Team team) {
        int red = 0;
        int blue = 0;
        for (GamePlayer player : playersInGame.values()) {
            if (player.getTeam() == Team.RED) {
                red++;
            }

            if (player.getTeam() == Team.BLUE) {
                blue++;
            }
        }

        if (team == Team.BLUE) {
            return blue;
        }

        return red;
    }

    public String getTimeLeft() {
        if (getStatus().equals(GameStatus.WAITING) || getStatus().equals(GameStatus.STARTING)) {
            return Localizers.getDefaultLoc().GAMESTATUS_NOT_STARTED.getString();
        }

        int time = getTimeLeftSeconds();
        int hours = time / (60 * 60);
        time = time % (60 * 60);
        int minutes = time / 60;
        time = time % 60;

        if (hours == 0) {
            return minutes + "m " + time + "s";
        }

        return Math.abs(hours) + "h " + Math.abs(minutes) + "m " + Math.abs(time) + "s";
    }

    public String getTimeLeftPretty() {
        if (getStatus().equals(GameStatus.WAITING) || getStatus().equals(GameStatus.STARTING)) {
            return Localizers.getDefaultLoc().GAMESTATUS_NOT_STARTED.getString();
        }
        Integer time = getTimeLeftSeconds();
        int hours = time / (60 * 60);
        time = time % (60 * 60);
        int minutes = time / 60;
        time = time % 60;

        String minute = "minutes";
        if (minutes == 1) {
            minute = "minute";
        }

        if (hours == 0) {
            if (time == 0) {
                return minutes + " " + ChatColor.BLUE + minute;
            }
            if (minutes == 0) {
                return time + " " + ChatColor.BLUE + "seconds";
            }
            return minutes + " " + ChatColor.BLUE + minute + " " + ChatColor.AQUA + time + " " + ChatColor.BLUE + "seconds";
        }
        return Math.abs(hours) + "h " + Math.abs(minutes) + "m " + Math.abs(time) + "s";
    }

    public String getPrettyStatus() {
        GameStatus status = getStatus();
        if (status == GameStatus.INGAME) {
            return Localizers.getDefaultLoc().GAMESTATUS_INGAME.getString();
        } else if (status == GameStatus.STARTING) {
            return Localizers.getDefaultLoc().GAMESTATUS_STARTING.getString();
        } else if (status == GameStatus.WAITING) {
            return Localizers.getDefaultLoc().GAMESTATUS_WAITING.getString();
        } else if (status == GameStatus.DISABLED) {
            return Localizers.getDefaultLoc().GAMESTATUS_DISABLED.getString();
        }
        return "ERROR";
    }

    public void broadcast(String message) {
        for (GamePlayer player : playersInGame.values()) {
            player.getPlayer().sendMessage(message);
        }
        for (SpectatePlayer sp : SpectateCommand.getCommand().getSpectators(this)) {
            sp.player.sendMessage(message);
        }
    }

    public void broadcast(String message, Team team) {
        for (GamePlayer player : playersInGame.values()) {
            if (player.getTeam() == team) {
                player.getPlayer().sendMessage(message);
            }
        }
    }

    /*
     * public Queue getQueue() { return queue; }
     */

    public boolean isFull() {
        return playersInGame.size() >= map.getPlayerlimit();
    }

    public Map getMap() {
        return map;
    }

}
