package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.Session;

import com.day.cq.commons.Externalizer;
import com.day.cq.dam.api.Asset;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.Permission;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;

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

    private static final String[] FIXED_RULES = { "dc:title", "dc:description", "jcr:created", "jcr:createdBy",
            "jcr:lastModified" };

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
                    Map<String, Object> allProperties = getAllProperties(res);
                    ret.addContent(getProperties(res, indexRules));

                    if (this.<String>getLastValue(allProperties, "dc:title") != null) {
                        ret.addContent("title", this.<String>getLastValue(allProperties, "dc:title"));
                    } else if (asset.getName() != null) {
                        ret.addContent("title", asset.getName());
                    }

                    if (this.<String>getLastValue(allProperties, "dc:description") != null) {
                        String description = this.<String>getLastValue(allProperties, "dc:description");
                        if (description == null) {
                            description = "";
                        }
                        ret.addContent("description", description);
                    }

                    if (MimeTypes.Text.isText(asset.getMimeType()) != null || MimeTypes.Image.isImage(asset.getMimeType()) != null){
                        InputStream is = asset.getOriginal().getStream();
                        if (is != null) {
                            String content = Base64.getEncoder().encodeToString(inputStreamToByteArray(is));
                            ret.addContent("content", content);
                        }
                    } else if (MimeTypes.Video.isVideo(asset.getMimeType()) != null) {
                        String data = getVideoData(documentId, asset.getMimeType(), ret.getContent("title", String.class), ret.getContent("description", String.class));

                        String content = Base64.getEncoder().encodeToString(data.getBytes());
                        ret.addContent("content", content);
                    }

                    ret.addContent("documenttype", MimeTypes.getType(asset.getMimeType()));

                    if (this.<String>getLastValue(res.getValueMap(), "jcr:createdBy") != null)
                        ret.addContent("author", this.<String>getLastValue(res.getValueMap(), "jcr:createdBy"));

                    if (this.<Long>getLastValue(res.getValueMap(), "jcr:created") != null)
                        ret.addContent("created", this.<Long>getLastValue(res.getValueMap(), "jcr:created"));

                    ret.addContent("lastmodified", asset.getLastModified());
                    ret.addContent("previewUrl", documentId);

                    if (MimeTypes.Video.isVideo(asset.getMimeType()) != null) {
                        Double videoDuration = this.<Double>getLastValue(allProperties, "videoDuration");

                        if (videoDuration != null)
                            ret.addContent("duration", videoDuration);
                    }

                    if (MimeTypes.Image.isImage(asset.getMimeType()) != null) {
                        Long width = this.<Long>getLastValue(allProperties, "tiff:ImageWidth");
                        Long height = this.<Long>getLastValue(allProperties, "tiff:ImageLength");

                        if (width != null)
                            ret.addContent("width", width);

                        if (height != null)
                            ret.addContent("height", height);
                    }

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

    /**
     * Generate a html content with a video player
     * 
     * @param url         URL of the video
     * @param mimeType    mimetype of video (i.e. mp4)
     * @param title       Title or filename
     * @param description description
     * @return html with a video player embed
     */
    private String getVideoData(String url, String mimeType, String title, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-16\"><base target=\"_blank\" href=\"")
                .append(url)
                .append("\"></head><body><div style=\"margin: 20px auto; width: 640px\"><div><video width=\"640\" controls><source src=\"")
                .append(url).append("\" type=\"").append(mimeType)
                .append("\">Your browser does not support the video tag.</video></div><div style=\"border: 1px solid #CCC; padding:10px; font-family: Helvetica\"><h3>")
                .append(title).append("</h3><p style=\"text-align: justify;\">").append(description)
                .append("</p></div></div></body></html>");
        return sb.toString();
    }
}
