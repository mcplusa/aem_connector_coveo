package com.mcplusa.coveo.connector.aem.service;

import com.mcplusa.coveo.sdk.pushapi.model.BatchRequest;
import com.mcplusa.coveo.sdk.pushapi.model.Document;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;

/**
 * Service to handle queues for an specific Agent.
 */
@Component(metatype = false, immediate = true)
@Service(CoveoQueueService.class)
public class CoveoQueueService {

  @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
  protected CoveoHostConfiguration hostConfiguration;

  @Getter
  private Map<String, BatchRequest> queueMap;

  @Getter
  private String agentId;

  @Activate
  public void activate(ComponentContext context) {
    this.queueMap = new HashMap<>();
    this.agentId = this.hostConfiguration.getAgentId();
  }

  @Deactivate
  public void deactivate(ComponentContext context) {
    if (this.queueMap != null) {
      this.queueMap.clear();
    }
  }

  /**
   * Push a document to the addOrUpdate list.
   * 
   * @param queueName key of the map
   * @param document  document to push
   */
  public void addDocument(String queueName, Document document) {
    if (queueMap.containsKey(queueName)) {
      BatchRequest batchRequest = queueMap.get(queueName);
      batchRequest.pushDocument(document);
      queueMap.put(queueName, batchRequest);
    }
  }

  /**
   * Push a document to the delete list.
   * 
   * @param queueName  key of the map
   * @param documentId documentId to push
   */
  public void deleteDocument(String queueName, String documentId) {
    if (queueMap.containsKey(queueName)) {
      BatchRequest batchRequest = queueMap.get(queueName);
      batchRequest.deleteDocument(documentId, true);
      queueMap.put(queueName, batchRequest);
    }
  }
}
