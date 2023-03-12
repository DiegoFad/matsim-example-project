/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.evDetour;


/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecifications;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehiclesFactory;
import playground.vsp.ev.RunUrbanEVExample;
import playground.vsp.ev.UrbanEVConfigGroup;
import playground.vsp.ev.UrbanEVModule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * this is an example of how to run MATSim with the UrbanEV module which inserts charging activities for all legs which use a EV.
 * By default, {@link} is used, which declares any vehicle as an EV
 * that has a vehicle type with HbefaTechnology set to 'electricity'.
 * At the beginning of each iteration, the consumption is estimated. Charging is planned to take place during the latest possible activity in the agent's plan
 * that fits certain criteria (ActivityType and minimum duration) and takes place before the estimated SOC drops below a defined threshold.
 */
public class RunMyUrbanEVExample{
	private static final Logger log = LogManager.getLogger(RunMyUrbanEVExample.class );
	static final double CAR_BATTERY_CAPACITY_kWh = 20.;
	static final double CAR_INITIAL_SOC = 0.5;
	public static void main(String[] args) {
		EvConfigGroup evConfigGroup = new EvConfigGroup();
		evConfigGroup.timeProfiles = true;
		evConfigGroup.chargersFile = "chargers993.xml";
		evConfigGroup.minimumChargeTime = 600;
		evConfigGroup.numberOfIndividualTimeProfiles = 324;

		String pathToConfig = args.length > 0 ?
				args[0] :
				"test/input/org/matsim/evDetour/chessboard-config.xml";


		Config config = ConfigUtils.loadConfig(pathToConfig, evConfigGroup);
		config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);
		UrbanEVConfigGroup evReplanningCfg = new UrbanEVConfigGroup();
		config.addModule(evReplanningCfg);
		evReplanningCfg.setCriticalSOC(0.4);
		evReplanningCfg.setMaxDistanceBetweenActAndCharger_m(1500.0);
		evReplanningCfg.setMaximumChargingProceduresPerAgent(5);
		evReplanningCfg.setMinWhileChargingActivityDuration_s(600);

		//TODO actually, should also work with all AccessEgressTypes but we have to check (write JUnit test)
		config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.none);

		//register charging interaction activities for car
		config.planCalcScore()
				.addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(TransportMode.car
						+ UrbanEVModule.PLUGOUT_INTERACTION).setScoringThisActivityAtAll(false ) );
		config.planCalcScore()
				.addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(
						TransportMode.car + UrbanEVModule.PLUGIN_INTERACTION).setScoringThisActivityAtAll(
						false ));
		// (if you need to change anything in the static RunUrbanEVExample methods, you need to inline them.  refactor --> inline method.)

		// ---

		Scenario scenario = ScenarioUtils.loadScenario(config);
		//TODO setting vehicle battery capacity and initial SoC not accessible
		VehiclesFactory vehicleFactory = scenario.getVehicles().getFactory();

		for (Person person : scenario.getPopulation().getPersons().values()) {

			VehicleType carVehicleType = vehicleFactory.createVehicleType(Id.create(person.getId().toString(),
					VehicleType.class)); //TODO should at least have a suffix "_car"
			VehicleUtils.setHbefaTechnology(carVehicleType.getEngineInformation(), "electricity");
			VehicleUtils.setEnergyCapacity(carVehicleType.getEngineInformation(), RunMyUrbanEVExample.CAR_BATTERY_CAPACITY_kWh);
			ElectricVehicleSpecifications.setChargerTypes(carVehicleType.getEngineInformation(), Arrays.asList("a", "b", "default"));
			scenario.getVehicles().addVehicleType(carVehicleType);
			//Vehicle from output_plans from chessboard scenario have no suffix. Therefore changed to personId
			Vehicle carVehicle = vehicleFactory.createVehicle(Id.createVehicleId(person.getId()),
					carVehicleType);
			ElectricVehicleSpecifications.setInitialSoc(carVehicle, RunMyUrbanEVExample.CAR_INITIAL_SOC);
			scenario.getVehicles().addVehicle(carVehicle);

			VehicleType bikeVehicleType = vehicleFactory.createVehicleType(
					Id.create(person.getId().toString() + "_bike", VehicleType.class));
			Vehicle bikeVehicle = vehicleFactory.createVehicle(VehicleUtils.createVehicleId(person, TransportMode.bike),
					bikeVehicleType);

			scenario.getVehicles().addVehicleType(bikeVehicleType);
			scenario.getVehicles().addVehicle(bikeVehicle);

			Map<String, Id<Vehicle>> mode2Vehicle = new HashMap<>();
			mode2Vehicle.put(TransportMode.car, carVehicle.getId());
			mode2Vehicle.put(TransportMode.bike, bikeVehicle.getId());

			//override the attribute - we assume to need car and bike only
			VehicleUtils.insertVehicleIdsIntoAttributes(person, mode2Vehicle);
		}

		// ---

		Controler controler = RunUrbanEVExample.prepareControler(scenario );

		controler.addOverridingModule( new AbstractModule(){
			@Override public void install(){
				//this.addEventHandlerBinding().toInstance( new TeleportationArrivalEventHandler(){
				this.addEventHandlerBinding().toInstance(new ChargingDetourHandler());
				this.addMobsimListenerBinding().toInstance(new EVDetourMobsimListener());
				this.bind(ChargingDetourHandler.class);


					//@Override public void handleEvent( TeleportationArrivalEvent event ){
						//log.info( event );
						// (the "travelled" event)
					//}
				//} );

			}
		} );

		//controler.addOverridingModule( new OTFVisLiveModule() );
		// (this switches on the visualizer.  comment out if not needed)

		controler.run();
	}

}
