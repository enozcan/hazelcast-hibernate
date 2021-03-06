/*
 * Copyright 2020 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.hazelcast.hibernate;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.instance.HazelcastInstanceFactory;
import com.hazelcast.hibernate.instance.IHazelcastInstanceLoader;
import com.hazelcast.hibernate.local.CleanupService;
import com.hazelcast.hibernate.region.HazelcastQueryResultsRegion;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.QueryResultsRegion;
import org.hibernate.cache.RegionFactory;
import org.hibernate.cache.access.AccessType;
import org.hibernate.cfg.Settings;

import java.util.Properties;

/**
 * Abstract superclass of Hazelcast based {@link org.hibernate.cache.RegionFactory} implementations
 */
public abstract class AbstractHazelcastCacheRegionFactory implements RegionFactory {

    protected HazelcastInstance instance;
    protected CleanupService cleanupService;
    private final ILogger logger = Logger.getLogger(getClass());

    private IHazelcastInstanceLoader instanceLoader;

    public AbstractHazelcastCacheRegionFactory() {
    }

    public AbstractHazelcastCacheRegionFactory(final Properties properties) {
        this();
    }

    public AbstractHazelcastCacheRegionFactory(final HazelcastInstance instance) {
        this.instance = instance;
    }

    @Override
    public final QueryResultsRegion buildQueryResultsRegion(final String regionName, final Properties properties)
            throws CacheException {
        HazelcastQueryResultsRegion region = new HazelcastQueryResultsRegion(instance, regionName, properties);
        cleanupService.registerCache(region.getCache());
        return region;
    }

    /**
     * @return true - for a large cluster, unnecessary puts will most likely slow things down.
     */
    @Override
    public boolean isMinimalPutsEnabledByDefault() {
        return true;
    }

    @Override
    public long nextTimestamp() {
        return HazelcastTimestamper.nextTimestamp(instance);
    }

    @Override
    public void start(final Settings settings, final Properties properties) throws CacheException {
        logger.info("Starting up " + getClass().getSimpleName());
        if (instance == null || !instance.getLifecycleService().isRunning()) {
            instanceLoader = HazelcastInstanceFactory.createInstanceLoader(properties);
            instance = instanceLoader.loadInstance();
        }
        cleanupService = new CleanupService(instance.getName());
    }

    @Override
    public void stop() {
        if (instanceLoader != null) {
            logger.info("Shutting down " + getClass().getSimpleName());
            instanceLoader.unloadInstance();
            instance = null;
            instanceLoader = null;
        }
        cleanupService.stop();
    }

    public HazelcastInstance getHazelcastInstance() {
        return instance;
    }

    @Override
    public AccessType getDefaultAccessType() {
        return AccessType.READ_WRITE;
    }
}
