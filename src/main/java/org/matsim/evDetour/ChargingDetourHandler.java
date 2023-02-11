package org.matsim.evDetour;

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

import java.util.*;

public class ChargingDetourHandler implements TeleportationArrivalEventHandler, ChargingStartEventHandler,
        ActivityStartEventHandler, ActivityEndEventHandler {

    private final Map<Id<Person>, Boolean> personsToCharging = new HashMap<>();
    private final Map<Id<Person>, Boolean> personsToPlugin = new HashMap<>();
    static final Map<Id<Person>,List<ChargingProcess>> personsToChargingProcesses = new HashMap<>();

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
            }
        }
    }

    @Override
    public void handleEvent(ChargingStartEvent chargingStartEvent) {
    }

    @Override
    public void handleEvent(ActivityStartEvent activityStartEvent) {
        if(activityStartEvent.getActType().endsWith("plugin interaction")){

            List<ChargingProcess> chargingProcesses = personsToChargingProcesses.get(activityStartEvent.getPersonId());
            if(chargingProcesses == null){chargingProcesses = new ArrayList<>();}
            //Create new Charging Process
            chargingProcesses.add(new ChargingProcess(activityStartEvent.getPersonId(), activityStartEvent.getLinkId()));
            personsToChargingProcesses.put(activityStartEvent.getPersonId(), chargingProcesses);
            //Plugin Persons Vehicle
            personsToPlugin.putIfAbsent(activityStartEvent.getPersonId(), true);
        }
    }

    @Override
    public void handleEvent(ActivityEndEvent activityEndEvent) {
        if(activityEndEvent.getActType().endsWith("plugout interaction")) {
            personsToCharging.put(activityEndEvent.getPersonId(), false);
        }
    }

    public static class ChargingProcess{

        private Id<Charger> chargerId;
        private Id<Person> personId;
        private Id<Link> activityLinkId;
        final private Id<Link> chargingLinkId;
        double pluginTripDistance = 0.0;
        double plugoutTripDistance = 0.0;


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

    }
}
