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
package org.apache.cxf.dosgi.discovery.zookeeper;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Properties;

import org.apache.cxf.dosgi.discovery.zookeeper.publish.PublishingEndpointListenerFactory;
import org.apache.cxf.dosgi.discovery.zookeeper.subscribe.EndpointListenerTracker;
import org.apache.cxf.dosgi.discovery.zookeeper.subscribe.InterfaceMonitorManager;
import org.apache.cxf.dosgi.discovery.zookeeper.util.Utils;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperDiscovery implements Watcher, ManagedService {

    public static final String DISCOVERY_ZOOKEEPER_ID = "org.apache.cxf.dosgi.discovery.zookeeper";

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperDiscovery.class);

    private final BundleContext bctx;

    private PublishingEndpointListenerFactory endpointListenerFactory;
    private ServiceTracker endpointListenerTracker;
    private InterfaceMonitorManager imManager;
    private ZooKeeper zk;
    private boolean closed;
    private boolean started;

    private Dictionary<String, ?> curConfiguration;

    public ZooKeeperDiscovery(BundleContext bctx) {
        this.bctx = bctx;
        try {
            updated(new Properties());
        } catch (ConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void setDefaults(Dictionary<String, String> configuration) {
        Utils.setDefault(configuration, "zookeeper.host", "utzigj02.seeburger.de");
        Utils.setDefault(configuration, "zookeeper.port", "2181");
        Utils.setDefault(configuration, "zookeeper.timeout", "3000");
    }

    @SuppressWarnings("unchecked")
    public synchronized void updated(Dictionary configuration) throws ConfigurationException {
        LOG.debug("Received configuration update for Zookeeper Discovery: {}", configuration);
        if (configuration != null) {
            setDefaults((Dictionary<String, String>)configuration);
        }
        // make changes only if config actually changed, to prevent unnecessary ZooKeeper reconnections
        if (!Utils.toMap(configuration).equals(Utils.toMap(curConfiguration))) {
            stop(false);
            curConfiguration = configuration;
            // config is null if it doesn't exist, is being deleted or has not yet been loaded
            // in which case we just stop running
            if (configuration != null) {
                createZooKeeper(configuration);
            }
        }
    }

    private synchronized void start() {
        if (closed) {
            return;
        }
        if (started) {
            // we must be re-entrant, i.e. can be called when already started
            LOG.debug("ZookeeperDiscovery already started");
            return;
        }
        LOG.debug("starting ZookeeperDiscovery");
        endpointListenerFactory = new PublishingEndpointListenerFactory(zk, bctx);
        endpointListenerFactory.start();
        imManager = new InterfaceMonitorManager(bctx, zk);
        endpointListenerTracker = new EndpointListenerTracker(bctx, imManager);
        endpointListenerTracker.open();
        started = true;
    }

    public synchronized void stop(boolean close) {
        if (started) {
            LOG.debug("stopping ZookeeperDiscovery");
        }
        started = false;
        closed |= close;
        if (endpointListenerFactory != null) {
            endpointListenerFactory.stop();
        }
        if (endpointListenerTracker != null) {
            endpointListenerTracker.close();
        }
        if (imManager != null) {
            imManager.close();
        }
        if (zk != null) {
            try {
                zk.close();
            } catch (InterruptedException e) {
                LOG.error("Error closing ZooKeeper", e);
            }
        }
    }

    private synchronized void createZooKeeper(Dictionary<String, ?> configuration) {
        if (closed) {
            return;
        }
        String host = configuration.get("zookeeper.host").toString();
        String port = configuration.get("zookeeper.port").toString();
        int timeout = Integer.parseInt(configuration.get("zookeeper.timeout").toString());
        LOG.debug("ZooKeeper configuration: connecting to {}:{} with timeout {}",
                new Object[]{host, port, timeout});
        try {
            zk = new ZooKeeper(host + ":" + port, timeout, this);
        } catch (IOException e) {
            LOG.error("Failed to start the ZooKeeper Discovery component.", e);
        }
    }

    /* Callback for ZooKeeper */
    public void process(WatchedEvent event) {
        LOG.debug("got ZooKeeper event " + event);
        switch (event.getState()) {
        case SyncConnected:
            LOG.info("Connection to ZooKeeper established");
            // this event can be triggered more than once in a row (e.g. after Disconnected event),
            // so we must be re-entrant here
            start();
            break;

        case Expired:
            LOG.info("Connection to ZooKeeper expired. Trying to create a new connection");
            stop(false);
            createZooKeeper(curConfiguration);
            break;

        default:
            // ignore other events
            break;
        }
    }
}
