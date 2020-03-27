package com.mcplusa.coveo.connector.aem.indexing;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.day.cq.replication.AgentConfig;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationContent;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationLog;
import com.day.cq.replication.ReplicationResult;
import com.day.cq.replication.ReplicationTransaction;
import com.day.cq.replication.TransportContext;
import com.day.cq.replication.TransportHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.service.CoveoService;
import com.mcplusa.coveo.sdk.CoveoResponse;
import com.mcplusa.coveo.sdk.pushapi.CoveoPushClient;
import com.mcplusa.coveo.sdk.pushapi.model.CompressionType;
import com.mcplusa.coveo.sdk.pushapi.model.Document;
import com.mcplusa.coveo.sdk.pushapi.model.IdentityModel;
import com.mcplusa.coveo.sdk.pushapi.model.IdentityType;
import com.mcplusa.coveo.sdk.pushapi.model.PermissionsSetsModel;

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
@Properties(
        @Property(name = Constants.SERVICE_RANKING, intValue = 1000))
public class CoveoTransportHandler implements TransportHandler {

    @Reference
    protected CoveoService coveoService;

    private static final Logger LOG = LoggerFactory.getLogger(CoveoTransportHandler.class);

    /**
     *
     * @param config AgentConfig
     * @return only accept if the serializationType is "coveo"
     */
    @Override
    public boolean canHandle(AgentConfig config) {
        return StringUtils.equalsIgnoreCase(config.getSerializationType(), CoveoIndexContentBuilder.NAME);
    }

    /**
     *
     * @param ctx TransportContext
     * @param tx ReplicationTransaction
     * @return ReplicationResult
     * @throws ReplicationException will add a log and skip the replication for this item
     */
    @Override
    public ReplicationResult deliver(TransportContext ctx, ReplicationTransaction tx) throws ReplicationException {
        ReplicationLog log = tx.getLog();
        try {
            CoveoPushClient pushClient = coveoService.getClient();
            log.debug(getClass().getSimpleName() + ": Using host: " + pushClient.getHost() + " org: " + pushClient.getOrganizationId() + " source: " + pushClient.getSourceId());
            ReplicationActionType replicationType = tx.getAction().getType();
            log.debug(getClass().getSimpleName() + ": replicationType " + replicationType.toString());
            if (replicationType == ReplicationActionType.TEST) {
                return doTest(ctx, tx, pushClient);
            } else {
                log.info(getClass().getSimpleName() + ": ---------------------------------------");
                if (tx.getContent() == ReplicationContent.VOID) {
                    LOG.warn("No Replication Content provided");
                    return new ReplicationResult(true, 0, "No Replication Content provided for path " + tx.getAction().getPath());
                }
                switch (replicationType) {
                    case ACTIVATE:
                        return doActivate(ctx, tx, pushClient);
                    case DELETE:
                    case DEACTIVATE:
                        return doDeactivate(ctx, tx, pushClient);
                    default:
                        log.warn(getClass().getSimpleName() + ": Replication action type" + replicationType + " not supported.");
                        throw new ReplicationException("Replication action type " + replicationType + " not supported.");
                }
            }
        } catch (JSONException jex) {
            LOG.error("JSON was invalid", jex);
            return new ReplicationResult(false, 0, jex.getLocalizedMessage());
        } catch (IOException ioe) {
            log.error(getClass().getSimpleName() + ": Could not perform Indexing due to " + ioe.getLocalizedMessage());
            LOG.error("Could not perform Indexing", ioe);
            return new ReplicationResult(false, 0, ioe.getLocalizedMessage());
        }
    }

    private ReplicationResult doDeactivate(TransportContext ctx, ReplicationTransaction tx, CoveoPushClient pushClient) throws ReplicationException, JSONException, IOException {
        ReplicationLog log = tx.getLog();
        log.info(getClass().getSimpleName() + ": Deleting... " + tx.getContent().getContentType());
        ObjectMapper mapper = new ObjectMapper();
        IndexEntry entry = mapper.readValue(tx.getContent().getInputStream(), IndexEntry.class);
        if (entry != null) {
            CoveoResponse deleteResponse = pushClient.deleteDocument(entry.getDocumentId());

            LOG.debug("Response: {}", deleteResponse);
            log.info(getClass().getSimpleName() + ": Delete Call returned " + deleteResponse.getStatusLine().getStatusCode() + ": " + deleteResponse.getStatusLine().getReasonPhrase());
            if (deleteResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED) {
                return ReplicationResult.OK;
            } else {
                LOG.error("Could not delete {}", entry.getDocumentId());
                return new ReplicationResult(false, 0, "Replication failed");
            }
        }

        LOG.error("Could not delete");
        return new ReplicationResult(false, 0, "Replication failed");
    }

    /**
     * Perform the replication. All logic is covered in
     * {@link CoveoIndexContentBuilder} so we only need to transmit the Document
     * to Coveo
     *
     * @param ctx TransportContext
     * @param tx ReplicationTransaction
     * @param restClient CoveoPushClient
     * @return ReplicationResult
     * @throws ReplicationException
     */
    private ReplicationResult doActivate(TransportContext ctx, ReplicationTransaction tx, CoveoPushClient pushClient) throws ReplicationException, JSONException, IOException {
        ReplicationLog log = tx.getLog();
        ObjectMapper mapper = new ObjectMapper();
        IndexEntry entry = mapper.readValue(tx.getContent().getInputStream(), IndexEntry.class);
        if (entry != null) {
            Document document = indexEntryToDocument(entry);
            log.debug("Document: " + printDocument(document));
            log.info(getClass().getSimpleName() + ": Indexing " + document.getDocumentId());

            CoveoResponse indexResponse = pushClient.pushSingleDocument(document);
            LOG.debug(indexResponse.toString());
            log.info(getClass().getSimpleName() + ": " + indexResponse.getStatusLine().getStatusCode() + ": " + indexResponse.getStatusLine().getReasonPhrase());
            if (indexResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED) {
                return ReplicationResult.OK;
            }
        }

        LOG.error("Could not replicate");
        return new ReplicationResult(false, 0, "Replication failed");
    }

    /**
     * Test Connection to Coveo
     *
     * @param ctx TransportContext
     * @param tx ReplicationTransaction
     * @param restClient CoveoPushClient
     * @return ReplicationResult
     * @throws ReplicationException
     */
    private ReplicationResult doTest(TransportContext ctx, ReplicationTransaction tx, CoveoPushClient pushClient) throws ReplicationException, IOException {
        ReplicationLog log = tx.getLog();
        CoveoResponse testResponse = pushClient.ping();
        log.info(getClass().getSimpleName() + ": ---------------------------------------");
        log.info(getClass().getSimpleName() + ": " + testResponse.getStatusLine().getStatusCode() + ": " + testResponse.getStatusLine().getReasonPhrase());

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

        if (indexEntry.getContent("title", String.class) != null)
            doc.setTitle(indexEntry.getContent("title", String.class));

        Map<String, Object> valuesMap = new HashMap<>();

        indexEntry.getContent().keySet().stream()
                .filter(key -> indexEntry.getContent().get(key) != null)
                .forEach(key -> {
                    if (!key.equals("content") && !key.equals("documentid") && !key.equals("title")
                            && !key.equals("acl")) {
                        valuesMap.put(cleanFieldName(key), indexEntry.getContent().get(key));
                    }
                });
        doc.setMetadata(valuesMap);

        if (indexEntry.getContent("lastmodified", Long.class) != null)
            doc.addMetadata("date", indexEntry.getContent("lastmodified", Long.class), Long.class);
        
        if (indexEntry.getContent("created", Long.class) != null)
            doc.addMetadata("createddate", indexEntry.getContent("created", Long.class), Long.class);

        if (indexEntry.getContent("author", String.class) != null)
            doc.addMetadata("author", indexEntry.getContent("author", String.class), String.class);

        Optional<String> extension = getExtension(documentId);
        if (extension.isPresent()) {
            doc.setFileExtension(extension.get());
        }

        if (indexEntry.getContent("documenttype", String.class) != null) {
            doc.addMetadata("documenttype", indexEntry.getContent("documenttype", String.class), String.class);
            doc.addMetadata("filetype", indexEntry.getContent("documenttype", String.class), String.class);
        }

        String data = indexEntry.getContent("content", String.class);
        if (data != null) {
            doc.setCompressionType(CompressionType.UNCOMPRESSED);
            doc.addMetadata("CompressedBinaryData", data, String.class);
        }

        // Implement permission
        PermissionsSetsModel psm = new PermissionsSetsModel();
        String aclJson = indexEntry.getContent("acl", String.class);
        if (aclJson != null) {
            Type listType = new TypeToken<List<Permission>>() {
            }.getType();
            List<Permission> acl = new Gson().fromJson(aclJson, listType);
            if (acl != null) {
                for (Permission p : acl) {
                    IdentityType identityType = p.isGroup() ? IdentityType.GROUP : IdentityType.USER;
    
                    if (p.getType() == Permission.PERMISSION_TYPE.ALLOW) {
                        psm.addAllowedPermission(new IdentityModel(identityType, p.getPrincipalName()));
                    } else {
                        psm.addDeniedPermission(new IdentityModel(identityType, p.getPrincipalName()));
                    }
                }
            }

            psm.setAllowAnonymous(acl == null || acl.isEmpty());

            doc.setPermissions(Arrays.asList(psm));
        }

        return doc;
    }

    /**
     * Extract the extension from URI
     *
     * @param uri
     * @return Optional<String> that contains the extension, it will be empty if
     * the URI doesn't contains an extension
     */
    private Optional<String> getExtension(String uri) {
        int extensionIndex = uri.lastIndexOf(".");
        if (extensionIndex > 0) {
            return Optional.of(uri.substring(extensionIndex));
        }

        return Optional.empty();
    }

    /**
     * Removes all special characters, only allow lowercase letters (a-z),
     * numbers (0-9), and underscores.
     *
     * @param fieldName
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

    private String printDocument(Document doc) {
        Gson gson = new Gson();
        JsonObject document = gson.fromJson(doc.toJson(), JsonObject.class);
        JsonElement data = document.get("CompressedBinaryData");
        if (data != null && data.getAsString().length() > 1000) {
            document.addProperty("CompressedBinaryData", data.getAsString().substring(0, 1000));
        }

        return document.toString();
    }
}
