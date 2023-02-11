package org.matsim.evDetour;

import com.google.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.controler.IterationCounter;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.mobsim.framework.events.MobsimBeforeCleanupEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeCleanupListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class EVDetourMobsimListener implements MobsimBeforeCleanupListener {

    @Inject
    private ChargingDetourHandler chargingDetourHandler;
    @Inject
    private OutputDirectoryHierarchy controlerIO;
    @Inject
    private IterationCounter iterationCounter;


    @Override
    public void notifyMobsimBeforeCleanup(MobsimBeforeCleanupEvent e) {
        CSVPrinter csvPrinter;

        try {
            csvPrinter = new CSVPrinter(Files.newBufferedWriter(Paths.get(controlerIO.getIterationFilename(iterationCounter.getIterationNumber(), "chargingDetourData.csv"))), CSVFormat.DEFAULT.withDelimiter(',').
                    withHeader("PersonId", "ChargerId", "ChargingLinkId", "ActivityLinkId", "PluginTripDistance", "PlugoutTripDistance",
                            "PluginTime", "PlugoutTime"));
            Map<Id<Person>, List<ChargingDetourHandler.ChargingProcess>> personsToChargingProcesses = chargingDetourHandler.getPersonsToChargingProcesses();
            for (List<ChargingDetourHandler.ChargingProcess> chargingProcesses : personsToChargingProcesses.values()) {
                for(ChargingDetourHandler.ChargingProcess chargingProcess : chargingProcesses){
                    csvPrinter.printRecord(chargingProcess.getPersonId(), chargingProcess.getChargerId(), chargingProcess.getChargingLinkId(),
                            chargingProcess.getActivityLinkId(), chargingProcess.getPluginTripDistance(),chargingProcess.getPlugoutTripDistance(),
                            chargingProcess.getPluginTime(), chargingProcess.getPlugoutTime());
                }
            }
            csvPrinter.close();

        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
