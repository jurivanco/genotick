package com.alphatica.genotick.genotick;

import com.alphatica.genotick.data.DataSetName;
import com.alphatica.genotick.timepoint.SetStats;
import com.alphatica.genotick.timepoint.TimePointExecutor;
import com.alphatica.genotick.timepoint.TimePointExecutorFactory;
import com.alphatica.genotick.timepoint.TimePointStats;
import com.alphatica.genotick.breeder.BreederSettings;
import com.alphatica.genotick.breeder.RobotBreeder;
import com.alphatica.genotick.breeder.RobotBreederFactory;
import com.alphatica.genotick.data.DataFactory;
import com.alphatica.genotick.data.DataLoader;
import com.alphatica.genotick.data.MainAppData;
import com.alphatica.genotick.killer.RobotKiller;
import com.alphatica.genotick.killer.RobotKillerFactory;
import com.alphatica.genotick.killer.RobotKillerSettings;
import com.alphatica.genotick.mutator.Mutator;
import com.alphatica.genotick.mutator.MutatorFactory;
import com.alphatica.genotick.mutator.MutatorSettings;
import com.alphatica.genotick.population.*;
import com.alphatica.genotick.processor.RobotExecutorFactory;
import com.alphatica.genotick.ui.UserInput;
import com.alphatica.genotick.ui.UserOutput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Application {
    private final UserOutput output;
    private final UserInput input;

    public Application(UserInput input, UserOutput output) {
        this.input = input;
        this.output = output;
    }

    public void start(MainSettings mainSettings, MainAppData data) {
        if(!validateSettings(mainSettings))
            return;
        logSettings(mainSettings);
        RobotKiller killer = getRobotKiller(mainSettings);
        Mutator mutator = getMutator(mainSettings);
        RobotBreeder breeder = wireBreeder(mainSettings, mutator);
        Population population = wirePopulation(mainSettings);
        Engine engine = wireEngine(mainSettings, data, killer, breeder, population);
        DataSetResult.setThreshold(mainSettings.resultThreshold);
        List<TimePointStats> results = engine.start(output);
        showSummary(results);
    }

    public MainAppData createData(String dataSettings) {
        DataLoader dataLoader = DataFactory.getDefaultLoader(dataSettings);
        return dataLoader.createRobotData();
    }

    private boolean validateSettings(MainSettings settings) {
        try {
            settings.validate();
            return true;
        } catch(IllegalArgumentException ex) {
            output.errorMessage(ex.getMessage());
            return false;
        }
    }

    private Engine wireEngine(MainSettings mainSettings, MainAppData data, RobotKiller killer,
                              RobotBreeder breeder, Population population) {
        EngineSettings engineSettings = getEngineSettings(mainSettings);
        TimePointExecutor timePointExecutor = wireTimePointExecutor(mainSettings);
        return EngineFactory.getDefaultEngine(engineSettings, data, timePointExecutor, killer, breeder, population);
    }

    private TimePointExecutor wireTimePointExecutor(MainSettings settings) {
        DataSetExecutor dataSetExecutor = new SimpleDataSetExecutor();
        RobotExecutorSettings robotExecutorSettings = new RobotExecutorSettings(settings);
        RobotExecutorFactory robotExecutorFactory = new RobotExecutorFactory(robotExecutorSettings);
        return TimePointExecutorFactory.getDefaultExecutor(dataSetExecutor, robotExecutorFactory);
    }

    private Population wirePopulation(MainSettings settings) {
        PopulationDAO dao = PopulationDAOFactory.getDefaultDAO(settings.populationDAO);
        Population population = PopulationFactory.getDefaultPopulation(dao);
        population.setDesiredSize(settings.populationDesiredSize);
        return population;
    }

    private RobotBreeder wireBreeder(MainSettings settings, Mutator mutator) {
        BreederSettings breederSettings = new BreederSettings(
                settings.minimumOutcomesBetweenBreeding,
                settings.inheritedChildWeight,
                settings.minimumOutcomesToAllowBreeding,
                settings.randomRobotsAtEachUpdate,
                settings.dataMaximumOffset,
                settings.ignoreColumns);
        return RobotBreederFactory.getDefaultBreeder(breederSettings, mutator);
    }

    private RobotKiller getRobotKiller(MainSettings settings) {
        RobotKillerSettings killerSettings = new RobotKillerSettings();
        killerSettings.maximumDeathByAge = settings.maximumDeathByAge;
        killerSettings.maximumDeathByWeight = settings.maximumDeathByWeight;
        killerSettings.probabilityOfDeathByAge = settings.probabilityOfDeathByAge;
        killerSettings.probabilityOfDeathByWeight = settings.probabilityOfDeathByWeight;
        killerSettings.protectRobotsUntilOutcomes = settings.protectRobotsUntilOutcomes;
        killerSettings.protectBestRobots = settings.protectBestRobots;
        killerSettings.killNonPredictingRobots = settings.killNonPredictingRobots;
        killerSettings.requireSymmetricalRobots = settings.requireSymmetricalRobots;
        return RobotKillerFactory.getDefaultRobotKiller(killerSettings);
    }

    private Mutator getMutator(MainSettings settings) {
        MutatorSettings mutatorSettings = new MutatorSettings(
                settings.instructionMutationProbability,
                settings.newInstructionProbability,
                settings.skipInstructionProbability);
        return MutatorFactory.getDefaultMutator(mutatorSettings);
    }


    private EngineSettings getEngineSettings(MainSettings settings) {
        EngineSettings engineSettings = new EngineSettings();
        engineSettings.startTimePoint = settings.startTimePoint;
        engineSettings.endTimePoint = settings.endTimePoint;
        engineSettings.performTraining = settings.performTraining;
        return engineSettings;
    }

    private void logSettings(MainSettings settings) {
        String settingsString = settings.getString();
        Debug.d(settingsString);
    }

    private void showSummary(List<TimePointStats> list) {
        Map<DataSetName,Double> statsResults = new HashMap<>();
        double result = 1;
        for (TimePointStats stats : list) {
            double percent = stats.getPercentEarned();
            recordSetsProfit(stats,statsResults);
            result *= percent / 100.0 + 1;
        }
        showStatsResults(statsResults);
        Debug.d("Final result for genotic:", result);
    }

    private void recordSetsProfit(TimePointStats stats, Map<DataSetName, Double> statsResults) {
        for(Map.Entry<DataSetName,SetStats> entry: stats.listSets()) {
            recordProfit(entry.getKey(),entry.getValue(),statsResults);
        }
    }

    private void recordProfit(DataSetName name, SetStats setStats, Map<DataSetName, Double> statsResults) {
        Double soFar = statsResults.get(name);
        if(soFar == null) {
            soFar = 0.0;
        }
        soFar += setStats.getTotalPercent();
        statsResults.put(name,soFar);
    }

    private void showStatsResults(Map<DataSetName, Double> statsResults) {
        for(Map.Entry<DataSetName,Double> entry: statsResults.entrySet()) {
            Debug.d("Profit for",entry.getKey(),":",entry.getValue());
        }
    }

}
