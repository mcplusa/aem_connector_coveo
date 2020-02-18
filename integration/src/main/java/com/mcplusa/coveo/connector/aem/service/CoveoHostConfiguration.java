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
 * Configuration for a single Coveo Source host
 */
@Component(metatype = true, immediate = true,
        label = CoveoHostConfiguration.SERVICE_NAME, description = CoveoHostConfiguration.SERVICE_DESCRIPTION)
@Service(CoveoHostConfiguration.class)
@Properties({
    @Property(name = "webconsole.configurationFactory.nameHint", value = "Host: {host}")
})
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
        @PropertyOption(name = "PRODUCTION", value = "PRODUCTION")
        ,
  @PropertyOption(name = "DEVELOPMENT", value = "DEVELOPMENT")
        ,
  @PropertyOption(name = "STAGING", value = "STAGING")
        ,
  @PropertyOption(name = "HIPAA", value = "HIPAA")
    })
    public static final String PROPERTY_ENV = "environment";

    @Getter
    protected String organizationId;

    @Getter
    protected String sourceId;

    @Getter
    protected String accessToken;

    @Getter
    protected String environment;

    protected ComponentContext context;

    @Activate
    public void activate(ComponentContext context) {
        this.context = context;
        this.organizationId = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_ORG_ID), null);
        this.sourceId = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_SOURCE_ID), null);
        this.accessToken = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_ACCESS_TOKEN), null);
        this.environment = PropertiesUtil.toString(context.getProperties().get(CoveoHostConfiguration.PROPERTY_ENV), CoveoHostConfiguration.PROPERTY_ENV_DEFAULT);
    }

}
