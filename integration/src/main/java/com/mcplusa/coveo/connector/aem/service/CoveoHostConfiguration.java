package com.mcplusa.coveo.connector.aem.service;

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
 * Configuration for a single Coveo Source host.
 */
@Component(metatype = true, immediate = true, label = CoveoHostConfiguration.SERVICE_NAME, description = CoveoHostConfiguration.SERVICE_DESCRIPTION)
@Service(CoveoHostConfiguration.class)
@Properties({ @Property(name = "webconsole.configurationFactory.nameHint", value = "Host: {host}") })
public class CoveoHostConfiguration {

  public static final String SERVICE_NAME = "Coveo Provider";
  public static final String SERVICE_DESCRIPTION = "Configuration for the Coveo Provider";

  @Property(name = "orgId", label = "Organization ID", value = "org-id", description = "Organization Id of the source")
  public static final String PROPERTY_ORG_ID = "orgId";

  @Property(name = "sourceId", label = "Source ID", value = "source-id", description = "Source id")
  public static final String PROPERTY_SOURCE_ID = "sourceId";

  @Property(name = "accessToken", label = "Access Token", value = "access-token", description = "Access Token of the Source")
  public static final String PROPERTY_ACCESS_TOKEN = "accessToken";

  private static final String PROPERTY_ENV_DEFAULT = "PRODUCTION";
  @Property(name = "environment", label = "Environment", value = PROPERTY_ENV_DEFAULT, options = {
      @PropertyOption(name = "PRODUCTION", value = "PRODUCTION"),
      @PropertyOption(name = "DEVELOPMENT", value = "DEVELOPMENT"),
      @PropertyOption(name = "STAGING", value = "STAGING"), @PropertyOption(name = "HIPAA", value = "HIPAA") })
  public static final String PROPERTY_ENV = "environment";

  @Property(name = "agentId", label = "Agent ID", value = "coveo-index-agent", description = "Agent ID of the created Coveo Index Agent")
  public static final String PROPERTY_AGENT_ID = "agentId";

  @Property(name = "userIdentityProvider", label = "Users Identity Provider", value = "user-identity-provider", description = "Identity Provider for Users")
  public static final String PROPERTY_USER_IDENTITY_PROVIDER = "userIdentityProvider";

  @Property(name = "groupIdentityProvider", label = "Groups Identity Provider", value = "group-identity-provider", description = "Identity Provider for Groups")
  public static final String PROPERTY_GROUP_IDENTITY_PROVIDER = "groupIdentityProvider";

  private static final String PROPERTY_PERMISSION_POLICY_DEFAULT = "ALL";
  @Property(name = "permissionPolicy", label = "Permission Policy", value = PROPERTY_PERMISSION_POLICY_DEFAULT, options = {
      @PropertyOption(name = "ALL", value = "All"),
      @PropertyOption(name = "CUG", value = "Closed User Group Policy"),
      @PropertyOption(name = "POLICY", value = "Local Access Control Policy") })
  public static final String PROPERTY_PERMISSION_POLICY = "permissionPolicy";

  @Property(name = "groupIdentityProviderFilter", label = "Groups Identity Provider Filter", value = "", description = "Filter groups to sync in Security Identity, only matches groups name will be included.")
  public static final String PROPERTY_GROUP_IDENTITY_PROVIDER_FILTER = "groupIdentityProviderFilter";

  @Getter
  protected String organizationId;

  @Getter
  protected String sourceId;

  @Getter
  protected String accessToken;

  @Getter
  protected String environment;

  @Getter
  protected String agentId;

  @Getter
  protected String userIdentityProvider;

  @Getter
  protected String groupIdentityProvider;

  @Getter
  protected String aemIdentityProvider;

  @Getter
  protected String permissionPolicy;

  @Getter
  protected String groupIdentityProviderFilter;

  protected ComponentContext context;

  @Activate
  public void activate(ComponentContext context) {
    this.context = context;
    this.organizationId = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_ORG_ID), null);
    this.sourceId = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_SOURCE_ID), null);
    this.accessToken = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_ACCESS_TOKEN), null);
    this.environment = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_ENV), CoveoHostConfiguration.PROPERTY_ENV_DEFAULT);
    this.agentId = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_AGENT_ID), null);
    this.userIdentityProvider = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_USER_IDENTITY_PROVIDER), null);
    this.groupIdentityProvider = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_GROUP_IDENTITY_PROVIDER), null);
    this.aemIdentityProvider = "aem-security-identity";
    this.permissionPolicy = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_PERMISSION_POLICY), CoveoHostConfiguration.PROPERTY_PERMISSION_POLICY_DEFAULT);
    this.groupIdentityProviderFilter = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_GROUP_IDENTITY_PROVIDER_FILTER), null);
  }
}
