package uz.koopin.mss.utils;

import org.yaml.snakeyaml.Yaml;
import uz.koopin.mss.VelocitySyncPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public final class ConfigurationUtil {

    private ConfigurationUtil() {
    }

    public static Map<String, Object> load(Path dataFolder, String fileName) throws IOException {
        Path file = dataFolder.resolve(fileName);

        if (Files.notExists(file)) {
            Files.createDirectories(dataFolder);
            try (InputStream defaults = VelocitySyncPlugin.class.getResourceAsStream("/" + fileName)) {
                if (defaults != null) {
                    Files.copy(defaults, file);
                } else {
                    Files.createFile(file);
                }
            }
        }

        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> root = new Yaml().load(in);
            return root != null ? root : Collections.emptyMap();
        }
    }
}
