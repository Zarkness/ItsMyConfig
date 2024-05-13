package to.itsme.itsmyconfig;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.apache.commons.io.FileUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import to.itsme.itsmyconfig.command.CommandManager;
import to.itsme.itsmyconfig.listener.impl.PacketChatListener;
import to.itsme.itsmyconfig.listener.impl.PacketItemListener;
import to.itsme.itsmyconfig.placeholder.DynamicPlaceHolder;
import to.itsme.itsmyconfig.placeholder.PlaceholderData;
import to.itsme.itsmyconfig.placeholder.PlaceholderManager;
import to.itsme.itsmyconfig.placeholder.PlaceholderType;
import to.itsme.itsmyconfig.placeholder.type.AnimatedPlaceholderData;
import to.itsme.itsmyconfig.placeholder.type.ColorPlaceholderData;
import to.itsme.itsmyconfig.placeholder.type.RandomPlaceholderData;
import to.itsme.itsmyconfig.placeholder.type.StringPlaceholderData;
import to.itsme.itsmyconfig.progress.ProgressBar;
import to.itsme.itsmyconfig.progress.ProgressBarBucket;
import to.itsme.itsmyconfig.requirement.RequirementManager;

import java.io.File;
import java.util.Collection;

/**
 * ItsMyConfig class represents the main configuration class for the plugin.
 * It extends the JavaPlugin class and provides methods to manage the plugin configuration.
 * It also holds instances of PlaceholderManager, ProgressBarBucket, RequirementManager, and BukkitAudiences.
 */
public final class ItsMyConfig extends JavaPlugin {

    private static final boolean ALLOW_ITEM_EDITS = false;

    private static ItsMyConfig instance;
    private final PlaceholderManager placeholderManager = new PlaceholderManager();
    private final ProgressBarBucket progressBarBucket = new ProgressBarBucket();
    private String symbolPrefix;
    private RequirementManager requirementManager;

    private BukkitAudiences adventure;

    public static ItsMyConfig getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        new DynamicPlaceHolder(this, progressBarBucket).register();
        new CommandManager(this);

        this.requirementManager = new RequirementManager();
        this.adventure = BukkitAudiences.create(this);

        loadConfig();

        if (getConfig().getBoolean("bstats", true)) {
            new Metrics(this, 21713);
        }

        final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketChatListener(this));

        if (ALLOW_ITEM_EDITS) {
            protocolManager.addPacketListener(new PacketItemListener(this));
        }
    }

    /**
     * The loadConfig method is responsible for loading the configuration file and initializing various settings and data.
     * It performs the following steps:
     *
     * 1. Clears all progress bars in the ProgressBarBucket.
     * 2. Saves the default configuration file if it does not exist.
     * 3. Reloads the configuration file.
     * 4. Loads the symbol prefix from the configuration.
     * 5. Loads the custom placeholders from the configuration and registers them.
     * 6. Loads the custom progress bars from the configuration and registers them.
     * 7. Loads custom YAML files from the plugin's data folder.
     */
    public void loadConfig() {

        progressBarBucket.clearAllProgressBars();
        this.saveDefaultConfig();
        this.reloadConfig();
        this.loadSymbolPrefix();
        this.loadPlaceholders();
        this.loadProgressBars();

        loadCustomYamlFiles(this.getDataFolder());
    }

    /**
     * Loads the symbol prefix from the configuration.
     */
    private void loadSymbolPrefix() {
        this.symbolPrefix = this.getConfig().getString("symbol-prefix");
    }

    /**
     * Loads the placeholders from the configuration file and registers them with the placeholder manager.
     * This method iterates over the placeholders configuration section, retrieves the placeholder data,
     * registers any associated requirements, and finally registers the placeholder with the placeholder manager.
     */
    private void loadPlaceholders() {
        placeholderManager.unregisterAll();
        final ConfigurationSection placeholdersConfigSection =
                this.getConfig().getConfigurationSection("custom-placeholder");
        if (placeholdersConfigSection != null) {
            for (final String identifier : placeholdersConfigSection.getKeys(false)) {
                PlaceholderData data = getPlaceholderData(placeholdersConfigSection, identifier, "config.yml");
                if (data != null) {
                    placeholderManager.register(identifier, data);
                    this.getLogger().info(String.format("Registered placeholder %s from config.yml", identifier));
                } else {
                    this.getLogger().warning(String.format("Failed to register placeholder %s from config.yml", identifier));
                }
            }
        }
    }

    /**
     * Retrieves the placeholder data based on the provided configuration section and identifier.
     *
     * @param placeholdersConfigSection The configuration section containing the placeholder data.
     * @param identifier                The identifier of the placeholder.
     * @param yamlFileName              The name of the YAML file containing the placeholder.
     * @return The placeholder data object.
     */
    private PlaceholderData getPlaceholderData(ConfigurationSection placeholdersConfigSection, String identifier, String yamlFileName) {
        if (placeholdersConfigSection == null) return null;

        final String placeholderTypeProperty = identifier + ".type";
        final PlaceholderType type = PlaceholderType.find(placeholdersConfigSection.getString(placeholderTypeProperty));
        if (type == null) return null;

        final String valuesProperty = identifier + ".values";
        final String valueProperty = identifier + ".value";

        switch (type) {
            case RANDOM:
                return new RandomPlaceholderData(placeholdersConfigSection.getStringList(valuesProperty));
            case ANIMATION:
                final int intervalPropertyDefaultValue = 20;
                return new AnimatedPlaceholderData(placeholdersConfigSection.getStringList(valuesProperty),
                        placeholdersConfigSection.getInt(identifier + ".interval", intervalPropertyDefaultValue));
            case COLOR:
                return new ColorPlaceholderData(placeholdersConfigSection.getConfigurationSection(identifier));
            default:
            case STRING:
                final String defaultValue = "";
                return new StringPlaceholderData(placeholdersConfigSection.getString(valueProperty, defaultValue));
        }
    }

    /**
     * Loads progress bars from the configuration file.
     * Each progress bar is registered in the ProgressBarBucket.
     */
    private void loadProgressBars() {
        final ConfigurationSection progressBarConfigSection =
                this.getConfig().getConfigurationSection("custom-progress");
        if (progressBarConfigSection != null) {
            for (final String identifier : progressBarConfigSection.getKeys(false)) {
                final ConfigurationSection configurationSection =
                        progressBarConfigSection
                                .getConfigurationSection(identifier);
                if (configurationSection != null) {
                    ProgressBar progressBar = getProgressBarFromYaml(identifier, configurationSection, "config.yml");
                    if (progressBar != null) {
                        progressBarBucket.registerProgressBar(progressBar);
                        this.getLogger().info(String.format("Registered custom progress bar %s from config.yml", identifier));
                    } else {
                        this.getLogger().warning(String.format("Failed to register custom progress bar %s from config.yml", identifier));
                    }
                }
            }
        }
    }

    /**
     * Retrieves a progress bar from a YAML configuration section.
     *
     * @param identifier   The identifier of the progress bar.
     * @param section      The YAML configuration section.
     * @param yamlFileName The name of the YAML file containing the progress bar.
     * @return The progress bar object.
     */
    private ProgressBar getProgressBarFromYaml(String identifier, ConfigurationSection section, String yamlFileName) {
        if (section == null) return null;

        String symbol = section.getString("symbol");
        String completedColor = section.getString("completed-color");
        String progressColor = section.getString("progress-color");
        String remainingColor = section.getString("remaining-color");

        if (symbol == null || completedColor == null || progressColor == null || remainingColor == null) {
            return null;
        }

        return new ProgressBar(
                identifier,
                symbol,
                completedColor,
                progressColor,
                remainingColor
        );
    }

    /**
     * Loads custom YAML files from the plugin's data folder.
     *
     * @param directory The directory containing the YAML files.
     */
    private void loadCustomYamlFiles(File directory) {
        Collection<File> files = FileUtils.listFiles(directory, new String[]{"yml"}, true);
        for (File file : files) {
            processCustomYamlFile(file);
        }
    }

    /**
     * Processes a custom YAML file, registering its placeholders and progress bars.
     *
     * @param file The YAML file to process.
     */
    private void processCustomYamlFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection placeholdersSection = yaml.getConfigurationSection("custom-placeholder");
        ConfigurationSection progressBarsSection = yaml.getConfigurationSection("custom-progress");

        if (placeholdersSection != null) {
            for (String identifier : placeholdersSection.getKeys(false)) {
                ConfigurationSection itemSection = placeholdersSection.getConfigurationSection(identifier);
                registerPlaceholder(identifier, itemSection, file.getName());
            }
        }

        if (progressBarsSection != null) {
            for (String identifier : progressBarsSection.getKeys(false)) {
                ConfigurationSection itemSection = progressBarsSection.getConfigurationSection(identifier);
                registerProgressBar(identifier, itemSection, file.getName());
            }
        }
    }

    /**
     * Registers a placeholder from a YAML configuration section.
     *
     * @param identifier   The identifier of the placeholder.
     * @param section      The YAML configuration section.
     * @param yamlFileName The name of the YAML file containing the placeholder.
     */
    private void registerPlaceholder(String identifier, ConfigurationSection section, String yamlFileName) {
        PlaceholderData data = getPlaceholderDataFromYaml(section);
        if (data != null) {
            placeholderManager.register(identifier, data);
            if (!yamlFileName.equals("config.yml")) {
                this.getLogger().info(String.format("Registered placeholder %s from %s", identifier, yamlFileName));
            }
        } else {
            this.getLogger().warning(String.format("Failed to register placeholder %s from %s", identifier, yamlFileName));
        }
    }

    /**
     * Retrieves placeholder data from a YAML configuration section.
     *
     * @param section The YAML configuration section.
     * @return The placeholder data object.
     */
    private PlaceholderData getPlaceholderDataFromYaml(ConfigurationSection section) {
        if (section == null) return null;

        String type = section.getString("type");
        PlaceholderType placeholderType = PlaceholderType.find(type);
        if (placeholderType == null) return null;

        switch (placeholderType) {
            case RANDOM:
                return new RandomPlaceholderData(section.getStringList("values"));
            case ANIMATION:
                int interval = section.getInt("interval", 20);
                return new AnimatedPlaceholderData(section.getStringList("values"), interval);
            case COLOR:
                return new ColorPlaceholderData(section);
            default:
            case STRING:
                return new StringPlaceholderData(section.getString("value", ""));
        }
    }

    /**
     * Registers a progress bar from a YAML configuration section.
     *
     * @param identifier   The identifier of the progress bar.
     * @param section      The YAML configuration section.
     * @param yamlFileName The name of the YAML file containing the progress bar.
     */
    private void registerProgressBar(String identifier, ConfigurationSection section, String yamlFileName) {
        ProgressBar progressBar = getProgressBarFromYaml(identifier, section, yamlFileName);
        if (progressBar != null) {
            progressBarBucket.registerProgressBar(progressBar);
            if (!yamlFileName.equals("config.yml")) {
                this.getLogger().info(String.format("Registered progress bar %s from %s", identifier, yamlFileName));
            }
        } else {
            this.getLogger().warning(String.format("Failed to register progress bar %s from %s", identifier, yamlFileName));
        }
    }

    /**
     * Retrieves the instance of the `BukkitAudiences` class used for sending chat messages and titles.
     *
     * @return The instance of the `BukkitAudiences` class.
     * @throws IllegalStateException if the plugin is disabled and the `Adventure` instance is accessed.
     */
    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    /**
     * Retrieves the symbol prefix.
     *
     * @return The symbol prefix used in messages or text.
     */
    public String getSymbolPrefix() {
        return symbolPrefix;
    }

    /**
     * Retrieves the PlaceholderManager instance.
     *
     * @return The PlaceholderManager instance.
     */
    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    /**
     * Returns the RequirementManager object. The RequirementManager class is responsible for managing requirements
     * and validating them.
     *
     * @return the RequirementManager object
     */
    public RequirementManager getRequirementManager() {
        return requirementManager;
    }
}
