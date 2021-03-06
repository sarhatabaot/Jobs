package com.gamingmesh.jobs.config;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.gamingmesh.jobs.api.JobsScheduleStartEvent;
import com.gamingmesh.jobs.api.JobsScheduleStopEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.BoostMultiplier;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.Schedule;
import com.gamingmesh.jobs.stuff.TimeManage;

public class ScheduleManager {

    private Jobs plugin;
    private int autoTimerBukkitId = -1;

    public static final List<Schedule> BOOSTSCHEDULE = new ArrayList<>();

    public ScheduleManager(Jobs plugin) {
	this.plugin = plugin;
    }

    public void start() {
	if (BOOSTSCHEDULE.isEmpty())
	    return;

	cancel();
	autoTimerBukkitId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::scheduler, 20, 30 * 20L);
    }

    public void cancel() {
	if (autoTimerBukkitId != -1)
	    Bukkit.getScheduler().cancelTask(autoTimerBukkitId);
    }

    public int getDateByInt() {
	return TimeManage.timeInInt();
    }

    private boolean scheduler() {
	if (BOOSTSCHEDULE.isEmpty())
	    return false;

	DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
	Date date = new Date();

	String currenttime = dateFormat.format(date);

	int Current = Integer.parseInt(currenttime.replace(":", ""));

	String CurrentDayName = getWeekDay();

	for (Schedule one : BOOSTSCHEDULE) {

	    int From = one.getFrom();
	    int Until = one.getUntil();

	    List<String> days = one.getDays();

	    if (one.isStarted() && one.getBroadcastInfoOn() < System.currentTimeMillis() && one.getBroadcastInterval() > 0) {
		one.setBroadcastInfoOn(System.currentTimeMillis() + one.getBroadcastInterval() * 60 * 1000);
		one.getMessageToBroadcast().forEach(Bukkit::broadcastMessage);
	    }

	    if (((one.isNextDay() && (Current >= From && Current < Until || Current >= one.getNextFrom() && Current < one.getNextUntil()) && !one
		.isStarted()) || !one.isNextDay() && (Current >= From && Current < Until)) && (days.contains(CurrentDayName) || days.contains("all")) && !one
		    .isStarted()) {

		JobsScheduleStartEvent event = new JobsScheduleStartEvent(one);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
		    continue;
		}

		if (one.isBroadcastOnStart())
		    if (one.getMessageOnStart().isEmpty())
			Bukkit.broadcastMessage(Jobs.getLanguage().getMessage("message.boostStarted"));
		    else
			one.getMessageOnStart().forEach(Bukkit::broadcastMessage);

		for (Job onejob : one.getJobs()) {
		    onejob.setBoost(one.getBoost());
		}

		one.setBroadcastInfoOn(System.currentTimeMillis() + one.getBroadcastInterval() * 60 * 1000);

		one.setStarted(true);
		one.setStoped(false);
		break;
	    } else if (((one.isNextDay() && Current > one.getNextUntil() && Current < one.getFrom() && !one.isStoped()) || !one.isNextDay() && Current > Until
		&& ((days.contains(CurrentDayName)) || days.contains("all"))) && !one.isStoped()) {
		JobsScheduleStopEvent event = new JobsScheduleStopEvent(one);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
		    continue;
		}

		if (one.isBroadcastOnStop())
		    if (one.getMessageOnStop().isEmpty())
			Bukkit.broadcastMessage(Jobs.getLanguage().getMessage("message.boostStoped"));
		    else
			one.getMessageOnStop().forEach(Bukkit::broadcastMessage);

		for (Job onejob : one.getJobs()) {
		    onejob.setBoost(new BoostMultiplier());
		}

		one.setStoped(true);
		one.setStarted(false);
	    }

	}
	return true;
    }

    public static String getWeekDay() {
	Calendar c = Calendar.getInstance();
	int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
	switch (dayOfWeek) {
	case 2:
	    return "monday";
	case 3:
	    return "tuesday";
	case 4:
	    return "wednesday";
	case 5:
	    return "thursday";
	case 6:
	    return "friday";
	case 7:
	    return "saturday";
	case 1:
	    return "sunday";
	default:
	    break;
	}
	return "all";
    }

    /**
     * Method to load the scheduler configuration
     * 
     * loads from Jobs/schedule.yml
     */
    public void load() {
	BOOSTSCHEDULE.clear();

	YmlMaker jobSchedule = new YmlMaker(plugin, "schedule.yml");
	jobSchedule.saveDefaultConfig();

	YamlConfiguration conf = YamlConfiguration.loadConfiguration(jobSchedule.getConfigFile());

	conf.options().copyDefaults(true);

	if (!conf.contains("Boost"))
	    return;

	ArrayList<String> sections = new ArrayList<>(conf.getConfigurationSection("Boost").getKeys(false));

	for (String OneSection : sections) {
	    ConfigurationSection path = conf.getConfigurationSection("Boost." + OneSection);

	    if (!path.getBoolean("Enabled", false))
		continue;

	    Schedule sched = new Schedule();
	    sched.setName(OneSection);

	    if (!path.getString("From", "").contains(":") || !path.getString("Until", "").contains(":")
		|| !path.isList("Days") || !path.isList("Jobs"))
		continue;

	    sched.setDays(path.getStringList("Days"));
	    sched.setJobs(path.getStringList("Jobs"));
	    sched.setFrom(Integer.valueOf(path.getString("From").replace(":", "")));
	    sched.setUntil(Integer.valueOf(path.getString("Until").replace(":", "")));

	    if (path.isList("MessageOnStart"))
		sched.setMessageOnStart(path.getStringList("MessageOnStart"), path.getString("From"), path.getString("Until"));

	    if (path.contains("BroadcastOnStart"))
		sched.setBroadcastOnStart(path.getBoolean("BroadcastOnStart"));

	    if (path.isList("MessageOnStop"))
		sched.setMessageOnStop(path.getStringList("MessageOnStop"), path.getString("From"), path.getString("Until"));

	    if (path.contains("BroadcastOnStop"))
		sched.setBroadcastOnStop(path.getBoolean("BroadcastOnStop"));

	    if (path.contains("BroadcastInterval"))
		sched.setBroadcastInterval(path.getInt("BroadcastInterval"));

	    if (path.isList("BroadcastMessage"))
		sched.setMessageToBroadcast(path.getStringList("BroadcastMessage"), path.getString("From"), path.getString("Until"));

	    if (path.isDouble("Exp"))
		sched.setBoost(CurrencyType.EXP, path.getDouble("Exp", 0D));
	    if (path.isDouble("Money"))
		sched.setBoost(CurrencyType.MONEY, path.getDouble("Money", 0D));
	    if (path.isDouble("Points"))
		sched.setBoost(CurrencyType.POINTS, path.getDouble("Points", 0D));

	    BOOSTSCHEDULE.add(sched);
	}

	if (!BOOSTSCHEDULE.isEmpty())
	    Jobs.consoleMsg("&e[Jobs] Loaded " + BOOSTSCHEDULE.size() + " schedulers!");
    }
}
