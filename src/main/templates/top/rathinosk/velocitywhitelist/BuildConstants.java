package top.rathinosk.velocitywhitelist;

/**
 * BuildConstants class stores constants that are replaced before compilation.
 * These constants provide information about the plugin, such as its ID, name, version, URL, description, authors, and build date.
 */
public class BuildConstants {

    public static final String ID = "${id}";
    public static final String NAME = "${name}";
    public static final String VERSION = "${version}";
    public static final String URL = "${link}";
    public static final String DESCRIPTION = "${description}";
    public static final String AUTHORS = "${authors}";
    public static final String BUILD_DATE = "${buildDate}";
}
