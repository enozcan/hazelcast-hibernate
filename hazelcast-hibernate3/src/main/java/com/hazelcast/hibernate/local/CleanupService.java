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

package com.hazelcast.hibernate.local;


import com.hazelcast.instance.OutOfMemoryErrorDispatcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * An internal service to clean cache regions
 */
public final class CleanupService {

    private static final long FIXED_DELAY = 60;
    private static final long FIXED_DELAY1 = 60;

    private final String name;
    private final ScheduledExecutorService executor;

    public CleanupService(final String name) {
        this.name = name;
        executor = Executors.newSingleThreadScheduledExecutor(new CleanupThreadFactory());
    }

    public void registerCache(final LocalRegionCache cache) {
        executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                cache.cleanup();
            }
        }, FIXED_DELAY, FIXED_DELAY1, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    /**
     * Internal ThreadFactory to create cleanup threads
     */
    private class CleanupThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(final Runnable r) {
            final Thread thread = new CleanupThread(r, name + ".hibernate.cleanup");
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * Runnable thread adapter to capture exceptions and notify Hazelcast about them
     */
    private static final class CleanupThread extends Thread {

        private CleanupThread(final Runnable target, final String name) {
            super(target, name);
        }

        public void run() {
            try {
                super.run();
            } catch (OutOfMemoryError e) {
                OutOfMemoryErrorDispatcher.onOutOfMemory(e);
            }
        }
    }
}
