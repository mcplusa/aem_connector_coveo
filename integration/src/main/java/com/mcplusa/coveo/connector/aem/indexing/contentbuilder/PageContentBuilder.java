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
import com.mcplusa.coveo.connector.aem.service.CoveoService;

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
import org.apache.jackrabbit.api.security.user.Authorizable;
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
    IndexEntry ret = new IndexEntry("idx", "document", path);
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
    try {

      ResourceResolver resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
      Session adminSession = resourceResolver.adaptTo(Session.class);
      UserManager userManager = resourceResolver.adaptTo(UserManager.class);
      List<Authorizable> authorizables = getAllAuthorizables(userManager);

      Node node = adminSession.getNode(path);
      List<NodePermissionLevel> permLevels = getPermissionLevelList(node, authorizables);

      Type listType =
          new TypeToken<List<NodePermissionLevel>>() {
            private static final long serialVersionUID = -452808663442673585L;
          }.getType();
      String aclJson = new Gson().toJson(permLevels, listType);

      mapContent.put("acl", aclJson);
    } catch (Exception ex) {
      LOG.error("Error getting Permissions for page " + path, ex);
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
    }

    return null;
  }

  @Override
  protected String[] getFixedRules() {
    return FIXED_RULES;
  }
}
