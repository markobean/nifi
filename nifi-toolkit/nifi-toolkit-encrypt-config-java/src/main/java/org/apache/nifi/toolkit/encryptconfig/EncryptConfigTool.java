package org.apache.nifi.toolkit.encryptconfig;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.util.List;

public class EncryptConfigTool {

    private static final Logger logger = LoggerFactory.getLogger(EncryptConfigTool.class);

    private static final String DEFAULT_DESCRIPTION = "This tool reads from a nifi.properties and/or " +
            "login-identity-providers.xml file with plain sensitive configuration values, " +
            "prompts the user for a root key, and encrypts each value. It will replace the " +
            "plain value with the protected value in the same file (or write to a new file if " +
            "specified). It can also be used to migrate already-encrypted values in those " +
            "files or in flow.xml.gz to be encrypted with a new key.";

    private static final String HELP_ARG = "help";
    private static final String VERBOSE_ARG = "verbose";
    //    private static final String BOOTSTRAP_CONF_ARG = "bootstrapConf";
    private static final String NIFI_PROPERTIES_ARG = "niFiProperties";

    // Static holder to avoid re-generating the options object multiple times in an invocation
    private static final Options staticOptions = buildOptions();

    private final String header;
    private final Options options;

    static Options buildOptions() {
        Options options = new Options();
        options.addOption(Option.builder("h").longOpt(HELP_ARG).hasArg(false).desc("Show usage information (this message)").build());
        options.addOption(Option.builder("v").longOpt(VERBOSE_ARG).hasArg(false).desc("Sets verbose mode (default false)").build());
        options.addOption(Option.builder("n").longOpt(NIFI_PROPERTIES_ARG).hasArg(true).argName("file").desc("The nifi.properties file containing unprotected config values (will be overwritten unless -o is specified)").build());
        // TODO: add more options
        return options;
    }

    static Options getCliOptions() {
        // TODO: keep this in an 'if', or simply define the static options in a static{} block?
//        if (staticOptions.getOptions().isEmpty()) {
//            staticOptions = buildOptions();
//        }
        return staticOptions;
    }

    /**
     * Runs main tool logic (parsing arguments, reading files, protecting properties, and writing key and properties out to destination files).
     *
     * @param args the command-line arguments
     */
    static void main(List<String> args) {
        Security.addProvider(new BouncyCastleProvider());

        EncryptConfigTool tool = new EncryptConfigTool();
    }

    EncryptConfigTool() {
        this(DEFAULT_DESCRIPTION);
    }

    EncryptConfigTool(String description) {
        header = "\n" + description + "\n\n";
        options = getCliOptions();
    }
}
