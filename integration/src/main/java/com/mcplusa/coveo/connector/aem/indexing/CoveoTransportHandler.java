package com.mcplusa.coveo.connector.aem.indexing;

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
import com.mcplusa.coveo.sdk.CoveoResponse;
import com.mcplusa.coveo.sdk.pushapi.CoveoPushClient;
import com.mcplusa.coveo.sdk.pushapi.model.Document;
import com.mcplusa.coveo.connector.aem.service.CoveoService;
import java.io.IOException;
import java.util.Map;
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
     * @throws ReplicationException
     */
    @Override
    public ReplicationResult deliver(TransportContext ctx, ReplicationTransaction tx) throws ReplicationException {
        ReplicationLog log = tx.getLog();
        try {
            CoveoPushClient pushClient = coveoService.getClient();
            log.info(getClass().getSimpleName() + ": Using host: " + pushClient.getHost() + " org: " + pushClient.getOrganizationId() + " source: " + pushClient.getSourceId());
            ReplicationActionType replicationType = tx.getAction().getType();
            log.info(getClass().getSimpleName() + ": replicationType " + replicationType.toString());
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
            Document document = indexEntryToDocument(entry, log);
            CoveoResponse deleteResponse = pushClient.deleteDocument(document.getDocumentId());

            LOG.debug(deleteResponse.toString());
            log.info(getClass().getSimpleName() + ": Delete Call returned " + deleteResponse.getStatusLine().getStatusCode() + ": " + deleteResponse.getStatusLine().getReasonPhrase());
            if (deleteResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED) {
                return ReplicationResult.OK;
            } else {
                LOG.error("Could not delete " + document.getMetadata("type", String.class) + " at " + document.getMetadata("path", String.class));
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
        log.info(getClass().getSimpleName() + ": Indexing... " + tx.getContent().getContentType());
        ObjectMapper mapper = new ObjectMapper();
        IndexEntry entry = mapper.readValue(tx.getContent().getInputStream(), IndexEntry.class);
        if (entry != null) {
            Document document = indexEntryToDocument(entry, log);
            log.debug(getClass().getSimpleName() + ": Indexing " + document.getDocumentId());

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
    
    private Document indexEntryToDocument(IndexEntry indexEntry, ReplicationLog log) {
        String documentId = indexEntry.getContent("documentId", String.class);
        Document doc = new Document(documentId);
        doc.setMetadata(indexEntry.getContent());
        
        String keys = "";
        for (Map.Entry<String, Object> en : indexEntry.getContent().entrySet()) {
            String key = en.getKey();
//            Object value = en.getValue();
            keys += key + ", ";
            
//            doc.addMetadata(cleanFieldName(key), value, Object.class);
        }
        log.info(getClass().getSimpleName() + ": keys: " + keys);

        return doc;
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
}
