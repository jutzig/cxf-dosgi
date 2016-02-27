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
package org.apache.cxf.dosgi.distribution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.cxf.binding.BindingConfiguration;
import org.apache.cxf.dosgi.dsw.qos.IntentMap;
import org.apache.cxf.dosgi.dsw.qos.IntentUnsatisfiedException;
import org.apache.cxf.dosgi.dsw.qos.IntentUtils;
import org.apache.cxf.endpoint.AbstractEndpointFactory;
import org.apache.cxf.feature.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntentManagerImpl implements IntentManager {

    static final Logger LOG = LoggerFactory.getLogger(IntentManagerImpl.class);
    private static final String PROVIDED_INTENT_VALUE = "PROVIDED";

    private final IntentMap intentMap;
    private final long maxIntentWaitTime;

    public IntentManagerImpl(IntentMap intentMap) {
        this(intentMap, 0);
    }

    public IntentManagerImpl(IntentMap intentMap, int maxIntentWaitTime) {
        this.intentMap = intentMap;
        this.maxIntentWaitTime = maxIntentWaitTime;
    }

    public String[] applyIntents(List<Feature> features, AbstractEndpointFactory factory,
                                 Map<String, Object> props) throws IntentUnsatisfiedException {
        Set<String> requestedIntents = IntentUtils.getRequestedIntents(props);
        Set<String> appliedIntents = new HashSet<String>();
        appliedIntents.addAll(reverseLookup(intentMap, PROVIDED_INTENT_VALUE));
        boolean bindingApplied = false;
        for (String intentName : requestedIntents) {
            bindingApplied |= processIntent(features, factory, intentName, intentMap.get(intentName));
            appliedIntents.add(intentName);
        }
        if (!bindingApplied) {
            String defaultBindingName = "SOAP";
            processIntent(features, factory, defaultBindingName, intentMap.get(defaultBindingName));
            appliedIntents.add(defaultBindingName);
        }
        appliedIntents.addAll(addSynonymIntents(appliedIntents, intentMap));
        return appliedIntents.toArray(new String[appliedIntents.size()]);
    }

    private boolean processIntent(List<Feature> features, AbstractEndpointFactory factory,
                                  String intentName, Object intent) throws IntentUnsatisfiedException {
        if (intent instanceof String) {
            if (PROVIDED_INTENT_VALUE.equalsIgnoreCase((String) intent)) {
                return false;
            }
        } else if (intent instanceof BindingConfiguration) {
            BindingConfiguration bindingCfg = (BindingConfiguration)intent;
            LOG.info("Applying intent: " + intentName + " via binding config: " + bindingCfg);
            factory.setBindingConfig(bindingCfg);
            return true;
        } else if (intent instanceof Feature) {
            Feature feature = (Feature) intent;
            LOG.info("Applying intent: " + intentName + " via feature: " + feature);
            features.add(feature);
            return false;
        } else {
            LOG.info("No mapping for intent: " + intentName);
            throw new IntentUnsatisfiedException(intentName);
        }
        return false;
    }

    private static Collection<String> addSynonymIntents(Collection<String> appliedIntents,
                                                 IntentMap map) {
        // E.g. SOAP and SOAP.1_1 are synonyms
        List<Object> values = new ArrayList<Object>();
        for (String key : appliedIntents) {
            values.add(map.get(key));
        }
        return reverseLookup(map, values);
    }

    private static Collection<String> reverseLookup(IntentMap im, Object obj) {
        return reverseLookup(im, Collections.singleton(obj));
    }

    /**
     * Retrieves all keys whose mapped values are found in the given collection.
     *
     * @param im an intent map
     * @param values a collection of potential values
     * @return all keys whose mapped values are found in the given collection
     */
    private static Collection<String> reverseLookup(IntentMap im, Collection<?> values) {
        Set<String> intentsFound = new HashSet<String>();
        for (Map.Entry<String, Object> entry : im.entrySet()) {
            if (values.contains(entry.getValue())) {
                intentsFound.add(entry.getKey());
            }
        }
        return intentsFound;
    }

    public void assertAllIntentsSupported(Map<String, Object> serviceProperties) {
        long endTime = System.currentTimeMillis() + maxIntentWaitTime;
        Set<String> requiredIntents = IntentUtils.getRequestedIntents(serviceProperties);
        List<String> unsupportedIntents = new ArrayList<String>();
        do {
            unsupportedIntents.clear();
            for (String ri : requiredIntents) {
                if (!intentMap.containsKey(ri)) {
                    unsupportedIntents.add(ri);
                }
            }
            long remainingSeconds = (endTime - System.currentTimeMillis()) / 1000;
            if (!unsupportedIntents.isEmpty() && remainingSeconds > 0) {
                LOG.debug("Waiting for custom intents " + unsupportedIntents + " timeout in " + remainingSeconds);
                try {
                    synchronized (intentMap) {
                        intentMap.wait(1000);
                    }
                } catch (InterruptedException e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
        } while (!unsupportedIntents.isEmpty() && System.currentTimeMillis() < endTime);

        if (!unsupportedIntents.isEmpty()) {
            throw new RuntimeException("service cannot be exported because the following "
                                       + "intents are not supported by this RSA: " + unsupportedIntents);
        }
    }
}
