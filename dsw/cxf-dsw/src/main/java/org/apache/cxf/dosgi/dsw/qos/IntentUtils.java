package org.apache.cxf.dosgi.dsw.qos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.apache.cxf.dosgi.dsw.util.OsgiUtils;
import org.apache.cxf.dosgi.dsw.util.Utils;
import org.apache.cxf.ws.policy.spring.PolicyNamespaceHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

public class IntentUtils {
    private static final String[] INTENT_MAP = {
        "/OSGI-INF/cxf/intents/intent-map.xml"
    };

	public static String formatIntents(String[] intents) {
	    StringBuilder sb = new StringBuilder();
	    boolean first = true;
	    for (String intent : intents) {
	        if (first) {
	            first = false;
	        } else {
	            sb.append(' ');
	        }
	        sb.append(intent);
	    }
	    return sb.toString();
	}

	public static String[] parseIntents(String intentsSequence) {
	    return intentsSequence == null ? new String[] {} : intentsSequence.split(" ");
	}

	public static IntentMap getIntentMap(BundleContext bundleContext) {
	    IntentMap im = IntentUtils.readIntentMap(bundleContext);
	    if (im == null) {
	        // Couldn't read an intent map
	        OsgiUtils.LOG.log(Level.FINE, "Using default intent map");
	        im = new IntentMap();
	        im.setIntents(new HashMap<String, Object>());
	    }
	    return im;
	}

	public static IntentMap readIntentMap(BundleContext bundleContext) {
	    List<String> springIntentLocations = new ArrayList<String>();
	    for (String mapFile : INTENT_MAP) {
	        if (bundleContext.getBundle().getResource(mapFile) == null) {
	            OsgiUtils.LOG.info("Could not find intent map file " + mapFile);
	            return null;
	        }
	        springIntentLocations.add("classpath:" + mapFile);
	    }
	
	    try {
	        
	        // switch to cxf bundle classloader for spring
	    	ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
	        Thread.currentThread().setContextClassLoader(PolicyNamespaceHandler.class.getClassLoader());
	        
	        OsgiUtils.LOG.fine("Loading Intent map from "+springIntentLocations);
	        OsgiBundleXmlApplicationContext ctx = new OsgiBundleXmlApplicationContext(springIntentLocations
	            .toArray(new String[] {}));
	        ctx.setPublishContextAsService(false);
	        ctx.setBundleContext(bundleContext);
	        ctx.refresh();
	        OsgiUtils.LOG.fine("application context: " + ctx);
	        IntentMap im = (IntentMap)ctx.getBean("intentMap");
	        OsgiUtils.LOG.fine("retrieved intent map: " + im);
	
	        Thread.currentThread().setContextClassLoader(oldClassLoader);
	
	        return im;
	    } catch (Throwable t) {
	        OsgiUtils.LOG.log(Level.WARNING, "Intent map load failed: ", t);
	        return null;
	    }
	}

    @SuppressWarnings("rawtypes")
    public static String[] getAllRequiredIntents(Map serviceProperties){
        // Get the intents that need to be supported by the RSA
        String[] requiredIntents = Utils.normalizeStringPlus(serviceProperties.get(RemoteConstants.SERVICE_EXPORTED_INTENTS));
        if(requiredIntents==null){
            requiredIntents = new String[0];
        }
        
        { // merge with extra intents;
            String[] requiredExtraIntents = Utils.normalizeStringPlus(serviceProperties.get(RemoteConstants.SERVICE_EXPORTED_INTENTS_EXTRA));
            if(requiredExtraIntents!= null && requiredExtraIntents.length>0){
                         
                requiredIntents = IntentUtils.mergeArrays(requiredIntents, requiredExtraIntents);
            }
        }
        
        return requiredIntents;
    }

    @SuppressWarnings("rawtypes")
    public static String[] getInetntsImplementedByTheService(Map serviceProperties){
        // Get the Intents that are implemented by the service 
        String[] serviceIntents = Utils.normalizeStringPlus(serviceProperties.get(RemoteConstants.SERVICE_INTENTS));
        
        return serviceIntents;
    }

    public static String[] mergeArrays(String[] a1,String[] a2){
        if(a1==null) return a2;
        if(a2==null) return a1;
        
        List<String> list = new ArrayList<String>(a1.length+a2.length);
    
        for (String s : a1) {
            list.add(s);  
        }
        
        for (String s : a2) {
            if(!list.contains(s))
                list.add(s);  
        }
        
        return list.toArray(new String[list.size()]);
    }

}