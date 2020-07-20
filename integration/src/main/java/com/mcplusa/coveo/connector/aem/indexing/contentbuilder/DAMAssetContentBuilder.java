package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.day.cq.commons.Externalizer;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.tagging.TagManager;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.NodePermissionLevel;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import com.mcplusa.coveo.connector.aem.service.CoveoService;
import com.mcplusa.coveo.sdk.CoveoResponse;
import com.mcplusa.coveo.sdk.pushapi.CoveoPushClient;
import com.mcplusa.coveo.sdk.pushapi.model.FileContainerResponse;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.Session;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpStatus;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Content Builder for DAM Assets. */
@Component(immediate = true)
@Service(CoveoContentBuilder.class)
@Property(
    name = CoveoIndexConfiguration.PRIMARY_TYPE,
    value = DAMAssetContentBuilder.PRIMARY_TYPE_VALUE)
public class DAMAssetContentBuilder extends AbstractCoveoContentBuilder {

  @Reference
  ResourceResolverFactory resolverFactory;

  @Reference
  protected CoveoService coveoService;

  private static final Logger LOG = LoggerFactory.getLogger(DAMAssetContentBuilder.class);
  private static final String TITLE_FIELDNAME = "dc:title";
  private static final String DESCRIPTION_FIELDNAME = "dc:description";
  private static final String CREATED_FIELDNAME = "jcr:created";
  private static final String AUTHOR_FIELDNAME = "jcr:createdBy";
  private static final String MODIFIED_FIELDNAME = "jcr:lastModified";
  public static final String PRIMARY_TYPE_VALUE = "dam:Asset";

  private static final String[] FIXED_RULES = {
    TITLE_FIELDNAME, DESCRIPTION_FIELDNAME, CREATED_FIELDNAME, AUTHOR_FIELDNAME, MODIFIED_FIELDNAME
  };

  @Override
  public IndexEntry create(
      String path, @Nonnull ResourceResolver resolver, boolean includeContent) {
    String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);
    if (ArrayUtils.isNotEmpty(indexRules)) {
      Externalizer externalizer = resolver.adaptTo(Externalizer.class);
      Resource res = resolver.getResource(path);
      this.permissionPolicy = this.coveoService.getPermissionPolicy();
      setTagManager(resolver.adaptTo(TagManager.class));
      if (res != null) {
        Asset asset = res.adaptTo(Asset.class);
        if (asset != null) {
          String documentId = path;
          if (externalizer != null) {
            documentId = externalizer.publishLink(resolver, path);
          }
          IndexEntry ret = new IndexEntry("idx", "asset", path);
          ret.setDocumentId(documentId);

          if (includeContent) {
            ret.addContent(getDocumentContent(resolver, res, asset, path, documentId));
          }

          return ret;
        }
        LOG.error("Could not adapt asset");
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
    IndexEntry ret = new IndexEntry("idx", "asset", path);
    ret.setDocumentId(documentId);

    return ret;
  }

  private Map<String, Object> getDocumentContent(
      ResourceResolver resolver, Resource res, Asset asset, String path, String documentId) {
    Map<String, Object> mapContent = new HashMap<>();
    String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);

    Map<String, Object> allProperties = getAllProperties(res);
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

    if (this.getLastValue(allProperties, TITLE_FIELDNAME, String.class) != null) {
      String title = this.getLastValue(allProperties, TITLE_FIELDNAME, String.class);
      mapContent.put("title", title);
    } else if (asset.getName() != null) {
      mapContent.put("title", asset.getName());
    }

    if (this.getLastValue(allProperties, DESCRIPTION_FIELDNAME, String.class) != null) {
      String desc = this.getLastValue(allProperties, DESCRIPTION_FIELDNAME, String.class);
      if (desc == null || desc.equals("null")) {
        desc = "";
      }
      mapContent.put("description", desc);
    }

    if (MimeTypes.Text.isText(asset.getMimeType()) != null
        || MimeTypes.Image.isImage(asset.getMimeType()) != null
        || MimeTypes.getType(asset.getMimeType()).isEmpty()) {
      Rendition original = asset.getOriginal();
      if (original != null) {
        InputStream is = original.getStream();

        if (is != null) {
          String content = pushToFileContainer(is);
          mapContent.put("fileId", content);
        }
      }

    } else if (MimeTypes.Video.isVideo(asset.getMimeType()) != null) {
      String data =
          getVideoData(
              documentId,
              asset.getMimeType(),
              (String) mapContent.get("title"),
              (String) mapContent.get("description"));

      String content = Base64.getEncoder().encodeToString(data.getBytes());
      mapContent.put("content", content);
    }

    if (StringUtils.isNotEmpty(MimeTypes.getType(asset.getMimeType()))) {
      mapContent.put("documenttype", MimeTypes.getType(asset.getMimeType()));
    }

    if (this.getLastValue(res.getValueMap(), AUTHOR_FIELDNAME, String.class) != null) {
      String author = this.getLastValue(res.getValueMap(), AUTHOR_FIELDNAME, String.class);
      mapContent.put("author", author);
    }

    if (this.getLastValue(res.getValueMap(), CREATED_FIELDNAME, GregorianCalendar.class) != null) {
      GregorianCalendar created = this.getLastValue(res.getValueMap(), CREATED_FIELDNAME, GregorianCalendar.class);
      mapContent.put("created", created);
    }

    mapContent.put("lastmodified", asset.getLastModified());
    mapContent.put("previewUrl", documentId);

    if (MimeTypes.Video.isVideo(asset.getMimeType()) != null) {
      Long videoDuration = this.getLastValue(allProperties, "videoDuration", Long.class);

      if (videoDuration != null) {
        mapContent.put("duration", videoDuration);
      }
    }

    if (MimeTypes.Image.isImage(asset.getMimeType()) != null) {
      Long width = this.getLastValue(allProperties, "tiff:ImageWidth", Long.class);
      Long height = this.getLastValue(allProperties, "tiff:ImageLength", Long.class);

      if (width != null) {
        mapContent.put("width", width);
      }

      if (height != null) {
        mapContent.put("height", height);
      }
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
            private static final long serialVersionUID = 8901050930521733762L;
          }.getType();
      String aclJson = new Gson().toJson(permLevels, listType);

      mapContent.put("acl", aclJson);
    } catch (Exception ex) {
      LOG.error("Error getting Permissions for asset " + path, ex);
    } finally {
      if (resourceResolver != null && resourceResolver.isLive()) {
        resourceResolver.close();
      }
    }

    return mapContent;
  }

  @Override
  protected String[] getFixedRules() {
    return FIXED_RULES;
  }

  /**
   * Generate a html content with a video player.
   *
   * @param url URL of the video
   * @param mimeType mimetype of video (i.e. mp4)
   * @param title Title or filename
   * @param description description
   * @return html with a video player embed
   */
  private String getVideoData(String url, String mimeType, String title, String description) {
    StringBuilder sb = new StringBuilder();
    sb.append(
            "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-16\"><base target=\"_blank\" href=\"")
        .append(url)
        .append(
            "\"></head><body><div style=\"margin: 20px auto; width: 640px\"><div><video width=\"640\" controls><source src=\"")
        .append(url)
        .append("\" type=\"")
        .append(mimeType)
        .append(
            "\">Your browser does not support the video tag.</video></div><div style=\"border: 1px solid #CCC; padding:10px; font-family: Helvetica\"><h3>")
        .append(title)
        .append("</h3><p style=\"text-align: justify;\">")
        .append(description != null ? description : "")
        .append("</p></div></div></body></html>");
    return sb.toString();
  }

  /**
   * Get a FileContainer and push the data to the S3 instance.
   *
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
