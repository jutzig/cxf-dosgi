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
package org.apache.cxf.dosgi.dsw.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClassUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ClassUtils.class);

    private ClassUtils() {
    }

    public static Class<?> getInterfaceClass(Object service, String interfaceName) {
        return getInterfaceClass(service.getClass(), interfaceName);
    }

    private static Class<?> getInterfaceClass(Class<?> serviceClass, String interfaceName) {
        for (Class<?> iClass : serviceClass.getInterfaces()) {
            if (iClass.getName().equals(interfaceName)) {
                return iClass;
            }
            Class<?> intf = getInterfaceClass(iClass, interfaceName);
            if (intf != null) {
                return intf;
            }
        }

        if (serviceClass.getName().equals(interfaceName)) {
            return serviceClass;
        }

        Class<?> interfaceOnProxiedClass = getInterfaceClassOnSuperClasses(serviceClass, interfaceName);
        if (interfaceOnProxiedClass != null) {
            return interfaceOnProxiedClass;
        }

        return null;
    }

    /**
     * This method tries to deal specifically with classes that might have been proxied
     * eg. CGLIB proxies of which there might be a chain of proxies as different osgi frameworks
     * might be proxying the original service class that has been registered and then proxying the proxy.
     *
     * @param serviceClass
     * @param interfaceName
     * @return
     */
    private static Class<?> getInterfaceClassOnSuperClasses(Class<?> serviceClass, String interfaceName) {
        Class<?> superClass = serviceClass.getSuperclass();
        if (superClass != null) {
            for (Class<?> iClass : superClass.getInterfaces()) {
                if (iClass.getName().equals(interfaceName)) {
                    return iClass;
                }
                Class<?> intf = getInterfaceClass(iClass, interfaceName);
                if (intf != null) {
                    return intf;
                }
            }
            Class<?> foundOnSuperclass = getInterfaceClassOnSuperClasses(superClass, interfaceName);
            if (foundOnSuperclass != null) {
                return foundOnSuperclass;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> loadProviderClasses(BundleContext callingContext,
                                                   Map<String, Object> sd, String propName) {
        Object serviceProviders = sd.get(propName);
        if (serviceProviders != null) {
            if (serviceProviders.getClass().isArray()) {
                if (serviceProviders.getClass().getComponentType() == String.class) {
                    return loadProviders(callingContext, (String[])serviceProviders);
                } else {
                    return Arrays.asList((Object[])serviceProviders);
                }
            } else if (serviceProviders.getClass() == String.class) {
                String[] classNames = serviceProviders.toString().split(",");
                return loadProviders(callingContext, classNames);
            } else if (serviceProviders instanceof List) {
                List<Object> list = (List<Object>)serviceProviders;
                if (!list.isEmpty()) {
                    List<Object> providers;
                    if (list.get(0).getClass() == String.class) {
                        providers = loadProviders(callingContext, list.toArray(new String[]{}));
                    } else {
                        providers = list;
                    }
                    return providers;
                }
            } else {
                return Arrays.asList(serviceProviders);
            }
        }
        return Collections.emptyList();

    }

    private static List<Object> loadProviders(BundleContext callingContext, String[] classNames) {
        List<Object> providers = new ArrayList<Object>();
        for (String className : classNames) {
            try {
                String realName = className.trim();
                if (!realName.isEmpty()) {
                    Class<?> pClass = callingContext.getBundle().loadClass(realName);
                    providers.add(pClass.newInstance());
                }
            } catch (Exception ex) {
                LOG.warn("Provider " + className.trim() + " can not be loaded or created " + ex.getMessage(), ex);
            }
        }
        return providers;
    }
}
