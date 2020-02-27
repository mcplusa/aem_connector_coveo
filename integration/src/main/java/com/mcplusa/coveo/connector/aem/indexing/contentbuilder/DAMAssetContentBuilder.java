package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.day.cq.commons.Externalizer;
import com.day.cq.dam.api.Asset;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.Permission;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.List;
import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.Session;
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

/**
 * Content Builder for DAM Assets
 */
@Component(immediate = true)
@Service(CoveoContentBuilder.class)
@Property(name = CoveoIndexConfiguration.PRIMARY_TYPE, value = DAMAssetContentBuilder.PRIMARY_TYPE_VALUE)
public class DAMAssetContentBuilder extends AbstractCoveoContentBuilder {

    @Reference
    ResourceResolverFactory resolverFactory;

    private static final Logger LOG = LoggerFactory.getLogger(DAMAssetContentBuilder.class);
    public static final String PRIMARY_TYPE_VALUE = "dam:Asset";

    private static final String[] FIXED_RULES = {"dc:title", "dc:description", "jcr:created", "jcr:createdBy", "jcr:lastModified"};

    @Override
    public IndexEntry create(String path, @Nonnull ResourceResolver resolver) {
        String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);
        if (ArrayUtils.isNotEmpty(indexRules)) {
            Externalizer externalizer = resolver.adaptTo(Externalizer.class);
            Resource res = resolver.getResource(path);
            if (res != null) {
                Asset asset = res.adaptTo(Asset.class);
                if (asset != null) {
                    String documentId = path;
                    if (externalizer != null) {
                        documentId = externalizer.publishLink(resolver, path);
                    }
                    IndexEntry ret = new IndexEntry("idx", "asset", path);
                    ret.addContent(getProperties(res, indexRules));
                    ret.addContent("everythingkeys", res.getValueMap().keySet());

                    InputStream is = asset.getOriginal().getStream();
                    if (is != null) {
                        String content = Base64.getEncoder().encodeToString(inputStreamToByteArray(is));
                        ret.addContent("content", content);
                    }

                    ret.addContent("title", asset.getName());
                    ret.addContent("author", this.<String>getLastValue(res.getValueMap(), "jcr:createdBy"));
                    ret.addContent("lastmodified", asset.getLastModified());
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

                    ret.setDocumentId(documentId);
                    return ret;
                }
                LOG.error("Could not adapt asset");
            }
        }
        return null;
    }

    @Override
    protected String[] getFixedRules() {
        return FIXED_RULES;
    }

    private byte[] inputStreamToByteArray(InputStream is) {
        ByteArrayOutputStream buffer = null;
        try {
            buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            return buffer.toByteArray();
        } catch (IOException ex) {
            LOG.error("Error trying to convert input stream to byte[]", ex);
            return null;
        } finally {
            try {
                is.close();
                if (buffer != null) {
                    buffer.close();
                }
            } catch (IOException ex) {
                LOG.error("Error closing inputstream", ex);
            }
        }
    }
}
