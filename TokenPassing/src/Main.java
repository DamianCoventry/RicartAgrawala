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

/**
 * The entry point for the application
 */
public class Main {
    // each of these store a command line parameter
    private static String _ipAddress;
    private static int _portStart;
    private static int _numNodes;
    private static int _idStart;

    /**
     * The entry point for the application
     * @param args parameters that alter run-time behaviour
     */
    public static void main(String[] args) {
        try {
            // all command line arguments are mandatory. this will throw a ParseException if the user has not supplied a
            // valid value for each argument.
            parseCommandLine(args);

            CountDownLatch villagersDone = new CountDownLatch(Villager.NUM_VILLAGERS_PER_NODE);

            for (int i = 0; i < Villager.NUM_VILLAGERS_PER_NODE; ++i) {
                Villager villager = new Villager(villagersDone, _ipAddress, _portStart,
                        _numNodes * Villager.NUM_VILLAGERS_PER_NODE, _idStart + i);
                villager.start();
            }

            // we wait for ALL villagers to finish shopping, not just the villagers within this node. to be especially
            // clear, that's waiting for ALL villagers within ALL nodes. it's done this way because villagers' Receiver
            // threads only unblock when they receive a message. if we're not responding then we're causing starvation.

            villagersDone.await();

            System.out.println("\nAll villagers within this node have shopped " + Villager.MAX_NUM_TIMES_SHOPPED +
                    " times each. Exiting...");
            Thread.sleep(5000);
        }
        catch (ParseException pe) {
            System.out.println("Command line argument parsing error. " + pe.getLocalizedMessage());
            pe.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Extracts required run-time configuration from the supplied command line. We need to know the IP address to use,
     * the port, the number of nodes, and the first port to start counting from.
     * @param args parameters that provide run-time configuration
     * @throws ParseException if the user has not supplied an argument, or has supplied an invalid value for an argument
     */
    private static void parseCommandLine(String[] args) throws ParseException {
        Options options = new Options();

        Option option = new Option("a", "address",true,
                "Local IP address to bind to");
        option.setType(String.class);
        option.setRequired(true);
        options.addOption(option);

        option = new Option("p", "portStart", true,
                "Local port from which the counting begins");
        option.setType(int.class);
        option.setRequired(true);
        options.addOption(option);

        option = new Option("n", "numNodes", true,
                "The number of nodes participating in the simulation");
        option.setType(int.class);
        option.setRequired(true);
        options.addOption(option);

        option = new Option("i", "idStart", true,
                "Villager ID from which the counting begins");
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
}
