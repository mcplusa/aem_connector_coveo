package com.mcplusa.coveo.connector.aem.indexing;

import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.replication.ContentBuilder;
import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationContent;
import com.day.cq.replication.ReplicationContentFactory;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import com.mcplusa.coveo.connector.aem.indexing.contentbuilder.CoveoContentBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * This Content-Builder is responsible to filter all relevant information from the activation event
 * into a ReplicationContent which is processed by {@link CoveoTransportHandler}.
 */
@Component(metatype = false)
@Service(ContentBuilder.class)
@Property(name = "name", value = CoveoIndexContentBuilder.NAME)
public class CoveoIndexContentBuilder implements ContentBuilder {

  private BundleContext context;

  @Reference private ResourceResolverFactory resolverFactory;

  /** Name of the Content Builder. */
  public static final String NAME = "coveo";
  /** Title of the Content Builder. */
  public static final String TITLE = "Coveo Index Content";

  @Activate
  public void activate(BundleContext context) {
    this.context = context;
  }

  @Override
  public ReplicationContent create(
      Session session, ReplicationAction action, ReplicationContentFactory factory)
      throws ReplicationException {
    return create(session, action, factory, null);
  }

  @Override
  public ReplicationContent create(
      Session session,
      ReplicationAction action,
      ReplicationContentFactory factory,
      Map<String, Object> map)
      throws ReplicationException {
    String path = action.getPath();

    ReplicationLog log = action.getLog();
    boolean includeContent = action.getType().equals(ReplicationActionType.ACTIVATE);

    if (StringUtils.isNotBlank(path)) {
      try {
        HashMap<String, Object> sessionMap = new HashMap<>();
        sessionMap.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
        ResourceResolver resolver = resolverFactory.getResourceResolver(sessionMap);

        Resource resource = resolver.getResource(path);
        if (resource != null) {
          String primaryType =
              resource.getValueMap().get(JcrConstants.JCR_PRIMARYTYPE, String.class);
          CoveoContentBuilder builder = getContentBuilder(primaryType, log);
          if (builder != null) {
            return createReplicationContent(
                factory, builder.create(path, resolver, includeContent));
          }
        } else {
          // Deleted content
          CoveoContentBuilder builder = getContentBuilder("dam:Asset", log);
          if (builder != null) {
            return createReplicationContent(factory, builder.createDeletedItem(path, resolver));
          }
        }
      } catch (LoginException e) {
        log.error("Could not retrieve the Session", e);
      }
    }
    log.info(getClass().getSimpleName() + ": Path is blank | path: " + path);
    return ReplicationContent.VOID;
  }

  /**
   * Looks up a ContentBuilder implementation for the given PrimaryType.
   *
   * @param primaryType content type.
   * @param log ReplicationLog.
   * @return CoveoIndexConfiguration or null if none found
   */
  private CoveoContentBuilder getContentBuilder(String primaryType, ReplicationLog log) {
    log.debug(getClass().getSimpleName() + ": getContentBuilder(): primaryType: " + primaryType);
    try {
      ServiceReference<?>[] serviceReferences =
          context.getServiceReferences(
              CoveoContentBuilder.class.getName(),
              "(" + CoveoIndexConfiguration.PRIMARY_TYPE + "=" + primaryType + ")");
      if (serviceReferences != null && serviceReferences.length > 0) {
        return (CoveoContentBuilder) context.getService(serviceReferences[0]);
      }
    } catch (InvalidSyntaxException | NullPointerException ex) {
      log.info(
          getClass().getSimpleName()
              + ": Could not load a CoveoContentBuilder for PrimaryType "
              + primaryType);
    }
    return null;
  }

  private ReplicationContent createReplicationContent(
      ReplicationContentFactory factory, IndexEntry content) throws ReplicationException {
    Path tempFile;

    try {
      tempFile = Files.createTempFile("coveo_index", ".tmp");
    } catch (IOException e) {
      throw new ReplicationException("Could not create temporary file", e);
    }

    try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
      ObjectMapper mapper = new ObjectMapper();
      writer.write(mapper.writeValueAsString(content));
      writer.flush();

      return factory.create("text/plain", tempFile.toFile(), true);
    } catch (IOException e) {
      throw new ReplicationException("Could not write to temporary file " + e.getMessage(), e);
    }
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return TITLE;
  }
}
