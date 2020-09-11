package com.mcplusa.coveo.connector.aem.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcplusa.coveo.sdk.CoveoEnvironment;
import com.mcplusa.coveo.sdk.CoveoFactory;
import com.mcplusa.coveo.sdk.CoveoResponse;
import com.mcplusa.coveo.sdk.CoveoResponseException;
import com.mcplusa.coveo.sdk.pushapi.CoveoPushClient;
import com.mcplusa.coveo.sdk.pushapi.model.BatchIdentity;
import com.mcplusa.coveo.sdk.pushapi.model.FileContainerResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import lombok.Getter;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpStatus;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service keeping a connection to the Coveo Source.
 */
@Component(metatype = false, immediate = false)
@Service(CoveoService.class)
public class CoveoService {

  private static final Logger LOG = LoggerFactory.getLogger(CoveoService.class);

  @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
  protected CoveoHostConfiguration config;

  @Reference
  private UserManagerService userManagerService;

  @Getter
  private CoveoPushClient client;

  @Getter
  private CoveoFactory factory;

  @Getter
  protected String userIdentityProvider;

  @Getter
  protected String groupIdentityProvider;

  @Getter
  protected String permissionPolicy;

  @Activate
  public void activate(ComponentContext context) {
    CoveoEnvironment environment = CoveoEnvironment.valueOf(config.getEnvironment());
    factory = new CoveoFactory(environment);
    client = factory.newPushClient(config.getAccessToken(), config.getOrganizationId(), config.getSourceId());
    userIdentityProvider = config.getUserIdentityProvider();
    groupIdentityProvider = config.getGroupIdentityProvider();
    permissionPolicy = config.getPermissionPolicy();
    createIdentityProvider();
  }

  @Deactivate
  public void deactivate(ComponentContext context) {
    if (this.factory != null) {
      try {
        this.factory.close();
      } catch (IOException ex) {
        LOG.warn("Could not close CoveoFactory", ex);
      }
    }
  }

  public void createIdentityProvider() {
    if (client != null && config.getOrganizationId() != null && config.getSourceId() != null
        && config.getAccessToken() != null) {
      BatchIdentity batchIdentity = this.userManagerService.getIdentityList(config.getGroupIdentityProviderFilter());
      updateSecurityIdentity(batchIdentity);
    }
  }

  private void updateSecurityIdentity(BatchIdentity batchIdentity) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    String json = gson.toJson(batchIdentity);
    LOG.info("JSON>>>>: {}", json);

    // try (InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {

    //   FileContainerResponse fileContainer = client.getFileContainer();

    //   CoveoResponse response = client.pushFileOnS3(is, fileContainer.getUploadUri());
    //   if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
    //     client.pushIdentitiesBatch(config.getAemIdentityProvider(), fileContainer.getFileId());
    //   } else {
    //     throw new CoveoResponseException(response);
    //   }

    // } catch (Exception e) {
    //   LOG.error("Could not push the identity batch to the file Container", e);
    // }
  }
}
