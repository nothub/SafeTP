package not.hub.safetp;

import io.papermc.lib.PaperLib;
import not.hub.safetp.tasks.ClearOldRequestsRunnable;
import not.hub.safetp.tasks.UnvanishRunnable;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SafeTP extends JavaPlugin {

    private RequestManager requestManager = new RequestManager();

    private boolean multiRequest;
    private int timeoutValue;
    private int unvanishDelay;

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        loadConfig();
        multiRequest = getConfig().getBoolean("multi-target-request");
        timeoutValue = getConfig().getInt("request-timeout-seconds");
        unvanishDelay = getConfig().getInt("unvanish-delay-ticks");
        if (unvanishDelay == 0) {
            unvanishDelay = 1;
        }
        new ClearOldRequestsRunnable(this).runTaskTimer(this, 0, 20);
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String commandLabel, String[] args) {

        Player sender = null;

        if (commandSender instanceof Player) {
            sender = (Player) commandSender;
        }

        if (sender == null) {
            return false;
        }

        if (args.length == 0) {
            sendMessage(sender, ChatColor.GOLD + "You need to run this command with an argument, like this:");
            sendMessage(sender, "/tpa NAME " + ChatColor.GOLD + ".. or .. " + ChatColor.RESET + "/tpy NAME " + ChatColor.GOLD + ".. or .. " + ChatColor.RESET + "/tpn NAME");
            return false;
        }

        if (isInvalidTarget(args[0])) {
            sendMessage(sender, ChatColor.GOLD + "Player not found.");
            return false;
        }

        if (sender.getName().equalsIgnoreCase(args[0])) {
            sendMessage(sender, ChatColor.GOLD + "You cant run this command on yourself!");
            return false;
        }

        if (command.getLabel().equalsIgnoreCase("tpa")) {
            askTP(getServer().getPlayer(args[0]), sender);
            return true;
        }

        if (command.getLabel().equalsIgnoreCase("tpy")) {
            acceptTP(sender, getServer().getPlayer(args[0]));
            return true;
        }

        if (command.getLabel().equalsIgnoreCase("tpn")) {
            denyTP(sender, getServer().getPlayer(args[0]));
            return true;
        }

        return false;

    }

    static void sendMessage(Player player, String message) {
        player.sendMessage(message);
    }

    public void clearOldRequests() {
        requestManager.clearOldRequests(timeoutValue);
    }

    public void unvanish(Player player) {
        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.showPlayer(this, player);
            }
        }
    }

    private void askTP(Player tpTarget, Player tpRequester) {

        if (tpTarget == null || tpRequester == null) {
            return;
        }

        if (requestManager.isRequestExisting(tpTarget, tpRequester)) {
            sendMessage(tpRequester, ChatColor.GOLD + "Please wait for " + ChatColor.RESET + tpTarget.getDisplayName() + ChatColor.GOLD + " to accept or deny your request.");
            return;
        }

        if (!multiRequest && requestManager.isRequester(tpRequester)) {
            sendMessage(tpRequester, ChatColor.GOLD + "Please wait for your existing request to be accepted or denied.");
            return;
        }

        sendMessage(tpRequester, ChatColor.GOLD + "Request sent to: " + ChatColor.RESET + tpTarget.getDisplayName());
        sendMessage(tpTarget, tpRequester.getDisplayName() + ChatColor.GOLD + " wants to teleport to you.");
        sendMessage(tpTarget, ChatColor.GOLD + "Type " + ChatColor.RESET + "/tpy " + tpRequester.getName() + ChatColor.GOLD + " to accept or " + ChatColor.RESET + "/tpn " + tpRequester.getName() + ChatColor.GOLD + " to deny.");

        requestManager.addRequest(tpTarget, tpRequester);

    }

    private void acceptTP(Player tpTarget, Player tpRequester) {

        if (tpTarget == null || tpRequester == null) {
            return;
        }

        if (!requestManager.isRequestExisting(tpTarget, tpRequester)) {
            sendMessage(tpTarget, ChatColor.GOLD + "There is no request to accept from " + ChatColor.RESET + tpRequester.getDisplayName() + ChatColor.GOLD + "!");
            return;
        }

        sendMessage(tpTarget, ChatColor.GOLD + "Request from " + ChatColor.RESET + tpRequester.getDisplayName() + ChatColor.GREEN + " accepted" + ChatColor.GOLD + "!");
        sendMessage(tpRequester, ChatColor.GOLD + "Your request was " + ChatColor.GREEN + "accepted" + ChatColor.GOLD + ", teleporting to: " + ChatColor.RESET + tpTarget.getDisplayName());

        executeTP(tpTarget, tpRequester);
        requestManager.removeRequest(tpTarget, tpRequester);

    }

    private void denyTP(Player tpTarget, Player tpRequester) {

        if (tpTarget == null || tpRequester == null) {
            return;
        }

        if (!requestManager.isRequestExisting(tpTarget, tpRequester)) {
            sendMessage(tpTarget, ChatColor.GOLD + "There is no request to deny from " + ChatColor.RESET + tpRequester.getDisplayName() + ChatColor.GOLD + "!");
            return;
        }

        sendMessage(tpTarget, ChatColor.GOLD + "Request from " + ChatColor.RESET + tpRequester.getDisplayName() + ChatColor.RED + " denied" + ChatColor.GOLD + "!");
        sendMessage(tpRequester, ChatColor.GOLD + "Your request sent to " + ChatColor.RESET + tpTarget.getDisplayName() + ChatColor.GOLD + " was" + ChatColor.RED + " denied" + ChatColor.GOLD + "!");
        requestManager.removeRequest(tpTarget, tpRequester);

    }

    private void executeTP(Player tpTarget, Player tpRequester) {

        if (tpTarget == null || tpRequester == null) {
            return;
        }

        getLogger().info("Teleporting " + tpRequester.getName() + " to " + tpTarget.getName());

        // vanish requester
        vanish(tpRequester);

        // execute teleport
        PaperLib.teleportAsync(tpRequester, tpTarget.getLocation()).thenAccept(result -> {

            if (result) {
                sendMessage(tpTarget, tpRequester.getDisplayName() + ChatColor.GOLD + " teleported to you!");
                sendMessage(tpRequester, ChatColor.GOLD + "Teleported to " + ChatColor.RESET + tpTarget.getDisplayName() + ChatColor.GOLD + "!");
            } else {
                sendMessage(tpTarget, ChatColor.RED + "Teleport failed, you should harass your admin because of this!");
                sendMessage(tpRequester, ChatColor.RED + "Teleport failed, you should harass your admin because of this!");
            }

        });

        // unvanish requester after n ticks
        new UnvanishRunnable(this, tpRequester).runTaskLater(this, unvanishDelay);

    }

    private boolean isInvalidTarget(String args) {

        // check for empty argument
        if (args.isEmpty()) {
            return true;
        }

        // check for invalid usernames
        String target = sanitizeUsername(args);
        if (target == null) {
            return true;
        }

        // check if player is online
        return getServer().getPlayer(target) == null;

    }

    private String sanitizeUsername(String name) {

        name = name.replaceAll("[^a-zA-Z0-9_]", "");

        if (name.length() < 3 || name.length() > 16) {
            return null;
        }

        return name;

    }

    private void vanish(Player player) {
        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.hidePlayer(this, player);
            }
        }
    }

    private void loadConfig() {
        getConfig().addDefault("multi-target-request", true);
        getConfig().addDefault("request-timeout-seconds", 60);
        getConfig().addDefault("unvanish-delay-ticks", 20);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

}
