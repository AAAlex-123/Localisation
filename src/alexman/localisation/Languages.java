package alexman.localisation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import requirement.requirements.ListRequirement;
import requirement.util.Requirements;

/**
 * Wrapper for a {@code ResourceBundle} that additionally provides a way to
 * alter the Locale an Application uses.
 * <p>
 * This change is facilitated by the use of Requirements and ListRequirement
 * objects. To avoid Since all of the Strings are loaded when the Application
 * starts, changing the Locale will take effect the next time the Application is
 * launched.
 *
 * @see Requirements
 * @see ListRequirement
 *
 * @author Alex Mandelias
 */
public final class Languages {

	private static final String  LANGUAGE_LITERAL = "Language";
	private static final String  COUNTRY_LITERAL  = "Country";
	private static final String  VARIANT_LITERAL  = "Variant";
	private static final Pattern pattern;

	private static final String BUNDLE_NAME = "alexman.localisation.language";

	static {
		final String part     = "(?<%s>[a-zA-Z]{2})";
		final String language_part = String.format(part, Languages.LANGUAGE_LITERAL);
		final String country_part  = String.format(part, Languages.COUNTRY_LITERAL);
		final String variant_part  = String.format(part, Languages.VARIANT_LITERAL);

		final String regex = String.format("^language(?:_%s(?:_%s(?:_%s)?)?)", language_part,
		        country_part, variant_part);
		pattern = Pattern.compile(regex);
	}

	/** Singleton pattern, this INSTANCE is used in every static method */
	private static Languages INSTANCE;

	/**
	 * Private getter for the INSTANCE, throws a Runtime Exception if it has not
	 * been configured prior to this method call.
	 *
	 * @return the {@link Languages#INSTANCE}
	 *
	 * @throws RuntimeException if the INSTANCE has not been configured prior to
	 *                          this method call
	 */
	private static Languages getInstance() {
		if (INSTANCE == null)
			throw new RuntimeException(
			        "The Languages class has not been configured. Make sure to call Languages.config before attempting to use methods of this class.");
		return INSTANCE;
	}

	/* Fields of the INSTANCE */

	private final String         configFile, languagesDirectory;
	private final ResourceBundle RESOURCE_BUNDLE;
	private final Properties     properties;
	private final Locale         currentLocale;

	/**
	 * Allow instantiation only through the Languages.config static method.
	 *
	 * @param configFile   the path of the configuration file
	 * @param languagesDir the directory with all the language.properties files
	 *
	 * @see Languages#configureUnsafe(String, String)
	 */
	private Languages(String configFile, String languagesDir) {

		final String correctLangDirEnding = Languages.BUNDLE_NAME
		        .replaceFirst("\\.[^\\.]+$", "")
		        .replaceAll("\\.", File.separator.replace("\\", "\\\\"));
		if (!languagesDir.endsWith(correctLangDirEnding)) {
			throw new RuntimeException(
			        String.format("Error: Invalid languages directory. It must end with '%s'"));
		}

		this.configFile = configFile;
		this.languagesDirectory = languagesDir;
		this.properties = new OrderedProperties();

		boolean success = false;
		try (BufferedReader reader = new BufferedReader(new FileReader(this.configFile))) {
			this.properties.load(reader);
			success = true;
		} catch (final FileNotFoundException e) {
			System.err.printf(
			        "Warning: Couldn't load language config file: '%'. Falling back to default settings.",
			        this.configFile);
		} catch (final IOException e) {
			System.err.printf(
			        "Warning: Couldn't read from language config file: '%'. Falling back to default settings.",
			        this.configFile);
		}

		if (!success) {
			this.properties.setProperty(LANGUAGE_LITERAL, "");
			this.properties.setProperty(COUNTRY_LITERAL, "");
			this.properties.setProperty(VARIANT_LITERAL, "");
		}

		this.currentLocale = Languages.constructLocaleFromProperties(this.properties);
		this.RESOURCE_BUNDLE = ResourceBundle.getBundle(Languages.BUNDLE_NAME, this.currentLocale);
	}

	/**
	 * Returns the name of the Resource Bundle used in this class.
	 *
	 * @return the name of the Resource Bundle
	 */
	public static String getBundleName() {
		return Languages.BUNDLE_NAME;
	}

	/**
	 * Configures the Languages class with new parameters. This method must only be
	 * called once before any other method of this class is called. Failing to do so
	 * will result in a Runtime Exception, as will calling it a second time.
	 *
	 * @param configFile   the path of the configuration file
	 * @param languagesDir the directory with all the language.properties files. At
	 *                     runtime, the classpath must contain this directory and it
	 *                     its path must end with whatever directory is specified by
	 *                     the {@link Languages#getBundleName()}, that is the bundle
	 *                     name but with separator characters instead of dots
	 *                     excluding '.language'.
	 */
	public static void configureUnsafe(String configFile, String languagesDir) {
		INSTANCE = new Languages(configFile, languagesDir);
	}

	/**
	 * Same as {@link Languages#configureUnsafe(String, String)} but does not allow
	 * the Languages class to be configured more than once, since it may potentially
	 * cause a number of strings to not be updated with the new .properties files
	 * found in the new language directory.
	 * <p>
	 * Configures the Languages class with new parameters. This method must only be
	 * called once before any other method of this class is called. Failing to do so
	 * will result in a Runtime Exception, as will calling it a second time.
	 *
	 * @param configFile   the path of the configuration file
	 * @param languagesDir the directory with all the language.properties files. At
	 *                     runtime, the classpath must contain this directory and it
	 *                     its path must end with whatever directory is specified by
	 *                     the {@link Languages#getBundleName()}, that is the bundle
	 *                     name but with separator characters instead of dots
	 *                     excluding '.language'.
	 *
	 * @throws RuntimeException if the Languages class has already been configured
	 *                          before
	 */
	public static void configure(String configFile, String languagesDir) {
		if (INSTANCE != null)
			throw new RuntimeException(
			        "The Languages class has already been configured. Make sure to only call Languages.configure once");

		Languages.configureUnsafe(configFile, languagesDir);
	}

	/**
	 * Returns the path of the file containing configuration information. This path
	 * was specified when the {@link Languages#configureUnsafe(String, String)}
	 * method was called.
	 *
	 * @return the path of the config file
	 *
	 * @throws RuntimeException if the Languages class has not been configured prior
	 *                          to this method call
	 */
	public static String getConfigFile() {
		return getInstance().configFile;
	}

	/**
	 * Returns the directory containing the .properties language files. This
	 * directory was specified when the
	 * {@link Languages#configureUnsafe(String, String)} method was called.
	 *
	 * @return the directory with the .properties files
	 *
	 * @throws RuntimeException if the Languages class has not been configured prior
	 *                          to this method call
	 */
	public static String getLanguagesDirectory() {
		return getInstance().languagesDirectory;
	}

	/**
	 * Returns the String associated with the {@code key} in the ResourceBundle.
	 *
	 * @param key the key
	 *
	 * @return the String associated with the key
	 *
	 * @throws RuntimeException if the Languages class has not been configured prior
	 *                          to this method call
	 */
	public static String getString(String key) {
		try {
			return Languages.getInstance().RESOURCE_BUNDLE.getString(key);
		} catch (final MissingResourceException e) {
			return '!' + key + '!';
		}
	}

	/**
	 * Searches the languages directory, to find {@code language.properties} files.
	 * They are returned inside a Requirements object, which the caller may use to
	 * specify a Language from the ones available. This Requirements object can then
	 * be used with the {@link Languages#updateLanguage(Requirements)} method to
	 * change the selected language.
	 *
	 * @return a Requirements object containing a single ListRequirement with key
	 *         'Language' with the available Locales as options, or {@code null} if
	 *         no language.properties files were found
	 *
	 * @throws RuntimeException if the Languages class has not been configured prior
	 *                          to this method call
	 */
	public static Requirements getLanguageRequirements() {

		final File         directory = new File(Languages.getLanguagesDirectory());
		final List<Locale> locales   = new LinkedList<>();

		File[] files = directory.listFiles();
		if (files == null) {
			System.err.printf("Warning: Path '%s' does not correspond to a directory", directory);
			return null;
		}

		for (File file : files) {

			final String fileName = file.getName();

			if (Languages.isLanguageFile(fileName)) {

				final Matcher m = Languages.pattern.matcher(fileName);
				if (!m.find())
					System.err.printf("Warning: Invalid language properties file name: '%s'",
					        fileName);

				final Function<String, String> f = (s -> s == null ? "" : s);

				final String language = f.apply(m.group(Languages.LANGUAGE_LITERAL));
				final String country  = f.apply(m.group(Languages.COUNTRY_LITERAL));
				final String variant  = f.apply(m.group(Languages.VARIANT_LITERAL));
				locales.add(new Locale(language, country, variant));
			}
		}

		if (locales.isEmpty())
			return null;

		final Requirements reqs = new Requirements();
		reqs.add("Language", locales);
		return reqs;
	}

	/**
	 * Attempts to update the selected language. A Locale is retrieved from the
	 * Requirements object and if it exists and is different than the current
	 * Locale, the selected Language is updated by updating the config file.
	 *
	 * @param reqs the Requirements object that will be used to retrieve the
	 *             selected Locale
	 *
	 * @return {@code true} if the Locale was changed as a result of this method
	 *         call, {@code false} otherwise
	 *
	 * @throws IOException      if an IO Exception occurrs while writing the new
	 *                          Locale to the file
	 * @throws RuntimeException if the Languages class has not been configured prior
	 *                          to this method call
	 */
	public static boolean updateLanguage(Requirements reqs) throws IOException {

		if (!reqs.fulfilled())
			return false;

		final Locale    chosen   = reqs.getValue("Language", Locale.class);
		final Languages instance = Languages.getInstance();

		if ((chosen == null) || chosen.equals(instance.currentLocale))
			return false;

		final Properties p = instance.properties;

		p.setProperty(Languages.LANGUAGE_LITERAL, chosen.getLanguage());
		p.setProperty(Languages.COUNTRY_LITERAL, chosen.getCountry());
		p.setProperty(Languages.VARIANT_LITERAL, chosen.getVariant());

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(instance.configFile))) {
			p.store(writer, null);
		}

		return true;
	}

	private static Locale constructLocaleFromProperties(Properties p) {
		return new Locale(p.getProperty(Languages.LANGUAGE_LITERAL),
		        p.getProperty(Languages.COUNTRY_LITERAL), p.getProperty(Languages.VARIANT_LITERAL));
	}

	private static boolean isLanguageFile(String filename) {
		return filename.startsWith("language_") && filename.endsWith(".properties");
	}
}
