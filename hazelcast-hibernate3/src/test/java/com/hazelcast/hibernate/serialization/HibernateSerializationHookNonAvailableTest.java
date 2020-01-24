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

package com.hazelcast.hibernate.serialization;

import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.internal.util.FilteringClassLoader;
import org.hibernate.cache.CacheKey;
import org.hibernate.cache.entry.CacheEntry;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertFalse;

@RunWith(HazelcastSerialClassRunner.class)
@Category({QuickTest.class})
public class HibernateSerializationHookNonAvailableTest {

    private static final Field ORIGINAL;
    private static final Field TYPE_MAP;
    private static final Method GET_SERIALIZATION_SERVICE;

    private static final ClassLoader FILTERING_CLASS_LOADER;

    static {
        try {
            List<String> excludes = Collections.singletonList("org.hibernate");
            FILTERING_CLASS_LOADER = new FilteringClassLoader(excludes, "com.hazelcast");

            String hazelcastInstanceImplClassName = "com.hazelcast.instance.impl.HazelcastInstanceImpl";
            Class<?> hazelcastInstanceImplClass = FILTERING_CLASS_LOADER.loadClass(hazelcastInstanceImplClassName);
            GET_SERIALIZATION_SERVICE = hazelcastInstanceImplClass.getMethod("getSerializationService");

            String hazelcastInstanceProxyClassName = "com.hazelcast.instance.impl.HazelcastInstanceProxy";
            Class<?> hazelcastInstanceProxyClass = FILTERING_CLASS_LOADER.loadClass(hazelcastInstanceProxyClassName);
            ORIGINAL = hazelcastInstanceProxyClass.getDeclaredField("original");
            ORIGINAL.setAccessible(true);

            String serializationServiceImplClassName = "com.hazelcast.internal.serialization.impl.AbstractSerializationService";
            Class<?> serializationServiceImplClass = FILTERING_CLASS_LOADER.loadClass(serializationServiceImplClassName);
            TYPE_MAP = serializationServiceImplClass.getDeclaredField("typeMap");
            TYPE_MAP.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testAutoregistrationOnHibernate3NonAvailable()
            throws Exception {

        Thread thread = Thread.currentThread();
        ClassLoader tccl = thread.getContextClassLoader();

        Object config = null;
        Method setClassLoader = null;
        try {
            thread.setContextClassLoader(FILTERING_CLASS_LOADER);

            Class<?> configClazz = FILTERING_CLASS_LOADER.loadClass("com.hazelcast.config.Config");
            config = configClazz.newInstance();
            setClassLoader = configClazz.getDeclaredMethod("setClassLoader", ClassLoader.class);

            setClassLoader.invoke(config, FILTERING_CLASS_LOADER);

            Class<?> hazelcastClazz = FILTERING_CLASS_LOADER.loadClass("com.hazelcast.core.Hazelcast");
            Method newHazelcastInstance = hazelcastClazz.getDeclaredMethod("newHazelcastInstance", configClazz);

            Object hz = newHazelcastInstance.invoke(hazelcastClazz, config);
            Object impl = ORIGINAL.get(hz);
            Object serializationService = GET_SERIALIZATION_SERVICE.invoke(impl);
            //noinspection unchecked
            ConcurrentMap<Class, ?> typeMap = (ConcurrentMap<Class, ?>) TYPE_MAP.get(serializationService);
            boolean cacheKeySerializerFound = false;
            boolean cacheEntrySerializerFound = false;
            for (Class clazz : typeMap.keySet()) {
                if (clazz == CacheKey.class) {
                    cacheKeySerializerFound = true;
                } else if (clazz == CacheEntry.class) {
                    cacheEntrySerializerFound = true;
                }
            }

            hazelcastClazz.getDeclaredMethod("shutdownAll").invoke(impl);

            assertFalse("CacheKey serializer found", cacheKeySerializerFound);
            assertFalse("CacheEntry serializer found", cacheEntrySerializerFound);
        } finally {
            if (config != null && setClassLoader != null) {
                setClassLoader.invoke(config, tccl);
            }

            thread.setContextClassLoader(tccl);
        }
    }
}
