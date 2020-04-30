package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Session;

import com.day.cq.commons.Externalizer;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.tagging.TagManager;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.NodePermissionLevel;
import com.mcplusa.coveo.connector.aem.indexing.Permission;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import com.mcplusa.coveo.connector.aem.service.CoveoService;
import com.mcplusa.coveo.sdk.CoveoResponse;
import com.mcplusa.coveo.sdk.pushapi.CoveoPushClient;
import com.mcplusa.coveo.sdk.pushapi.model.FileContainerResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpStatus;
import org.apache.jackrabbit.api.security.user.Authorizable;
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

    @Reference
    protected CoveoService coveoService;

    private static final Logger LOG = LoggerFactory.getLogger(DAMAssetContentBuilder.class);
    public static final String PRIMARY_TYPE_VALUE = "dam:Asset";

    private static final String[] FIXED_RULES = { "dc:title", "dc:description", "jcr:created", "jcr:createdBy",
            "jcr:lastModified" };

    @Override
    public IndexEntry create(String path, @Nonnull ResourceResolver resolver, boolean includeContent) {
        String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);
        if (ArrayUtils.isNotEmpty(indexRules)) {
            Externalizer externalizer = resolver.adaptTo(Externalizer.class);
            Resource res = resolver.getResource(path);
            setTagManager(resolver.adaptTo(TagManager.class));
            if (res != null) {
                Asset asset = res.adaptTo(Asset.class);
                if (asset != null) {
                    String documentId = path;
                    if (externalizer != null) {
                        documentId = externalizer.publishLink(resolver, path);
                    }
                    IndexEntry ret = new IndexEntry("idx", "asset", path);

                    if (includeContent) {
                        Map<String, Object> allProperties = getAllProperties(res);
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

                        if (this.<String>getLastValue(allProperties, "dc:title") != null) {
                            ret.addContent("title", this.<String>getLastValue(allProperties, "dc:title"));
                        } else if (asset.getName() != null) {
                            ret.addContent("title", asset.getName());
                        }

                        if (this.<String>getLastValue(allProperties, "dc:description") != null) {
                            String description = this.<String>getLastValue(allProperties, "dc:description");
                            if (description == null || description.equals("null")) {
                                description = "";
                            }
                            ret.addContent("description", description);
                        }

                        if (MimeTypes.Text.isText(asset.getMimeType()) != null
                                || MimeTypes.Image.isImage(asset.getMimeType()) != null
                                || MimeTypes.getType(asset.getMimeType()).isEmpty()) {
                            Rendition original = asset.getOriginal();
                            if (original != null) {
                                InputStream is = original.getStream();

                                if (is != null) {
                                    String content = pushToFileContainer(is);
                                    ret.addContent("fileId", content);
                                }
                            }

                        } else if (MimeTypes.Video.isVideo(asset.getMimeType()) != null) {
                            String data = getVideoData(documentId, asset.getMimeType(), ret.getContent("title", String.class), ret.getContent("description", String.class));

                            String content = Base64.getEncoder().encodeToString(data.getBytes());
                            ret.addContent("content", content);
                        }

                        if (StringUtils.isNotEmpty(MimeTypes.getType(asset.getMimeType()))) {
                            ret.addContent("documenttype", MimeTypes.getType(asset.getMimeType()));
                        }

                        if (this.<String>getLastValue(res.getValueMap(), "jcr:createdBy") != null) {
                            ret.addContent("author", this.<String>getLastValue(res.getValueMap(), "jcr:createdBy"));
                        }

                        if (this.<Long>getLastValue(res.getValueMap(), "jcr:created") != null) {
                            ret.addContent("created", this.<Long>getLastValue(res.getValueMap(), "jcr:created"));
                        }

                        ret.addContent("lastmodified", asset.getLastModified());
                        ret.addContent("previewUrl", documentId);

                        if (MimeTypes.Video.isVideo(asset.getMimeType()) != null) {
                            Long videoDuration = this.<Long>getLastValue(allProperties, "videoDuration");

                            if (videoDuration != null) {
                                ret.addContent("duration", videoDuration);
                            }
                        }

                        if (MimeTypes.Image.isImage(asset.getMimeType()) != null) {
                            Long width = this.<Long>getLastValue(allProperties, "tiff:ImageWidth");
                            Long height = this.<Long>getLastValue(allProperties, "tiff:ImageLength");

                            if (width != null) {
                                ret.addContent("width", width);
                            }

                            if (height != null) {
                                ret.addContent("height", height);
                            }
                        }

                        // Retrieve ACLs from policy
                        try {
                            List<NodePermissionLevel> permissionLevels = new ArrayList<>();
                            ResourceResolver resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
                            Session adminSession = resourceResolver.adaptTo(Session.class);
                            UserManager userManager = resourceResolver.adaptTo(UserManager.class);
                            List<Authorizable> authorizables = getAllAuthorizables(userManager);

                            Node node = adminSession.getNode(path);
                            int nodeLevel = 0;

                            while (node != null) {
                                Set<Permission> permissions = new HashSet<>();
                                if (node.hasNode("rep:policy")) {
                                  JsonObject policy = toJson(node.getNode("rep:policy"));
                                  if (policy != null) {
                                      List<Permission> acls = getACLs(policy, authorizables);
                                      if (acls != null && !acls.isEmpty()) {
                                          permissions.addAll(acls);
                                      }
                                  }
                                }

                                if (node.hasNode("rep:cugPolicy")) {
                                  JsonObject cugPolicy = toJson(node.getNode("rep:cugPolicy"));
                                  if (cugPolicy != null) {
                                      List<Permission> cugAcls = getCugACLs(cugPolicy.getAsJsonArray("rep:principalNames"), authorizables);
                                      if (cugAcls != null && !cugAcls.isEmpty()) {
                                          permissions.addAll(cugAcls);
                                      }
                                  }
                                }

                                if (permissions.size() > 0) {
                                    List<Permission> nonDuplicatedPermissions = new ArrayList<>();
                                    nonDuplicatedPermissions.addAll(permissions);
                                    permissionLevels.add(new NodePermissionLevel(nodeLevel, nonDuplicatedPermissions));
                                    nodeLevel++;
                                }

                                try {
                                    node = node.getParent();
                                    if (node.getPath().equals("/") || node.getPath().equals("/content")) {
                                        node = null;
                                    }
                                } catch (ItemNotFoundException e) {
                                    node = null;
                                }
                            }

                            Type listType = new TypeToken<List<NodePermissionLevel>>() {
                            }.getType();
                            String aclJson = new Gson().toJson(permissionLevels, listType);

                            ret.addContent("acl", aclJson);
                        } catch (Exception ex) {
                            LOG.error("error policy", ex);
                        }

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
                .append(title).append("</h3><p style=\"text-align: justify;\">")
                .append(description != null ? description : "").append("</p></div></div></body></html>");
        return sb.toString();
    }

    /**
     * Get a FileContainer and push the data to the S3 instance.
     * @param is InputStream of the document.
     * @return the fileId of the FileContainer.
     */
    private String pushToFileContainer(InputStream is) {
        try {
            CoveoPushClient client = coveoService.getClient();
            FileContainerResponse fileContainer = client.getFileContainer();

            CoveoResponse response = client.pushFileOnS3(is, fileContainer.getUploadUri());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                return fileContainer.getFileId();
            }
        } catch (Exception e) {
            LOG.error("Could not push the data to the file Container", e);
        }

        return null;
    }
}
