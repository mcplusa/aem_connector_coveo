package com.mcplusa.coveo.connector.aem.indexing;

import com.mcplusa.coveo.connector.aem.service.CoveoService;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service(value = Runnable.class)
@Properties({ @Property(name = "scheduler.expression", value = "0 0 */3 ? * *"),
    @Property(name = "scheduler.concurrent", boolValue = false) })
public class SecurityIdentityCronJob implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityIdentityCronJob.class);

  @Reference
  private CoveoService coveoService;

  @Override
  public void run() {
    try {
      LOG.debug("Updating Security Identity...");
      coveoService.createIdentityProvider();
    } catch (Exception e) {
      LOG.error("Could not update Security Identity", e);
    }
  }

}