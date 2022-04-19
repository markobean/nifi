/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.toolkit.encryptconfig;

import org.apache.commons.cli.*;
import org.apache.nifi.properties.scheme.StandardProtectionSchemeResolver;
import org.apache.nifi.toolkit.tls.commandLine.CommandLineParseException;
import org.apache.nifi.toolkit.tls.commandLine.ExitCode;
import org.apache.nifi.util.NiFiBootstrapUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class NiFiMode implements ToolMode { //extends EncryptConfigTool implements ToolMode {

    private static final Logger logger = LoggerFactory.getLogger(NiFiMode.class);

    private static final String DEFAULT_DESCRIPTION = "This tool reads from a nifi.properties and/or " +
            "login-identity-providers.xml file with plain sensitive configuration values, " +
            "prompts the user for a root key, and encrypts each value. It will replace the " +
            "plain value with the protected value in the same file (or write to a new file if " +
            "specified). It can also be used to migrate already-encrypted values in those " +
            "files or in flow.xml.gz to be encrypted with a new key.";

    private static final String FOOTER = "\nJava home: ${System.getenv(JAVA_HOME)}\nNiFi Toolkit home: ${System.getenv(NIFI_TOOLKIT_HOME)}";

    private static final String HELP_ARG = "help";
    private static final String VERBOSE_ARG = "verbose";
    private static final String NIFI_PROPERTIES_ARG = "niFiProperties";
    private static final String OUTPUT_NIFI_PROPERTIES_ARG = "outputNiFiProperties";
    private static final String LOGIN_IDENTITY_PROVIDERS_ARG = "loginIdentityProviders";
    private static final String OUTPUT_LOGIN_IDENTITY_PROVIDERS_ARG = "outputLoginIdentityProviders";
    private static final String AUTHORIZERS_ARG = "authorizers";
    private static final String OUTPUT_AUTHORIZERS_ARG = "outputAuthorizers";
    private static final String FLOW_XML_ARG = "flowXml";
    private static final String OUTPUT_FLOW_XML_ARG = "outputFlowXml";
    private static final String BOOTSTRAP_CONF_ARG = "bootstrapConf";
    private static final String KEY_ARG = "key";
    private static final String PROTECTION_SCHEME_ARG = "protectionScheme";
    private static final String PASSWORD_ARG = "password";
    private static final String KEY_MIGRATION_ARG = "oldKey";
    private static final String PASSWORD_MIGRATION_ARG = "oldPassword";
    private static final String PROTECTION_SCHEME_MIGRATION_ARG = "oldProtectionScheme";
    private static final String USE_KEY_ARG = "useRawKey";
    private static final String MIGRATION_ARG = "migrate";
    private static final String PROPS_KEY_ARG = "propsKey";
    private static final String DO_NOT_ENCRYPT_NIFI_PROPERTIES_ARG = "encryptFlowXmlOnly";
    private static final String NEW_FLOW_ALGORITHM_ARG = "newFlowAlgorithm";
    private static final String NEW_FLOW_PROVIDER_ARG = "newFlowProvider";
    private static final String TRANSLATE_CLI_ARG = "translateCli";

    private static final StandardProtectionSchemeResolver PROTECTION_SCHEME_RESOLVER = new StandardProtectionSchemeResolver();
    private static final String PROTECTION_SCHEME_DESC = String.format("Selects the protection scheme for encrypted properties. Default is AES_GCM. Valid values: %s",
            PROTECTION_SCHEME_RESOLVER.getSupportedProtectionSchemes());

    // Static holder to avoid re-generating the options object multiple times in an invocation
    private static final Options staticOptions = buildOptions();

    private final String header; // = "\n" + DEFAULT_DESCRIPTION + "\n\n";
    private final Options options;
    private boolean isVerbose = false;
    private boolean isBootstrapKeyRequired = false;

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(Option.builder("h")
                .longOpt(HELP_ARG)
                .hasArg(false)
                .desc("Show usage information (this message)")
                .build());
        options.addOption(Option.builder("v")
                .longOpt(VERBOSE_ARG)
                .hasArg(false)
                .desc("Sets verbose mode (default false)")
                .build());
        options.addOption(Option.builder("n")
                .longOpt(NIFI_PROPERTIES_ARG).
                hasArg(true)
                .argName("file")
                .desc("The nifi.properties file containing unprotected config values (will be overwritten unless -o is specified)")
                .build());
        options.addOption(Option.builder("o")
                .longOpt(OUTPUT_NIFI_PROPERTIES_ARG)
                .hasArg(true).argName("file")
                .desc("The destination nifi.properties file containing protected config values (will not modify input nifi.properties)")
                .build());
        options.addOption(Option.builder("l")
                .longOpt(LOGIN_IDENTITY_PROVIDERS_ARG)
                .hasArg(true)
                .argName("file")
                .desc("The login-identity-providers.xml file containing unprotected config values (will be overwritten unless -i is specified)")
                .build());
        options.addOption(Option.builder("i")
                .longOpt(OUTPUT_LOGIN_IDENTITY_PROVIDERS_ARG)
                .hasArg(true)
                .argName("file")
                .desc("The destination login-identity-providers.xml file containing protected config values (will not modify input login-identity-providers.xml)")
                .build());
        options.addOption(Option.builder("a")
                .longOpt(AUTHORIZERS_ARG)
                .hasArg(true)
                .argName("file")
                .desc("The authorizers.xml file containing unprotected config values (will be overwritten unless -u is specified)")
                .build());
        // TODO: finish formatting
        options.addOption(Option.builder("u").longOpt(OUTPUT_AUTHORIZERS_ARG).hasArg(true).argName("file").desc("The destination authorizers.xml file containing protected config values (will not modify input authorizers.xml)").build());
        // TODO: add option for flow.json (both input and output) Or re-use/dynamically determine xml vs. json
        options.addOption(Option.builder("f").longOpt(FLOW_XML_ARG).hasArg(true).argName("file").desc("The flow.xml.gz file currently protected with old password (will be overwritten unless -g is specified)").build());
        options.addOption(Option.builder("g").longOpt(OUTPUT_FLOW_XML_ARG).hasArg(true).argName("file").desc("The destination flow.xml.gz file containing protected config values (will not modify input flow.xml.gz)").build());
        options.addOption(Option.builder("b").longOpt(BOOTSTRAP_CONF_ARG).hasArg(true).argName("file").desc("The bootstrap.conf file to persist root key and to optionally provide any configuration for the protection scheme.").build());
        options.addOption(Option.builder("S").longOpt(PROTECTION_SCHEME_ARG).hasArg(true).argName("protectionScheme").desc(PROTECTION_SCHEME_DESC).build());
        options.addOption(Option.builder("k").longOpt(KEY_ARG).hasArg(true).argName("keyhex").desc("The raw hexadecimal key to use to encrypt the sensitive properties").build());
        options.addOption(Option.builder("e").longOpt(KEY_MIGRATION_ARG).hasArg(true).argName("keyhex").desc("The old raw hexadecimal key to use during key migration").build());
        options.addOption(Option.builder("H").longOpt(PROTECTION_SCHEME_MIGRATION_ARG).hasArg(true).argName("protectionScheme").desc("The old protection scheme to use during encryption migration (see --protectionScheme for possible values). Default is AES_GCM").build());
        options.addOption(Option.builder("p").longOpt(PASSWORD_ARG).hasArg(true).argName("password").desc("The password from which to derive the key to use to encrypt the sensitive properties").build());
        options.addOption(Option.builder("w").longOpt(PASSWORD_MIGRATION_ARG).hasArg(true).argName("password").desc("The old password from which to derive the key during migration").build());
        options.addOption(Option.builder("r").longOpt(USE_KEY_ARG).hasArg(false).desc("If provided, the secure console will prompt for the raw key value in hexadecimal form").build());
        options.addOption(Option.builder("m").longOpt(MIGRATION_ARG).hasArg(false).desc("If provided, the nifi.properties and/or login-identity-providers.xml sensitive properties will be re-encrypted with the new scheme").build());
        options.addOption(Option.builder("x").longOpt(DO_NOT_ENCRYPT_NIFI_PROPERTIES_ARG).hasArg(false).desc("If provided, the properties in flow.xml.gz will be re-encrypted with a new key but the nifi.properties and/or login-identity-providers.xml files will not be modified").build());
        options.addOption(Option.builder("s").longOpt(PROPS_KEY_ARG).hasArg(true).argName("password|keyhex").desc("The password or key to use to encrypt the sensitive processor properties in flow.xml.gz").build());
        options.addOption(Option.builder("A").longOpt(NEW_FLOW_ALGORITHM_ARG).hasArg(true).argName("algorithm").desc("The algorithm to use to encrypt the sensitive processor properties in flow.xml.gz").build());
        options.addOption(Option.builder("P").longOpt(NEW_FLOW_PROVIDER_ARG).hasArg(true).argName("algorithm").desc("The security provider to use to encrypt the sensitive processor properties in flow.xml.gz").build());
        options.addOption(Option.builder("c").longOpt(TRANSLATE_CLI_ARG).hasArg(false).desc("Translates the nifi.properties file to a format suitable for the NiFi CLI tool").build());
        return options;
    }

    static Options getCliOptions() {
        return staticOptions;
    }

    public NiFiMode() {
        header = "\n" + DEFAULT_DESCRIPTION + "\n\n";
        options = getCliOptions();
    }

    /**
     * Prints the usage message and available arguments for this tool (along with a specific error message if provided).
     *
     * @param errorMessage the optional error message
     */
    void printUsage(final String errorMessage) {
        if (!errorMessage.isEmpty()) {
            System.out.println(errorMessage);
            System.out.println();
        }
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.setWidth(160);
        helpFormatter.setOptionComparator(null);
        // preserve manual ordering of options when printing instead of alphabetical
        helpFormatter.printHelp(NiFiMode.class.getCanonicalName(), header, options, FOOTER, true);
    }

    protected void printUsageAndThrow(final String errorMessage, final ExitCode exitCode) throws CommandLineParseException {
        printUsage(errorMessage);
        throw new CommandLineParseException(errorMessage, exitCode);
    }

    // TODO: where is the bulk of this Class? Is it (incorrectly) in EncryptConfigTool.java??
    @Override
    public void run(final String[] args) {
        logger.info("Invoking NiFi Encrypt Configuration Tool");
//        EncryptConfigTool.main(args); // Nope.. moved that main class to this one

        try {
            CommandLine commandLine = parse(args);
            if (commandLine == null) {
                EncryptConfigMainJava.debugPrint("commandLine is null. Exit.");
                return;
            }
//        } catch (CommandLineParseException cpe) {
//            if (cpe.getExitCode() == ExitCode.HELP) {
//                EncryptConfigMainJava.debugPrint("command line parse exception; exit code = help");
//                System.exit(ExitCode.HELP.ordinal());
//            } else {
//                EncryptConfigMainJava.debugPrint("command line parse exception; exit code name = " + cpe.getExitCode().name());
//                System.exit(ExitCode.ERROR_PARSING_COMMAND_LINE.ordinal());
//            }
//        }
            isVerbose = commandLine.hasOption(VERBOSE_ARG);

//            Set<String> processingErrors = new HashSet<>();
            // Perform CLI translation first because, if enabled, it will be the only item processed
            translateCli(commandLine);

            // TODO: is this list complete?
            isBootstrapKeyRequired = commandLine.hasOption(NIFI_PROPERTIES_ARG)
                    || commandLine.hasOption(LOGIN_IDENTITY_PROVIDERS_ARG)
                    || commandLine.hasOption(AUTHORIZERS_ARG)
                    || commandLine.hasOption(PASSWORD_ARG);

            Map<String, String> bootstrapKey = updateBootstrap(commandLine);  // TODO: return map oldKey: value, newKey: value
            if (bootstrapKey.isEmpty() && isBootstrapKeyRequired(commandLine)) {

            }

            // TODO: do we want to return errors? Or print error/exit within the method. See ExitCode class for the breadth of error codes (i.e. case for exiting immediately)
            /*
            processingErrors.addAll(encryptNiFiProperties(commandLine));
            processingErrors.addAll(encryptFlow(commandLine)); // TODO: no need for additional command line props for JSON; look at filename (or parse) to determine format
            processingErrors.addAll(encryptLoginIdentityProviders(commandLine));
            processingErrors.addAll(encryptAuthorizers(commandLine));

             */

            // TODO: is it 'correct' to build up all the errors? Or quit as soon as at least one is found? What is proper behavior.. print messages for all?
            if (!processingErrors.isEmpty()) {
                System.out.println("\nError processing");
                for (String error : processingErrors) {
                    System.out.println("  " + error);
                }
                System.out.println();
                System.exit(ExitCode.ERROR_PARSING_COMMAND_LINE.ordinal());
            }

        } catch (CommandLineParseException cpe) {
        if (cpe.getExitCode() == ExitCode.HELP) {
            EncryptConfigMainJava.debugPrint("command line parse exception; exit code = help");
            System.exit(ExitCode.HELP.ordinal());
        } else {
            EncryptConfigMainJava.debugPrint("command line parse exception; exit code name = " + cpe.getExitCode().name());
            System.exit(ExitCode.ERROR_PARSING_COMMAND_LINE.ordinal());
        }
    }


        EncryptConfigMainJava.debugPrint("run finished normally");

    }

    private CommandLine parse(final String[] args) throws CommandLineParseException {
        // Parse command line
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(getCliOptions(), args);
        } catch (ParseException pe) {
            printUsageAndThrow("Error parsing command line. (" + pe.getMessage() + ")", ExitCode.ERROR_PARSING_COMMAND_LINE);
        }
        return commandLine;
    }

    private void translateCli(CommandLine commandLine) {
        Map<String, String> errors = new HashMap<>();
        if (commandLine.hasOption(TRANSLATE_CLI_ARG)) {
            EncryptConfigMainJava.debugPrint("Translate CLI - do the thing");
        }
    }
}
