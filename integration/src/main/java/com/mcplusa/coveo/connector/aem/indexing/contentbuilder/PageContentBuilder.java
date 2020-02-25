package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.day.cq.commons.Externalizer;
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
import javax.jcr.Node;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
@Service(CoveoContentBuilder.class)
@Property(name = CoveoIndexConfiguration.PRIMARY_TYPE, value = PageContentBuilder.PRIMARY_TYPE_VALUE)
public class PageContentBuilder extends AbstractCoveoContentBuilder {

    @Reference
    ResourceResolverFactory resolverFactory;

    public static final String PRIMARY_TYPE_VALUE = "cq:Page";

    private static final Logger LOG = LoggerFactory.getLogger(PageContentBuilder.class);
    private static final String[] FIXED_RULES = {"cq:lastModified", "cq:template", "jcr:description", "jcr:title", "rep:policy"};

    @Override
    public IndexEntry create(String path, @Nonnull ResourceResolver resolver) {
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
                    ret.addContent(getProperties(res, indexRules));
                    ret.setDocumentId(documentId);
                    ret.addContent("title", page.getTitle());
                    ret.addContent("author", this.<String>getLastValue(res.getValueMap(), "jcr:createdBy"));
                    ret.addContent("lastmodified", page.getLastModified().getTimeInMillis());
                    ret.addContent("created", this.<Long>getLastValue(res.getValueMap(), "jcr:created"));

                    try {
                        ResourceResolver resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
                        Session adminSession = resourceResolver.adaptTo(Session.class);
                        UserManager userManager = resourceResolver.adaptTo(UserManager.class);

                        Node node = adminSession.getNode(path);

                        JsonObject policy = toJson(node).getAsJsonObject("rep:policy");
                        List<Permission> acls = getACLs(policy, userManager);
                        Type listType = new TypeToken<List<Permission>>() {
                        }.getType();
                        String aclJson = new Gson().toJson(acls, listType);
                        ret.addContent("acl", aclJson);
                    } catch (Exception ex) {
                        LOG.error("error policy", ex);
                    }

                    return ret;
                }
            }
        } else {
            LOG.warn("Could not load indexRules for " + PRIMARY_TYPE_VALUE);
        }

        return null;
    }

    @Override
    protected String[] getFixedRules() {
        return FIXED_RULES;
    }

}
