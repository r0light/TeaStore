package tools.descartes.teastore.auth.rest;

import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheManagerBuilder;

public class CachingHelper {

    private static CacheManager cacheManager = null;
    private static final Object lock = new Object();

    public static CacheManager getCacheManager() {
        synchronized (lock) {
            if (cacheManager != null) {
                return cacheManager;
            } else {
                cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
                return cacheManager;
            }
        }
    }
}
