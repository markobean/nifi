package org.apache.nifi.toolkit.encryptconfig;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class EncryptConfigMainJava {

    private static final Logger logger = LoggerFactory.getLogger(EncryptConfigMainJava.class);

    static final int EXIT_STATUS_SUCCESS = 0;
    static final int EXIT_STATUS_FAILURE = -1;
    static final int EXIT_STATUS_OTHER = 1;

    static final String NIFI_REGISTRY_OPT = "nifiRegistry";
    static final String NIFI_REGISTRY_FLAG = "--" + NIFI_REGISTRY_OPT;
    static final String DECRYPT_OPT = "decrypt";
    static final String DECRYPT_FLAG = "--" + DECRYPT_OPT;

    static final int HELP_FORMAT_WIDTH = 160;


    static void printUsage(String message) {
        debugPrint("printUsage called with message: " + message);
        if (message != null) {
            System.out.println(message);
            System.out.println();
        }

        // TODO: debug only
        debugPrint("  ---   Detailed usage of EncryptConfigMain goes here   ---");
        debugPrint("");

        String header = "\nThis tool enables easy encryption and decryption of configuration files for NiFi and its sub-projects. "
                + "Unprotected files can be input to this tool to be protected by a key in a manner that is understood by NiFi. "
                + "Protected files, along with a key, can be input to this tool to be unprotected, for troubleshooting or automation purposes.\n\n";

        Options options = new Options();
        options.addOption("h", "help", false, "Show usage information (this message)");
        options.addOption(null, NIFI_REGISTRY_OPT, false, "Specifies to target NiFi Registry. When this flag is not included, NiFi is the target.");

        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.setWidth(160);
        helpFormatter.setOptionComparator(null);
        helpFormatter.printHelp(EncryptConfigMainJava.class.getCanonicalName() + " [-h] [options]", header, options, "\n");
        System.out.println();

        helpFormatter.setSyntaxPrefix(""); // disable "usage: " prefix for the following outputs

        Options nifiModeOptions = EncryptConfigTool.getCliOptions(); //ConfigEncryptionTool.getCliOptions();
        nifiModeOptions.addOption(null, DECRYPT_OPT, false, "TODO: add decrypt option for NiFi mode similar to NiFi Registry mode");
        helpFormatter.printHelp(
                "When targeting NiFi:",
                nifiModeOptions,
                false);
        System.out.println();

        // TODO: update to java version of NiFiRegistryMode
//        Options nifiRegistryModeOptions = NiFiRegistryMode.getCliOptions();
//        nifiRegistryModeOptions.addOption(null, DECRYPT_OPT, false, "Can be used with -r to decrypt a previously encrypted NiFi Registry Properties file. Decrypted content is printed to STDOUT.");
//        helpFormatter.printHelp(
//                "When targeting NiFi Registry using the ${NIFI_REGISTRY_FLAG} flag:",
//                nifiRegistryModeOptions,
//                false);
//        System.out.println();
    }

    static void printUsageAndExit(int exitStatusCode) {
        debugPrint("no message here");
        System.exit(exitStatusCode);
    }

    static void printUsageAndExit(String message, int exitStatusCode) {
        printUsage(message);
        System.exit(exitStatusCode);
    }

    // TODO: remove this method and any references to it; only using during development phase
    static void debugPrint(String msg) {
        System.out.println("[DEBUG] " + msg);
    }

    public static void main(String[] args) {
        debugPrint("Starting EncryptConfigMain");

        if (args.length < 1) {
            printUsageAndExit(EXIT_STATUS_FAILURE);
        }

        if ("-h".equals(args[0]) || "--help".equals(args[0])) {
            printUsageAndExit(EXIT_STATUS_OTHER);
        }

        try {
            List<String> argsList = Arrays.asList(args);
            ToolMode toolMode = determineModeFromArgs(argsList);
            if (toolMode != null) {
                toolMode.run(argsList);
                System.exit(EXIT_STATUS_SUCCESS);
            } else {
                debugPrint("toolMode is null");
                printUsageAndExit("Could not determine tool mode based on options provided. See usage for details of valid options.", EXIT_STATUS_FAILURE);
            }
        } catch (Throwable t) {
            logger.error("", t);
            printUsageAndExit(t.getMessage(), EXIT_STATUS_FAILURE);
        }
    }

    private static ToolMode determineModeFromArgs(List<String> args) {
        debugPrint("determinModeFromArgs");
        if (args.contains(NIFI_REGISTRY_FLAG)) {
            args.remove(NIFI_REGISTRY_FLAG);
            if (args.contains(DECRYPT_FLAG)) {
                args.remove(DECRYPT_FLAG);
                debugPrint("toolMode = registryDecrypt");
                return null; //new NiFiRegistryDecryptMode();
            } else {
                debugPrint("toolMode = registry");
                return null; //new NiFiRegistryMode();
            }
        } else {
            debugPrint("no registry flag");
            if (args.contains(DECRYPT_FLAG)) {
                logger.error("The {} flag is only available when running in {} mode and targeting nifi-registry.properties to allow for displaying plaintext values of encrypted properties.",
                        DECRYPT_FLAG, NIFI_REGISTRY_FLAG);
                return null;
            } else {
                debugPrint("toolMode = legacy (aka NiFI)");
                return new LegacyMode();
            }
        }
    }
}
