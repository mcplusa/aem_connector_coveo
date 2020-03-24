package com.mcplusa.coveo.connector.aem.service;

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
  private Map<String, Boolean> queueMap;

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
}
