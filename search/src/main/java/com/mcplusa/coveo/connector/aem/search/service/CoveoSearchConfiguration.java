package com.mcplusa.coveo.connector.aem.search.service;

import lombok.Getter;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;

/**
 * Configuration for a Coveo Search.
 */
@Component(metatype = true, immediate = true,
        label = CoveoSearchConfiguration.SERVICE_NAME, description = CoveoSearchConfiguration.SERVICE_DESCRIPTION)
@Service(CoveoSearchConfiguration.class)
@Properties({
    @Property(name = "webconsole.configurationFactory.nameHint", value = "Host: {host}")
})
public class CoveoSearchConfiguration {
  public static final String SERVICE_NAME = "Coveo Search Configuration";
  public static final String SERVICE_DESCRIPTION = "Configuration for Coveo Search";

  @Property(name = "orgId", label = "Organization ID", value = "org-id", description = "Organization Id of the source")
  public static final String PROPERTY_ORG_ID = "orgId";

  private static final String PROPERTY_ENV_DEFAULT = "PRODUCTION";
  @Property(name = "environment", label = "Environment", value = PROPERTY_ENV_DEFAULT, options = {
        @PropertyOption(name = "PRODUCTION", value = "PRODUCTION")
        ,
  @PropertyOption(name = "DEVELOPMENT", value = "DEVELOPMENT")
        ,
  @PropertyOption(name = "STAGING", value = "STAGING")
        ,
  @PropertyOption(name = "HIPAA", value = "HIPAA")
    })
  public static final String PROPERTY_ENV = "environment";

  @Property(name = "apiKey", label = "API Key", value = "api-key", description = "API key with the privilege to impersonate users")
  public static final String PROPERTY_API_KEY = "apiKey";

  @Property(name = "userIdentityProvider", label = "Users Identity Provider", value = "user-identity-provider", description = "Identity Provider for Users")
  public static final String PROPERTY_USER_IDENTITY_PROVIDER = "userIdentityProvider";

  @Property(name = "groupIdentityProvider", label = "Groups Identity Provider", value = "group-identity-provider", description = "Identity Provider for Groups")
  public static final String PROPERTY_GROUP_IDENTITY_PROVIDER = "groupIdentityProvider";

  @Getter
  protected String organizationId;

  @Getter
  protected String environment;

  @Getter
  protected String apiKey;

  @Getter
  protected String userIdentityProvider;

  @Getter
  protected String groupIdentityProvider;

  @Getter
  protected String aemIdentityProvider;

  protected ComponentContext context;

  @Activate
  public void activate(ComponentContext context) {
    this.context = context;
    this.organizationId = PropertiesUtil.toString(context.getProperties().get(CoveoSearchConfiguration.PROPERTY_ORG_ID), null);
    this.environment = PropertiesUtil.toString(context.getProperties().get(CoveoSearchConfiguration.PROPERTY_ENV), CoveoSearchConfiguration.PROPERTY_ENV_DEFAULT);
    this.apiKey = PropertiesUtil.toString(context.getProperties().get(CoveoSearchConfiguration.PROPERTY_API_KEY), null);
    this.userIdentityProvider = PropertiesUtil.toString(context.getProperties().get(CoveoSearchConfiguration.PROPERTY_USER_IDENTITY_PROVIDER), null);
    this.groupIdentityProvider = PropertiesUtil.toString(context.getProperties().get(CoveoSearchConfiguration.PROPERTY_GROUP_IDENTITY_PROVIDER), null);
    this.aemIdentityProvider = "aem-security-identity";
  }
}