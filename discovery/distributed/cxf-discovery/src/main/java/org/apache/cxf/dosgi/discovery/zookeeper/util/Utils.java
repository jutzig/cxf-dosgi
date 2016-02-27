/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.dosgi.discovery.zookeeper.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointListener;

public final class Utils {

    static final String PATH_PREFIX = "/osgi/service_registry";
    static final Pattern OBJECTCLASS_PATTERN = Pattern.compile(".*\\(objectClass=([^)]+)\\).*");

    private Utils() {
        // never constructed
    }

    public static String getZooKeeperPath(String name) {
        return name == null || name.isEmpty() ? PATH_PREFIX : PATH_PREFIX + '/' + name.replace('.', '/');
    }

    /**
     * Returns the value of a "string+" property as an array of strings.
     * <p>
     * A "string+" property can have a value which is either a string,
     * an array of strings, or a collection of strings.
     * <p>
     * If the given value is not of one of the valid types, or is null,
     * an empty array is returned.
     *
     * @param property a "string+" property value
     * @return the property value as an array of strings, or an empty array
     */
    public static String[] getStringPlusProperty(Object property) {
        if (property instanceof String) {
            return new String[] {(String)property};
        } else if (property instanceof String[]) {
            return (String[])property;
        } else if (property instanceof Collection) {
            try {
                @SuppressWarnings("unchecked")
                Collection<String> strings = (Collection<String>)property;
                return strings.toArray(new String[strings.size()]);
            } catch (ArrayStoreException ase) {
                // ignore collections with wrong type
            }
        }
        return new String[0];
    }

    /**
     * Removes nulls and empty strings from the given string array.
     *
     * @param strings an array of strings
     * @return a new array containing the non-null and non-empty
     *         elements of the original array in the same order
     */
    public static String[] removeEmpty(String[] strings) {
        String[] result = new String[strings.length];
        int copied = 0;
        for (String s : strings) {
            if (s != null && !s.isEmpty()) {
                result[copied++] = s;
            }
        }
        return copied == result.length ? result : Arrays.copyOf(result, copied);
    }

    public static String[] getScopes(ServiceReference sref) {
        return removeEmpty(getStringPlusProperty(sref.getProperty(EndpointListener.ENDPOINT_LISTENER_SCOPE)));
    }

    // copied from the DSW OSGiUtils class
    public static String getUUID(BundleContext bc) {
        synchronized ("org.osgi.framework.uuid") {
            String uuid = bc.getProperty("org.osgi.framework.uuid");
            if (uuid == null) {
                uuid = UUID.randomUUID().toString();
                System.setProperty("org.osgi.framework.uuid", uuid);
            }
            return uuid;
        }
    }

    /**
     * Puts the given key-value pair in the given dictionary if the key does not
     * already exist in it or if its existing value is null.
     *
     * @param dict a dictionary
     * @param key the key
     * @param value the default value to set
     */
    public static void setDefault(Dictionary<String, String> dict, String key, String value) {
        if (dict.get(key) == null) {
            dict.put(key, value);
        }
    }

    /**
     * Converts the given Dictionary to a Map.
     *
     * @param dict a dictionary
     * @param <K> the key type
     * @param <V> the value type
     * @return the converted map, or an empty map if the given dictionary is null
     */
    public static <K, V> Map<K, V> toMap(Dictionary<K, V> dict) {
        Map<K, V> map = new HashMap<K, V>();
        if (dict != null) {
            Enumeration<K> keys = dict.keys();
            while (keys.hasMoreElements()) {
                K key = keys.nextElement();
                map.put(key, dict.get(key));
            }
        }
        return map;
    }

    public static String getObjectClass(String scope) {
        Matcher m = OBJECTCLASS_PATTERN.matcher(scope);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * Returns a service's properties as a map.
     *
     * @param serviceReference a service reference
     * @return the service's properties as a map
     */
    public static Map<String, Object> getProperties(ServiceReference serviceReference) {
        String[] keys = serviceReference.getPropertyKeys();
        Map<String, Object> props = new HashMap<String, Object>(keys.length);
        for (String key : keys) {
            Object val = serviceReference.getProperty(key);
            props.put(key, val);
        }
        return props;
    }
}
