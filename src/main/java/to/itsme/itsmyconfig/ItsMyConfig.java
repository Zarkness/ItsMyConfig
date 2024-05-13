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

    public void loadConfig() {
        progressBarBucket.clearAllProgressBars();
        this.saveDefaultConfig();
        this.reloadConfig();
        this.loadSymbolPrefix();
        this.loadPlaceholders();
        this.loadProgressBars();

        loadCustomYamlFiles(this.getDataFolder());
    }

    private void loadSymbolPrefix() {
        this.symbolPrefix = this.getConfig().getString("symbol-prefix");
    }

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

    private void loadCustomYamlFiles(File directory) {
        Collection<File> files = FileUtils.listFiles(directory, new String[]{"yml"}, true);
        for (File file : files) {
            processCustomYamlFile(file);
        }
    }

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

    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    public String getSymbolPrefix() {
        return symbolPrefix;
    }

    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    public RequirementManager getRequirementManager() {
        return requirementManager;
    }
}
