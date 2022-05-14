/**
 * Designed and written by Damian Coventry
 * Copyright (c) 2022, all rights reserved
 *
 * Massey University
 * 159.355 Concurrent Systems
 * Assignment 3
 * 2022 Semester 1
 *
 */

import org.apache.commons.cli.*;

import java.util.concurrent.CountDownLatch;

public class Main {
    private static String _ipAddress;
    private static int _portStart;
    private static int _numNodes;
    private static int _idStart;

    private static void parseCommandLine(String[] args) throws ParseException {
        Options options = new Options();

        Option option = new Option("a", "address",true,"Local IP address to bind to");
        option.setType(String.class);
        option.setRequired(true);
        options.addOption(option);

        option = new Option("p", "portStart", true,"Local port from which the counting begins");
        option.setType(int.class);
        option.setRequired(true);
        options.addOption(option);

        option = new Option("n", "numNodes", true,"The number of nodes participating in the simulation");
        option.setType(int.class);
        option.setRequired(true);
        options.addOption(option);

        option = new Option("i", "idStart", true,"Villager ID from which the counting begins");
        option.setType(int.class);
        option.setRequired(true);
        options.addOption(option);

        CommandLineParser parser = new PosixParser();
        CommandLine commandLine = parser.parse(options, args);

        _ipAddress = commandLine.getOptionValue("a");
        _portStart = Integer.parseInt(commandLine.getOptionValue("p"));
        _numNodes = Integer.parseInt(commandLine.getOptionValue("n"));
        _idStart = Integer.parseInt(commandLine.getOptionValue("i"));
    }

    public static void main(String[] args) {
        try {
            parseCommandLine(args);

            CountDownLatch villagersDone = new CountDownLatch(Villager.NUM_VILLAGERS_PER_NODE);

            for (int i = 0; i < Villager.NUM_VILLAGERS_PER_NODE; ++i) {
                Villager villager = new Villager(villagersDone, _ipAddress, _portStart,
                        _numNodes * Villager.NUM_VILLAGERS_PER_NODE, _idStart + i);
                villager.start();
            }

            villagersDone.await();

            System.out.println("\nAll villagers within this node have shopped " + Villager.MAX_NUM_TIMES_SHOPPED   + " times each. Exiting...");
            Thread.sleep(5000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
