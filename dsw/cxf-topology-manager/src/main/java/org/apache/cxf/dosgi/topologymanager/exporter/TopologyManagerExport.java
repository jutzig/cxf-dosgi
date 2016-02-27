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
package org.apache.cxf.dosgi.topologymanager.exporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.cxf.dosgi.topologymanager.util.SimpleServiceTracker;
import org.apache.cxf.dosgi.topologymanager.util.SimpleServiceTrackerListener;
import org.apache.cxf.dosgi.topologymanager.util.Utils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages exported endpoints of DOSGi services and notifies EndpointListeners of changes.
 *
 * <li> Tracks local RemoteServiceAdmin instances by using a ServiceTracker
 * <li> Uses a ServiceListener to track local OSGi services
 * <li> When a service is published that is supported by DOSGi the
 *      known RemoteServiceAdmins are instructed to export the service and
 *      the EndpointListeners are notified
 * <li> When a service is unpublished the EndpointListeners are notified.
 *      The endpoints are not closed as the ExportRegistration takes care of this
 */
public class TopologyManagerExport {

    private static final String DOSGI_SERVICES = "(" + RemoteConstants.SERVICE_EXPORTED_INTERFACES + "=*)";

    private static final Logger LOG = LoggerFactory.getLogger(TopologyManagerExport.class);

    private final BundleContext bctx;
    private final EndpointListenerNotifier epListenerNotifier;
    private final ExecutorService execService;
    private final SimpleServiceTracker<RemoteServiceAdmin> remoteServiceAdminTracker;
    private final ServiceListener serviceListener;
    private final EndpointRepository endpointRepo;

    public TopologyManagerExport(BundleContext ctx, SimpleServiceTracker<RemoteServiceAdmin> rsaTracker) {
        this(ctx, rsaTracker, null);
    }

    public TopologyManagerExport(BundleContext ctx, SimpleServiceTracker<RemoteServiceAdmin> rsaTracker,
                                 EndpointListenerNotifier notif) {
        endpointRepo = new EndpointRepository();
        epListenerNotifier = notif == null ? new EndpointListenerNotifier(ctx, endpointRepo) : notif;
        execService = new ThreadPoolExecutor(5, 10, 50, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        bctx = ctx;
        remoteServiceAdminTracker = rsaTracker;

        // track RemoteServiceAdmins through which we can export services
        remoteServiceAdminTracker.addListener(new SimpleServiceTrackerListener<RemoteServiceAdmin>() {

            public void added(ServiceReference reference, RemoteServiceAdmin rsa) {
                LOG.debug("RemoteServiceAdmin added: {}, total {}",
                        rsa, remoteServiceAdminTracker.getAllServices().size());
                for (ServiceReference serviceRef : endpointRepo.getServicesToBeExportedFor(rsa)) {
                    triggerExport(serviceRef);
                }
            }

            public void modified(ServiceReference reference, RemoteServiceAdmin rsa) {
            }

            public void removed(ServiceReference reference, RemoteServiceAdmin rsa) {
                LOG.debug("RemoteServiceAdmin removed: {}, total {}", rsa,
                        remoteServiceAdminTracker.getAllServices().size());
                List<EndpointDescription> endpoints = endpointRepo.removeRemoteServiceAdmin(rsa);
                epListenerNotifier.notifyListeners(false, endpoints);
            }
        });

        // track all service registrations so we can export any services that are configured to be exported
        // ServiceListener events may be delivered out of order, concurrently, re-entrant, etc. (see spec or docs)
        serviceListener = new ServiceListener() {

            public void serviceChanged(ServiceEvent event) {
                ServiceReference sref = event.getServiceReference();
                if (event.getType() == ServiceEvent.REGISTERED) {
                    LOG.debug("Received REGISTERED ServiceEvent: {}", event);
                    if (shouldExportService(sref)) {
                        triggerExport(sref);
                    }
                } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                    LOG.debug("Received UNREGISTERING ServiceEvent: {}", event);
                    List<EndpointDescription> endpoints = endpointRepo.removeService(sref);
                    epListenerNotifier.notifyListeners(false, endpoints);
                }
            }
        };
    }

    /**
     * checks if a Service is intended to be exported
     */
    private boolean shouldExportService(ServiceReference sref) {
        return sref.getProperty(RemoteConstants.SERVICE_EXPORTED_INTERFACES) != null;
    }

    public void start() {
        epListenerNotifier.start();
        bctx.addServiceListener(serviceListener);
        exportExistingServices();
    }

    public void stop() {
        execService.shutdown();
        bctx.removeServiceListener(serviceListener);
        epListenerNotifier.stop();
    }

    protected void triggerExport(final ServiceReference sref) {
        execService.execute(new Runnable() {
            public void run() {
                try {
                    doExportService(sref);
                } catch (Throwable t) {
                    LOG.error("export failed", t);
                }
            }
        });
    }

    protected void doExportService(final ServiceReference sref) {
        LOG.debug("Exporting service {}", sref);
        endpointRepo.addService(sref); // mark for future export even if there are currently no RSAs
        List<RemoteServiceAdmin> rsaList = remoteServiceAdminTracker.getAllServices();
        if (rsaList.isEmpty()) {
            LOG.error("No RemoteServiceAdmin available! Unable to export service from bundle {}, interfaces: {}",
                    Utils.getBundleName(sref),
                    sref.getProperty(org.osgi.framework.Constants.OBJECTCLASS));
        }

        for (final RemoteServiceAdmin remoteServiceAdmin : rsaList) {
            LOG.info("TopologyManager: handling remoteServiceAdmin " + remoteServiceAdmin);

            if (endpointRepo.isAlreadyExportedForRsa(sref, remoteServiceAdmin)) {
                // already handled by this remoteServiceAdmin
                LOG.debug("already handled by this remoteServiceAdmin -> skipping");
            } else {
                exportServiceUsingRemoteServiceAdmin(sref, remoteServiceAdmin);
            }
        }
    }

    private void exportServiceUsingRemoteServiceAdmin(final ServiceReference sref,
                                                      final RemoteServiceAdmin remoteServiceAdmin) {
        // abort if the service was unregistered by the time we got here
        // (we check again at the end, but this optimization saves unnecessary heavy processing)
        if (sref.getBundle() == null) {
            LOG.info("TopologyManager: export aborted for {} since it was unregistered", sref);
            endpointRepo.removeService(sref);
            return;
        }
        // do the export
        LOG.debug("exporting {}...", sref);
        // TODO: additional parameter Map?
        Collection<ExportRegistration> exportRegs = remoteServiceAdmin.exportService(sref, null);
        if (exportRegs.isEmpty()) {
            LOG.warn("TopologyManager: nothing was exported for {}", sref);
            return;
        }
        // process successful/failed registrations
        List<EndpointDescription> endpoints = new ArrayList<EndpointDescription>();
        for (ExportRegistration reg : exportRegs) {
            if (reg.getException() == null) {
                EndpointDescription endpoint = getExportedEndpoint(reg);
                LOG.info("TopologyManager: export succeeded for {}, endpoint ", sref, endpoint);
                endpoints.add(endpoint);
            } else {
                // TODO: what should we do with failed exports?
                LOG.error("TopologyManager: export failed for {}", sref);
                reg.close();
            }
        }
        // abort export if service was unregistered in the meanwhile (since we have a race
        // with the unregister event which may have already been handled, so we'll miss it)
        if (sref.getBundle() == null) {
            LOG.info("TopologyManager: export reverted for {} since service was unregistered", sref);
            endpointRepo.removeService(sref);
            for (ExportRegistration reg : exportRegs) {
                reg.close();
            }
            return;
        }
        // add the new exported endpoints
        if (!endpoints.isEmpty()) {
            LOG.info("TopologyManager: export successful for {}, endpoints: {}", sref, endpoints);
            endpointRepo.addEndpoints(sref, remoteServiceAdmin, endpoints);
            epListenerNotifier.notifyListeners(true, endpoints); // notify after endpoints are added to repo
        }
    }

    /**
     * Retrieves an exported Endpoint (while safely handling nulls).
     *
     * @param exReg an export registration
     * @return exported Endpoint or null if not present
     */
    private EndpointDescription getExportedEndpoint(ExportRegistration exReg) {
        ExportReference ref = (exReg == null) ? null : exReg.getExportReference();
        return (ref == null) ? null : ref.getExportedEndpoint();
    }

    private void exportExistingServices() {
        try {
            // cast to String is necessary for compiling against OSGi core version >= 4.3
            ServiceReference[] references = bctx.getServiceReferences((String)null, DOSGI_SERVICES);
            if (references != null) {
                for (ServiceReference sref : references) {
                    triggerExport(sref);
                }
            }
        } catch (InvalidSyntaxException e) {
            LOG.error("Error in filter {}. This should not occur!", DOSGI_SERVICES);
        }
    }
}
