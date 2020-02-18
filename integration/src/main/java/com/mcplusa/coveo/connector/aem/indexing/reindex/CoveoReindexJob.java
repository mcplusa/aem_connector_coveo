package com.mcplusa.coveo.connector.aem.indexing.reindex;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that performs a reindexing
 */
@Component(immediate = true)
@Service(Runnable.class)
public class CoveoReindexJob implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(CoveoReindexJob.class);

  @Override
  public void run() {
    LOG.info("Running reindexing");
  }

}
