package com.core.flush.refetching;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.replication.ContentBuilder;
import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationContent;
import com.day.cq.replication.ReplicationContentFactory;
import com.day.cq.replication.ReplicationException;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.osgi.framework.Constants;

/**
 * Custom dispatcher flush content builder that sends a list of URIs
 * to be re-fetched immediately upon flushing a page.
 */
@Component(service=ContentBuilder.class,property={"name=dispatcher-re-fetching",Constants.SERVICE_RANKING +":Integer=1001"})
public class DispatcherFlushContentBuilder implements ContentBuilder {

	@Reference
	private ResourceResolverFactory rrFactory;
	
	private static final Logger logger = LoggerFactory.getLogger(DispatcherFlushContentBuilder.class);

    public static final String NAME = "dispatcher-re-fetching";

    public static final String TITLE = "Re-fetch Dispatcher Flush";

    
	
    /**
     * Create the replication content, containing one or more URIs to be
     * re-fetched immediately upon flushing a page.
     *
     * @param factory factory
     * @param uris URIs to re-fetch
     * @return replication content
     *
     * @throws ReplicationException if an error occurs
     */
    private ReplicationContent create(ReplicationContentFactory factory, String[] uris) 
            throws ReplicationException {
            
        File tmpFile;
        BufferedWriter out = null;
        
        try {
            tmpFile = File.createTempFile("dispatcher-refetching", ".post");
        } catch (IOException e) {
            throw new ReplicationException("Unable to create temp file", e);
        }
        
        try {
            out = new BufferedWriter(new FileWriter(tmpFile));
            for (int i = 0; i < uris.length; i++) {
                out.write(uris[i]);
                out.newLine();
                logger.debug("adding "+uris[i]);
            }
            out.close();
            IOUtils.closeQuietly(out);
            out = null;
            return factory.create("text/plain", tmpFile, true);
        } catch (IOException e) {
            if (out != null) {
                IOUtils.closeQuietly(out);
            }
            tmpFile.delete();
            throw new ReplicationException("Unable to create repository content", e);
        }
    }
    
    /**
     * {@inheritDoc}
     *
     * @return {@value #NAME}
     */
    public String getName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #TITLE}
     */
    public String getTitle() {
        return TITLE;
    }

	@Override
	public ReplicationContent create(Session session, ReplicationAction action, ReplicationContentFactory factory)
			throws ReplicationException {


    	
    	ResourceResolver rr = null;
    	PageManager pm = null;
    	
    	try {
			HashMap<String,Object> map = new HashMap<String,Object>();
			map.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
			rr = rrFactory.getResourceResolver(map);
			if (rr != null) {
				pm = rr.adaptTo(PageManager.class);
			}
		} catch (LoginException e) {
			logger.error(e.getMessage(),e);
		}
        
        /* In this simple example we check whether the page activated has
        no extension (e.g. /content/geometrixx/en/services) and add
        this page plus html extension to the list of URIs to re-fetch */

        String path = action.getPath();
        if (path != null) {
            int pathSep = path.lastIndexOf('/');
            if (pathSep != -1) {
                int extSep = path.indexOf('.', pathSep);
                if (extSep == -1) {
                	ArrayList<String> urisList = new ArrayList<String>();
                	urisList.add(path + ".html");
                	if (pm != null) {
                		/* Check if the link is a page and if it has any vanityPath, and use the mapping url
                		 * in case any aliases are used or any /etc/mapping
                		 */
                    	Page flushedPage = pm.getPage(path);
                    	if (flushedPage != null) {
                    		// try to find if that page has also some alias or vanity url
                    		ValueMap props = flushedPage.getProperties();
                    		String vanityPath = props.get("sling:vanityPath", "");
                    		if (vanityPath.length() > 0 ) {
                            	urisList.add(vanityPath);
                    		}
                        	String mapped = rr.map(path);
                        	if (!path.equals(mapped)) {
                            	urisList.add(mapped + ".html");
                        	}
                    	}
                	}
                    String[] uris = urisList.toArray(new String[urisList.size()]);
                    return create(factory, uris);
                } else {    
                    String[] uris = new String[] { path };
                    return create(factory, uris);
                }

            }
        }
        return ReplicationContent.VOID;
    
	
	}

	@Override
	public ReplicationContent create(Session session, ReplicationAction action, ReplicationContentFactory factory,
			Map<String, Object> arg3) throws ReplicationException {
		return create(session,action,factory);
	}



}
