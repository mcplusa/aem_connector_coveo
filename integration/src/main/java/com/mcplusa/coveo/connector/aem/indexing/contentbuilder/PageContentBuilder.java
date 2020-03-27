package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.day.cq.commons.Externalizer;
import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.Permission;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import java.lang.reflect.Type;
import javax.annotation.Nonnull;
import javax.jcr.Session;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
@Service(CoveoContentBuilder.class)
@Property(name = CoveoIndexConfiguration.PRIMARY_TYPE, value = PageContentBuilder.PRIMARY_TYPE_VALUE)
public class PageContentBuilder extends AbstractCoveoContentBuilder {

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private RequestResponseFactory requestResponseFactory;

    @Reference
    private SlingRequestProcessor requestProcessor;

    public static final String PRIMARY_TYPE_VALUE = "cq:Page";

    private static final Logger LOG = LoggerFactory.getLogger(PageContentBuilder.class);
    private static final String[] FIXED_RULES = {"cq:lastModified", "cq:template", "jcr:description", "jcr:title", "rep:policy"};

    @Override
    public IndexEntry create(String path, @Nonnull ResourceResolver resolver, boolean includeContent) {
        String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);
        if (ArrayUtils.isNotEmpty(indexRules)) {
            Externalizer externalizer = resolver.adaptTo(Externalizer.class);
            PageManager pageManager = resolver.adaptTo(PageManager.class);
            if (pageManager != null) {
                Page page = pageManager.getPage(path);
                if (page != null) {
                    String documentId = path;
                    if (externalizer != null) {
                        documentId = externalizer.publishLink(resolver, path) + ".html";
                    }
                    Resource res = page.getContentResource();
                    IndexEntry ret = new IndexEntry("idx", "page", path);
                    ret.setDocumentId(documentId);

                    if (includeContent) {
                        ret.addContent(getProperties(res, indexRules));

                        // Extract additional properties from the Node
                        try {
                            Session adminSession = resolver.adaptTo(Session.class);
                            Node node = adminSession.getNode(path);
                            JsonObject content = toJson(node).getAsJsonObject("jcr:content");
                            if (content != null) {
                                Map<String, Object> allprops = getJsonProperties(content, indexRules);
                                ret.addContent(allprops);
                            }
                        } catch (Exception ex) {
                            LOG.error("Could not extract additionals properties from the node", ex);
                        }

                        ret.addContent("title", page.getTitle());
                        ret.addContent("author", this.<String>getLastValue(res.getValueMap(), "jcr:createdBy"));
                        ret.addContent("lastmodified", page.getLastModified().getTimeInMillis());
                        ret.addContent("created", this.<Long>getLastValue(res.getValueMap(), "jcr:created"));
                        ret.addContent("content", getHtmlContent(resolver, path + ".html"));

                        try {
                            ResourceResolver resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
                            Session adminSession = resourceResolver.adaptTo(Session.class);
                            UserManager userManager = resourceResolver.adaptTo(UserManager.class);

                            if (adminSession != null) {
                                Node node = adminSession.getNode(path);

                                JsonObject policy = toJson(node).getAsJsonObject("rep:policy");
                                List<Permission> acls = getACLs(policy, userManager);
                                Type listType = new TypeToken<List<Permission>>() {
                                }.getType();
                                String aclJson = new Gson().toJson(acls, listType);
                                ret.addContent("acl", aclJson);
                            }
                        } catch (Exception ex) {
                            LOG.error("error policy", ex);
                        }
                    }

                    return ret;
                }
            }
        } else {
            LOG.warn("Could not load indexRules for " + PRIMARY_TYPE_VALUE);
        }

        return null;
    }

    private String getHtmlContent(ResourceResolver resourceResolver, String path) {
        try {
            HttpServletRequest request = requestResponseFactory.createRequest("GET", path);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            HttpServletResponse response = requestResponseFactory.createResponse(out);
            requestProcessor.processRequest(request, response, resourceResolver);

            String html = new String(out.toByteArray(), "UTF-8");
            return Base64.getEncoder().encodeToString(html.getBytes());
        } catch (UnsupportedEncodingException ex) {
            LOG.error("Error getting Page content", ex);
        } catch (ServletException | IOException ex) {
            LOG.error("Error getting Page content", ex);
        }

        return null;
    }

    @Override
    protected String[] getFixedRules() {
        return FIXED_RULES;
    }

}
