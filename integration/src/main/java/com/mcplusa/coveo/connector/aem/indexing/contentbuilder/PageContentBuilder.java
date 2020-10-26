package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.day.cq.commons.Externalizer;
import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.day.cq.tagging.TagManager;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.NodePermissionLevel;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
<<<<<<< HEAD
import java.lang.reflect.Type;
import javax.annotation.Nonnull;
import javax.jcr.Session;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
=======
import com.mcplusa.coveo.connector.aem.service.CoveoService;

>>>>>>> 369b48311d342b2009f953de36ef64d9c1499be0
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
@Property(
    name = CoveoIndexConfiguration.PRIMARY_TYPE,
    value = PageContentBuilder.PRIMARY_TYPE_VALUE)
public class PageContentBuilder extends AbstractCoveoContentBuilder {

<<<<<<< HEAD
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
                            Session session = resolver.adaptTo(Session.class);
                            Node node = session.getNode(path);
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
                        ret.addContent("created", this.<Long>getLastValue(res.getValueMap(), "jcr:created"));
                        ret.addContent("content", getHtmlContent(resolver, path + ".html"));

                        if (page.getLastModified() != null) {
                            ret.addContent("lastmodified", page.getLastModified().getTimeInMillis());
                        }

                        try {
                            ResourceResolver resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
                            Session adminSession = resourceResolver.adaptTo(Session.class);
                            UserManager userManager = resourceResolver.adaptTo(UserManager.class);

                            if (adminSession != null) {
                                Node node = adminSession.getNode(path);

                                JsonObject policy = toJson(node).getAsJsonObject("rep:policy");
                                if (policy != null) {
                                    List<Permission> acls = getACLs(policy, userManager);
                                    Type listType = new TypeToken<List<Permission>>() {
                                    }.getType();
                                    String aclJson = new Gson().toJson(acls, listType);
                                    ret.addContent("acl", aclJson);
                                }
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
=======
  @Reference private ResourceResolverFactory resolverFactory;

  @Reference private RequestResponseFactory requestResponseFactory;

  @Reference private SlingRequestProcessor requestProcessor;

  @Reference
  protected CoveoService coveoService;

  public static final String PRIMARY_TYPE_VALUE = "cq:Page";

  private static final Logger LOG = LoggerFactory.getLogger(PageContentBuilder.class);
  private static final String[] FIXED_RULES = {
    "cq:lastModified", "cq:template", "jcr:description", "jcr:title", "rep:policy"
  };

  @Override
  public IndexEntry create(
      String path, @Nonnull ResourceResolver resolver, boolean includeContent) {
    Externalizer externalizer = resolver.adaptTo(Externalizer.class);
    PageManager pageManager = resolver.adaptTo(PageManager.class);
    setTagManager(resolver.adaptTo(TagManager.class));
    this.permissionPolicy = coveoService.getPermissionPolicy();
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
          ret.addContent(getDocumentContent(resolver, res, page, path));
>>>>>>> 369b48311d342b2009f953de36ef64d9c1499be0
        }

        return ret;
      }
    }

    return null;
  }

  @Override
  public IndexEntry createDeletedItem(String path, ResourceResolver resolver) {
    Externalizer externalizer = resolver.adaptTo(Externalizer.class);
    String documentId = path;
    if (externalizer != null) {
      documentId = externalizer.publishLink(resolver, path);
    }
    IndexEntry ret = new IndexEntry("idx", "page", path);
    ret.setDocumentId(documentId);

    return ret;
  }

  private Map<String, Object> getDocumentContent(
      ResourceResolver resolver, Resource res, Page page, String path) {
    Map<String, Object> mapContent = new HashMap<>();
    String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);

    mapContent.putAll(getProperties(res, indexRules));

    // Extract additional properties from the Node
    try {
      Session session = resolver.adaptTo(Session.class);
      Node node = session.getNode(path);
      JsonObject content = toJson(node).getAsJsonObject("jcr:content");
      if (content != null) {
        Map<String, Object> allprops = getJsonProperties(content, indexRules);
        mapContent.putAll(allprops);
      }
    } catch (Exception ex) {
      LOG.error("Could not extract additionals properties from the node", ex);
    }

    mapContent.put("title", page.getTitle());
    mapContent.put("author", this.getLastValue(res.getValueMap(), "jcr:createdBy", String.class));
    mapContent.put("created", this.getLastValue(res.getValueMap(), "jcr:created", Long.class));
    mapContent.put("content", getHtmlContent(resolver, path + ".html"));

    if (page.getLastModified() != null) {
      mapContent.put("lastmodified", page.getLastModified().getTimeInMillis());
    }

    // Retrieve ACLs from policy
    ResourceResolver resourceResolver = null;
    try {
      resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
      Session adminSession = resourceResolver.adaptTo(Session.class);
      UserManager userManager = resourceResolver.adaptTo(UserManager.class);

      Node node = adminSession.getNode(path);
      List<NodePermissionLevel> permLevels = getPermissionLevelList(node, userManager);

      Type listType =
          new TypeToken<List<NodePermissionLevel>>() {
            private static final long serialVersionUID = -452808663442673585L;
          }.getType();
      String aclJson = new Gson().toJson(permLevels, listType);

      mapContent.put("acl", aclJson);
    } catch (Exception ex) {
      LOG.error("Error getting Permissions for page " + path, ex);
    } finally {
      if (resourceResolver != null && resourceResolver.isLive()) {
        resourceResolver.close();
      }
    }

    return mapContent;
  }

  private String getHtmlContent(ResourceResolver resourceResolver, String path) {
    try {
      HttpServletRequest request = requestResponseFactory.createRequest("GET", path);

      ByteArrayOutputStream out = new ByteArrayOutputStream();

      HttpServletResponse response = requestResponseFactory.createResponse(out);
      requestProcessor.processRequest(request, response, resourceResolver);

      String html = new String(out.toByteArray(), StandardCharsets.UTF_8);
      return Base64.getEncoder().encodeToString(html.getBytes());
    } catch (ServletException | IOException ex) {
      LOG.error("Error getting Page content", ex);
    } catch (Exception ex) {
      LOG.error("Unexpected Error getting Page content", ex);
    }

    return null;
  }

  @Override
  protected String[] getFixedRules() {
    return FIXED_RULES;
  }
}
