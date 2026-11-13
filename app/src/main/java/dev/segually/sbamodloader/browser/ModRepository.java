package dev.segually.sbamodloader.browser;

import java.io.IOException;
import java.util.List;

/**
 * Where the mod list comes from. The real implementation will fetch a JSON repository on every load; until
 * it exists, {@link MockModRepository} serves sample data.
 */
public interface ModRepository {
    List<BrowserModels.Entry> load() throws IOException;
}
