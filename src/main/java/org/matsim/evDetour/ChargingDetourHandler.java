package org.matsim.evDetour;

import com.google.inject.Inject;
import javafx.collections.ListChangeListener;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.charging.ChargingStartEvent;
import org.matsim.contrib.ev.charging.ChargingStartEventHandler;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;
import org.matsim.core.controler.IterationCounter;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.mobsim.framework.events.MobsimBeforeCleanupEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeCleanupListener;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ChargingDetourHandler implements TeleportationArrivalEventHandler,
        ActivityStartEventHandler, ActivityEndEventHandler, MobsimBeforeCleanupListener {

    @Inject
    OutputDirectoryHierarchy controlerIO;
    @Inject
    IterationCounter iterationCounter;

    private final Map<Id<Person>, Boolean> personsToCharging = new HashMap<>();
    private final Map<Id<Person>, Boolean> personsToPlugin = new HashMap<>();
    static final Map<Id<Person>,List<ChargingProcess>> personsToChargingProcesses = new HashMap<>();

    public Map<Id<Person>, List<ChargingProcess>> getPersonsToChargingProcesses(){return personsToChargingProcesses;}

    @Override
    public void handleEvent(TeleportationArrivalEvent teleportationArrivalEvent) {

        if(Objects.equals(teleportationArrivalEvent.getMode(), "walk") && personsToPlugin.get(teleportationArrivalEvent.getPersonId())){
            List<ChargingProcess> chargingProcesses = personsToChargingProcesses.get(teleportationArrivalEvent.getPersonId());
            ChargingProcess chargingProcess = chargingProcesses.get(chargingProcesses.size()-1);
            personsToCharging.putIfAbsent(teleportationArrivalEvent.getPersonId(), false);
            if(!personsToCharging.get(teleportationArrivalEvent.getPersonId())) {
                chargingProcess.setPluginTripDistance(teleportationArrivalEvent.getDistance());
                personsToCharging.put(teleportationArrivalEvent.getPersonId(), true);
         }
            else {
                chargingProcess.setPlugoutTripDistance(teleportationArrivalEvent.getDistance());
                personsToCharging.put(teleportationArrivalEvent.getPersonId(), false);
            }
        }
    }

    @Override
    public void handleEvent(ActivityStartEvent activityStartEvent) {

        personsToCharging.putIfAbsent(activityStartEvent.getPersonId(), false);
        personsToPlugin.putIfAbsent(activityStartEvent.getPersonId(), false);

        if(activityStartEvent.getActType().endsWith("plugin interaction")){

            List<ChargingProcess> chargingProcesses = personsToChargingProcesses.get(activityStartEvent.getPersonId());
            if(chargingProcesses == null){chargingProcesses = new ArrayList<>();}
            //Create new Charging Process
            chargingProcesses.add(new ChargingProcess(activityStartEvent.getPersonId(), activityStartEvent.getLinkId()));
            personsToChargingProcesses.put(activityStartEvent.getPersonId(), chargingProcesses);
            //Plugin Persons Vehicle
            personsToPlugin.put(activityStartEvent.getPersonId(), true);
            ChargingProcess chargingProcess = chargingProcesses.get(chargingProcesses.size()-1);
            chargingProcess.setPluginTime(activityStartEvent.getTime());

        }
        if(personsToCharging.get(activityStartEvent.getPersonId()) && personsToPlugin.get(activityStartEvent.getPersonId())){
            List<ChargingProcess> chargingProcesses = personsToChargingProcesses.get(activityStartEvent.getPersonId());
            ChargingProcess chargingProcess = chargingProcesses.get(chargingProcesses.size()-1);
            if(chargingProcess.activityLinkId == null) {
                chargingProcess.setActivityLinkId(activityStartEvent.getLinkId());
            }
        }
    }

    @Override
    public void handleEvent(ActivityEndEvent activityEndEvent) {
        if(activityEndEvent.getActType().endsWith("plugout interaction")) {
            personsToPlugin.put(activityEndEvent.getPersonId(), false);
            List<ChargingProcess> chargingProcesses = personsToChargingProcesses.get(activityEndEvent.getPersonId());
            ChargingProcess chargingProcess = chargingProcesses.get(chargingProcesses.size()-1);
            chargingProcess.setPlugoutTime(activityEndEvent.getTime());
        }
    }

    @Override
    public void notifyMobsimBeforeCleanup(MobsimBeforeCleanupEvent e) {
        CSVPrinter csvPrinter;

        try {
            csvPrinter = new CSVPrinter(Files.newBufferedWriter(Paths.get(controlerIO.getIterationFilename(iterationCounter.getIterationNumber(), "chargingDetourData.csv"))), CSVFormat.DEFAULT.withDelimiter(',').
                    withHeader("PersonId", "ChargerId", "ChargingLinkId", "ActivityLinkId", "PluginTripDistance", "PlugoutTripDistance",
                            "PluginTime", "PlugoutTime"));

            for (List<ChargingProcess> chargingProcesses : personsToChargingProcesses.values()) {
                for(ChargingProcess chargingProcess : chargingProcesses){
                    csvPrinter.printRecord(chargingProcess.personId, chargingProcess.chargerId, chargingProcess.chargingLinkId,
                            chargingProcess.activityLinkId, chargingProcess.pluginTripDistance,chargingProcess.plugoutTripDistance,
                            chargingProcess.pluginTime, chargingProcess.plugoutTime);
                }
            }
            csvPrinter.close();

        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void reset(int iteration) {
        personsToCharging.clear();
        personsToPlugin.clear();
        personsToChargingProcesses.clear();
    }

    public static class ChargingProcess{
//TODO Map vehicle ID to person ID to get access to Charger ID
        private Id<Charger> chargerId;
        private Id<Person> personId;
        private Id<Link> activityLinkId;
        final private Id<Link> chargingLinkId;
        double pluginTripDistance = 0.0;

        double plugoutTripDistance = 0.0;

        double pluginTime;
        double plugoutTime;

        public void setPluginTime(double pluginTime) {
            this.pluginTime = pluginTime;
        }

        public void setPlugoutTime(double plugoutTime) {
            this.plugoutTime = plugoutTime;
        }

        public void setActivityLinkId(Id<Link> activityLinkId) {
            this.activityLinkId = activityLinkId;
        }

        public void setPluginTripDistance(double pluginTripDistance) {
            this.pluginTripDistance = pluginTripDistance;
        }

        public void setPlugoutTripDistance(double plugoutTripDistance) {
            this.plugoutTripDistance = plugoutTripDistance;
        }

        public ChargingProcess(Id<Person> personId, Id<Link> chargingLinkId) {

            this.personId = personId;
            this.chargingLinkId = chargingLinkId;
        }
        public Id<Charger> getChargerId() {
            return chargerId;
        }

        public Id<Person> getPersonId() {
            return personId;
        }

        public Id<Link> getActivityLinkId() {
            return activityLinkId;
        }

        public Id<Link> getChargingLinkId() {
            return chargingLinkId;
        }

        public double getPluginTripDistance() {
            return pluginTripDistance;
        }

        public double getPlugoutTripDistance() {
            return plugoutTripDistance;
        }

        public double getPluginTime() {
            return pluginTime;
        }

        public double getPlugoutTime() {
            return plugoutTime;
        }

    }
}
