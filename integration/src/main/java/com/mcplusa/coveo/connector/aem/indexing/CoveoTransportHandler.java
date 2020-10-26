package com.mcplusa.coveo.connector.aem.indexing;

import com.day.cq.replication.Agent;
import com.day.cq.replication.AgentConfig;
import com.day.cq.replication.AgentManager;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationContent;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationLog;
import com.day.cq.replication.ReplicationQueue;
import com.day.cq.replication.ReplicationResult;
import com.day.cq.replication.ReplicationTransaction;
import com.day.cq.replication.TransportContext;
import com.day.cq.replication.TransportHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.service.CoveoQueueService;
import com.mcplusa.coveo.connector.aem.service.CoveoService;
import com.mcplusa.coveo.sdk.CoveoResponse;
import com.mcplusa.coveo.sdk.CoveoResponseException;
import com.mcplusa.coveo.sdk.pushapi.CoveoPushClient;
import com.mcplusa.coveo.sdk.pushapi.model.BatchRequest;
import com.mcplusa.coveo.sdk.pushapi.model.CompressionType;
import com.mcplusa.coveo.sdk.pushapi.model.Document;
import com.mcplusa.coveo.sdk.pushapi.model.FileContainerResponse;
import com.mcplusa.coveo.sdk.pushapi.model.IdentityModel;
import com.mcplusa.coveo.sdk.pushapi.model.IdentityType;
import com.mcplusa.coveo.sdk.pushapi.model.PermissionLevelsModel;
import com.mcplusa.coveo.sdk.pushapi.model.PermissionModel;
import com.mcplusa.coveo.sdk.pushapi.model.PermissionsSetsModel;
import com.mcplusa.coveo.sdk.pushapi.model.PushAPIStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpStatus;
import org.apache.sling.commons.json.JSONException;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TransportHandler Implementation that posts a ReplicationContent created by
 * {@link CoveoIndexContentBuilder} to a configured Coveo.
 */
@Service(TransportHandler.class)
@Component(label = "Coveo Index Agent", immediate = true, enabled = true)
@Properties(@Property(name = Constants.SERVICE_RANKING, intValue = 1000))
public class CoveoTransportHandler implements TransportHandler {

  @Reference
  protected AgentManager agentManager;

  @Reference
  protected CoveoQueueService coveoQueueService;

  @Reference
  protected CoveoService coveoService;

  private static final Logger LOG = LoggerFactory.getLogger(CoveoTransportHandler.class);
  private static final String REPLICATION_ERROR_MSG = "Replication failed";
  private static final String AGENT_NOT_FOUND = "The Agent can not be found. Check if agentId is configured properly in the Coveo Provider.";
  private static final String NO_REPLICATION_CONTENT = "No Replication Content provided";

  private static final String TITLE_FIELDNAME = "title";
  private static final String AUTHOR_FIELDNAME = "author";
  private static final String COMPRESSEDBINARYDATA_FIELDNAME = "CompressedBinaryData";
  private static final String COMPRESSEDBINARYDATAFILEID_FIELDNAME = "compressedBinaryDataFileId";
  private static final String DOCUMENT_TYPE_FIELDNAME = "documenttype";
  private static final String ACL_FIELDNAME = "acl";

  /**
   * Checks if Service will accept the replication.
   *
   * @param config AgentConfig
   * @return only accept if the serializationType is "coveo"
   */
  @Override
  public boolean canHandle(AgentConfig config) {
    return StringUtils.equalsIgnoreCase(
        config.getSerializationType(), CoveoIndexContentBuilder.NAME);
  }

  /**
   * Deliver the replication and return an ReplicationResult.
   *
   * @param ctx TransportContext
   * @param tx  ReplicationTransaction
   * @return ReplicationResult
   * @throws ReplicationException will add a log and skip the replication for this
   *                              item
   */
  @Override
  public ReplicationResult deliver(TransportContext ctx, ReplicationTransaction tx) throws ReplicationException {
    ReplicationLog log = tx.getLog();

    // Check if agent is configured.
    Agent agent = agentManager.getAgents().get(coveoQueueService.getAgentId());
    if (agent == null) {
      log.error(getClass().getSimpleName() + ": " + AGENT_NOT_FOUND + " current agentId: '"
          + coveoQueueService.getAgentId() + "'");
      LOG.error("{} current agentId: '{}'", AGENT_NOT_FOUND, coveoQueueService.getAgentId());
      return new ReplicationResult(false, 0,
          AGENT_NOT_FOUND + " current agentId: '" + coveoQueueService.getAgentId() + "'");
    }

    if (isFirstEntryOfBatch()) {
      updateSourceStatus(PushAPIStatus.REFRESH, log);
    }
    try {
      CoveoPushClient pushClient = coveoService.getClient();
      log.debug(getClass().getSimpleName() + ": Using host: " + pushClient.getHost() + " org: "
          + pushClient.getOrganizationId() + " source: " + pushClient.getSourceId());
      ReplicationActionType replicationType = tx.getAction().getType();
      log.info(getClass().getSimpleName() + ":" + replicationType + " " + tx.getAction().getPath());

      if (replicationType == ReplicationActionType.TEST) {
        return doTest(ctx, tx, pushClient);
      } else {
        log.info(getClass().getSimpleName() + ": ---------------------------------------");
        if ((tx.getContent() == ReplicationContent.VOID || tx.getContent() == null || tx.getContent().getContentType() == null)
            && (replicationType == null || replicationType != ReplicationActionType.DELETE)) {
          return doVoid(tx);
        }
        switch (replicationType) {
          case ACTIVATE:
            return doActivate(ctx, tx);
          case DELETE:
            return doDeactivate(ctx, tx);
          case DEACTIVATE:
            return doDeactivate(ctx, tx);
          default:
            String errorMsg = "Replication action type " + replicationType + " not supported.";
            log.warn(getClass().getSimpleName() + ": " + errorMsg);
            if (isLastEntryOfBatch()) {
              updateSourceStatus(PushAPIStatus.IDLE, log);
              pushToFileContainer(log);
            }
            throw new ReplicationException(errorMsg);
        }
      }
    } catch (JSONException jex) {
      updateSourceStatus(PushAPIStatus.IDLE, log);
      pushToFileContainer(log);

      log.error(getClass().getSimpleName() + ": JSON was invalid");
      LOG.error("JSON was invalid", jex);
      return new ReplicationResult(false, 0, jex.getLocalizedMessage());
    } catch (IOException ioe) {
      updateSourceStatus(PushAPIStatus.IDLE, log);
      pushToFileContainer(log);

      log.error(getClass().getSimpleName() + ": Could not perform Replication due to " + ioe.getLocalizedMessage());
      LOG.error("Could not perform Replication", ioe);
      return new ReplicationResult(false, 0, ioe.getLocalizedMessage());
    }
  }

  private ReplicationResult doVoid(ReplicationTransaction tx) {
    ReplicationLog log = tx.getLog();
    LOG.warn(NO_REPLICATION_CONTENT);

    if (isLastEntryOfBatch()) {
      CoveoResponse batchResponse = pushToFileContainer(log);
      updateSourceStatus(PushAPIStatus.IDLE, log);

      if (batchResponse != null && batchResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED) {
        return ReplicationResult.OK;
      } else {
        LOG.error("Could not push batch of documents: {}", batchResponse);
        return new ReplicationResult(false, 0, REPLICATION_ERROR_MSG);
      }

    } else {
      return new ReplicationResult(true, 0, NO_REPLICATION_CONTENT + " for path " + tx.getAction().getPath());
    }
  }

  private ReplicationResult doDeactivate(TransportContext ctx, ReplicationTransaction tx)
      throws JSONException, IOException {
    if (tx.getContent() == null || tx.getContent().getContentType() == null) {
      // file has no content or it is an unknown format, skip
      return ReplicationResult.OK;
    }

    ReplicationLog log = tx.getLog();
    log.info(getClass().getSimpleName() + ": Deleting... " + tx.getContent().getContentType());
    ObjectMapper mapper = new ObjectMapper();
    IndexEntry entry = null;
    
    try {
      entry = mapper.readValue(tx.getContent().getInputStream(), IndexEntry.class);
    } catch (Exception e) {
      LOG.error("Could not parse JSON", e);
    }

    if (entry != null) {
      Optional<ReplicationQueue> queue = this.getReplicationQueue();
      if (queue.isPresent()) {
        this.coveoQueueService.deleteDocument(queue.get().getName(), entry.getDocumentId());
      }

      if (isLastEntryOfBatch()) {
        updateSourceStatus(PushAPIStatus.IDLE, log);
        CoveoResponse batchResponse = pushToFileContainer(log);
        log.info(
            getClass().getSimpleName()
                + ": Delete Call returned "
                + batchResponse.getStatusLine().getStatusCode()
                + ": "
                + batchResponse.getStatusLine().getReasonPhrase());

        if (batchResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED) {
          return ReplicationResult.OK;
        } else {
          LOG.error("Could not delete {}", entry.getDocumentId());
          return new ReplicationResult(false, 0, REPLICATION_ERROR_MSG);
        }

      } else {
        log.info("File " + entry.getDocumentId() + " added to the batch.");
        return ReplicationResult.OK;
      }
    }

    updateSourceStatus(PushAPIStatus.IDLE, log);
    LOG.warn("File {} is already deleted", tx.getAction().getPath());
    return ReplicationResult.OK;
  }

  /**
   * Perform the replication. All logic is covered in
   * {@link CoveoIndexContentBuilder} so we only need to transmit the Document to
   * Coveo
   *
   * @param ctx TransportContext
   * @param tx  ReplicationTransaction
   * @return ReplicationResult
   * @throws ReplicationException if an error occurs.
   */
  private ReplicationResult doActivate(TransportContext ctx, ReplicationTransaction tx)
      throws ReplicationException, JSONException, IOException {
    ReplicationLog log = tx.getLog();
    ObjectMapper mapper = new ObjectMapper();
    IndexEntry entry = mapper.readValue(tx.getContent().getInputStream(), IndexEntry.class);
    if (entry != null) {
      Document document = indexEntryToDocument(entry);
      log.debug("Document: " + printDocument(document));
      log.info(getClass().getSimpleName() + ": Indexing " + document.getDocumentId());

      Optional<ReplicationQueue> queue = this.getReplicationQueue();
      if (queue.isPresent()) {
        this.coveoQueueService.addDocument(queue.get().getName(), document);
      }

      if (isLastEntryOfBatch()) {
        CoveoResponse batchResponse = pushToFileContainer(log);
        updateSourceStatus(PushAPIStatus.IDLE, log);

        if (batchResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED) {
          return ReplicationResult.OK;
        } else {
          LOG.error("Could not push batch of documents: {}", batchResponse);
          return new ReplicationResult(false, 0, REPLICATION_ERROR_MSG);
        }

      } else {
        log.info("File " + entry.getDocumentId() + " added to the batch.");
        return ReplicationResult.OK;
      }
    }

    updateSourceStatus(PushAPIStatus.IDLE, log);
    LOG.error("Could not replicate");
    return new ReplicationResult(false, 0, REPLICATION_ERROR_MSG);
  }

  /**
   * Test Connection to Coveo.
   *
   * @param ctx        TransportContext
   * @param tx         ReplicationTransaction
   * @param restClient CoveoPushClient
   * @return ReplicationResult
   * @throws ReplicationException if an error occurs.
   */
  private ReplicationResult doTest(TransportContext ctx, ReplicationTransaction tx, CoveoPushClient pushClient)
      throws IOException {
    ReplicationLog log = tx.getLog();
    CoveoResponse testResponse = pushClient.ping();
    log.info(getClass().getSimpleName() + ": ---------------------------------------");
    log.info(getClass().getSimpleName() + ": " + testResponse.getStatusLine().getStatusCode() + ": "
        + testResponse.getStatusLine().getReasonPhrase());
    if (testResponse.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
      log.info(getClass().getSimpleName() + ": The connection to Coveo has been successfully established.");
      return ReplicationResult.OK;
    } else {
      log.info(getClass().getSimpleName() + ": The connection could not be established to Coveo.");
      return new ReplicationResult(false, 0, "Replication test failed");
    }
  }

  private Document indexEntryToDocument(IndexEntry indexEntry) {
    String documentId = indexEntry.getContent("documentId", String.class);
    Document doc = new Document(documentId);

    if (indexEntry.getContent(TITLE_FIELDNAME, String.class) != null) {
      doc.setTitle(indexEntry.getContent(TITLE_FIELDNAME, String.class));
    }

    Map<String, Object> valuesMap = new HashMap<>();

    indexEntry.getContent().keySet().stream()
        .filter(key -> indexEntry.getContent().get(key) != null)
        .forEach(
            key -> {
              if (!key.equals("content")
                  && !key.equals("documentid")
                  && !key.equals(TITLE_FIELDNAME)
                  && !key.equals(ACL_FIELDNAME)) {
                valuesMap.put(cleanFieldName(key), indexEntry.getContent().get(key));
              }
            });
    doc.setMetadata(valuesMap);

    if (indexEntry.getContent("lastmodified", Long.class) != null) {
      doc.addMetadata("date", indexEntry.getContent("lastmodified", Long.class), Long.class);
    }

    if (indexEntry.getContent("created", Long.class) != null) {
      doc.addMetadata("createddate", indexEntry.getContent("created", Long.class), Long.class);
    }

    if (indexEntry.getContent(AUTHOR_FIELDNAME, String.class) != null) {
      doc.addMetadata(
          AUTHOR_FIELDNAME, indexEntry.getContent(AUTHOR_FIELDNAME, String.class), String.class);
    }

    Optional<String> extension = getExtension(documentId);
    if (extension.isPresent()) {
      doc.setFileExtension(extension.get());
    }

    if (StringUtils.isNotEmpty(indexEntry.getContent(DOCUMENT_TYPE_FIELDNAME, String.class))) {
      String documentType = indexEntry.getContent(DOCUMENT_TYPE_FIELDNAME, String.class);
      doc.addMetadata(DOCUMENT_TYPE_FIELDNAME, documentType, String.class);
      doc.addMetadata("filetype", documentType, String.class);
    }

    String data = indexEntry.getContent("content", String.class);
    if (data != null) {
      doc.setCompressionType(CompressionType.UNCOMPRESSED);
      doc.addMetadata(COMPRESSEDBINARYDATA_FIELDNAME, data, String.class);
    }

    String fileId = indexEntry.getContent("fileId", String.class);
    if (StringUtils.isNotEmpty(fileId)) {
      doc.setCompressionType(CompressionType.UNCOMPRESSED);
      doc.addMetadata(COMPRESSEDBINARYDATAFILEID_FIELDNAME, fileId, String.class);
    }

    // Implement permission
    String aclJson = indexEntry.getContent(ACL_FIELDNAME, String.class);
    if (aclJson != null) {
      List<PermissionModel> permissions = extractPermissions(aclJson);

      doc.setPermissions(permissions);

    } else {
      PermissionsSetsModel psm = new PermissionsSetsModel();
      psm.setAllowAnonymous(true);
      doc.setPermissions(Arrays.asList(psm));
    }

    return doc;
  }

  private List<PermissionModel> extractPermissions(String aclJson) {
    List<PermissionModel> permissions = new ArrayList<>();
    Type listType =
        new TypeToken<List<NodePermissionLevel>>() {
          private static final long serialVersionUID = -8061055707344967221L;
        }.getType();
    List<NodePermissionLevel> acl = new Gson().fromJson(aclJson, listType);

    if (acl == null) {
      return permissions;
    }

    List<NodePermissionLevel> aclOrdered =
        acl.stream()
            .sorted((a, b) -> a.getNodeLevel() - b.getNodeLevel())
            .collect(Collectors.toList());
    for (NodePermissionLevel nodePermission : aclOrdered) {
      PermissionLevelsModel permissionLevel =
          new PermissionLevelsModel("Permission Level " + (nodePermission.getNodeLevel() + 1));

      PermissionsSetsModel psm = new PermissionsSetsModel();
      for (Permission permission : nodePermission.getPermissions()) {
        IdentityType identityType = IdentityType.USER;
        String identityProvider = this.coveoService.getUserIdentityProvider();
        if (permission.isGroup()) {
          identityType = IdentityType.VIRTUAL_GROUP;
          identityProvider = this.coveoService.getGroupIdentityProvider();
        }

        IdentityModel identity =
            new IdentityModel(permission.getPrincipalName(), identityType, identityProvider);

        if (permission.getType() == Permission.PERMISSION_TYPE.ALLOW) {
          psm.addAllowedPermission(identity);
        } else {
          psm.addDeniedPermission(identity);
        }
      }

      psm.setAllowAnonymous(false);

      permissionLevel.addPermissionSet(psm);
      permissions.add(permissionLevel);
    }

    if (permissions.isEmpty()) {
      PermissionLevelsModel permissionLevel = new PermissionLevelsModel("Permission Level 1");
      PermissionsSetsModel psm = new PermissionsSetsModel();
      psm.setAllowAnonymous(true);
      permissionLevel.addPermissionSet(psm);
      permissions.add(permissionLevel);
    }

    return permissions;
  }

  /**
   * Extract the extension from URI.
   *
   * @param uri URI that should contain the file extension
   * @return Optional that contains the extension, it will be empty if the URI
   *         doesn't contains an extension
   */
  private Optional<String> getExtension(String uri) {
    int extensionIndex = uri.lastIndexOf('.');
    if (extensionIndex > 0) {
      return Optional.of(uri.substring(extensionIndex));
    }

    return Optional.empty();
  }

  /**
   * Removes all special characters, only allow lowercase letters (a-z), numbers
   * (0-9), and underscores.
   *
   * @param fieldName value to be "cleaned"
   * @return a valid field name for Coveo
   */
  private String cleanFieldName(String fieldName) {
    String regexWhitespaces = "\\s+";
    String regexSpecialCharacters = "\\W";
    return fieldName
        .replaceAll(regexWhitespaces, "_")
        .replaceAll(regexSpecialCharacters, "")
        .toLowerCase();
  }

  /**
   * Perform the source status update. it adds logs for both successful and error
   * responses.
   *
   * @param status PushAPIStatus
   * @param log    ReplicationLog
   */
  private void updateSourceStatus(PushAPIStatus status, ReplicationLog log) {
    CoveoPushClient pushClient = coveoService.getClient();
    try {
      CoveoResponse updateResponse = pushClient.updateSourceStatus(status);
      if (updateResponse.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
        log.debug(getClass().getSimpleName() + ": Source Status updated to " + status.toString());
      } else {
        log.error(getClass().getSimpleName() + ": Could not update the Source status to " + status.toString());
        LOG.error("Could not update the Source status to {}", status);
      }
    } catch (IOException e) {
      log.error(
          getClass().getSimpleName()
              + ": Exception: Could not update the Source status to "
              + status.toString());
      LOG.error("Exception: Could not update the Source status to " + status.toString(), e);
    }
  }

  private String printDocument(Document doc) {
    Gson gson = new Gson();
    JsonObject document = gson.fromJson(doc.toJson(), JsonObject.class);
    JsonElement data = document.get(COMPRESSEDBINARYDATA_FIELDNAME);
    if (data != null && data.getAsString().length() > 1000) {
      document.addProperty(COMPRESSEDBINARYDATA_FIELDNAME, data.getAsString().substring(0, 1000));
    }

    return document.toString();
  }

  private Optional<ReplicationQueue> getReplicationQueue() {
    Agent agent = agentManager.getAgents().get(coveoQueueService.getAgentId());
    if (agent != null && agent.getQueue() != null) {
      ReplicationQueue queue = agent.getQueue();

      return Optional.ofNullable(queue);
    }

    return Optional.empty();
  }

  /**
   * Check if is the first entry of the queue.
   *
   * @return true if is the first entry.
   */
  private boolean isFirstEntryOfBatch() {
    Optional<ReplicationQueue> queue = getReplicationQueue();
    if (queue.isPresent()) {
      Map<String, BatchRequest> queueMap = coveoQueueService.getQueueMap();

      // Check if the queue exist
      if (queueMap != null && queueMap.containsKey(queue.get().getName())) {
        return false;
      } else if (queueMap != null) {
        queueMap.put(queue.get().getName(), new BatchRequest());
        return true;
      }
    }

    return false;
  }

  /**
   * Check if is the last entry of the queue.
   *
   * @return true if its the last entry
   */
  private boolean isLastEntryOfBatch() {
    Optional<ReplicationQueue> queue = getReplicationQueue();
    if (queue.isPresent()) {
      Map<String, BatchRequest> queueMap = coveoQueueService.getQueueMap();

      // If it the last item, remove it from the Map and return true
      if (queueMap != null && queue.get().entries().size() <= 1) {
        return true;
      }
    }

    return false;
  }

  /**
   * Get a FileContainer and push the batch file to the S3 instance.
   *
   * @param batchRequest contains the list of documents to add/update/delete.
   * @return the fileId of the FileContainer.
   */
  private CoveoResponse pushToFileContainer(ReplicationLog log) {
    Optional<ReplicationQueue> queue = getReplicationQueue();
    if (queue.isPresent()) {
      BatchRequest batchRequest = this.coveoQueueService.getQueueMap().get(queue.get().getName());
      int addOrUpdateSize = batchRequest.getAddOrUpdate().size();
      int deleteSize = batchRequest.getDelete().size();
      log.debug("Batch: Add size " + addOrUpdateSize + " | Delete size " + deleteSize);

      Gson gson = new GsonBuilder().disableHtmlEscaping().create();
      String json = gson.toJson(batchRequest);

      try (InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {

        CoveoPushClient client = coveoService.getClient();
        FileContainerResponse fileContainer = client.getFileContainer();

        CoveoResponse response = client.pushFileOnS3(is, fileContainer.getUploadUri());
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
          log.info("Batch of files pushed");
          this.coveoQueueService.getQueueMap().remove(queue.get().getName());
          return this.coveoService.getClient().pushDocumentsBatch(fileContainer.getFileId());
        }

        throw new CoveoResponseException(response);
      } catch (Exception e) {
        LOG.error("Could not push the data to the file Container", e);
      }
    }

    return null;
  }
}
