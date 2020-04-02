package com.mcplusa.coveo.connector.aem.service;

import com.mcplusa.coveo.sdk.CoveoEnvironment;
import com.mcplusa.coveo.sdk.CoveoFactory;
import com.mcplusa.coveo.sdk.pushapi.CoveoPushClient;
import java.io.IOException;
import lombok.Getter;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service keeping a connection to the Coveo Source.
 */
@Component(metatype = false, immediate = true)
@Service(CoveoService.class)
public class CoveoService {

    private static final Logger log = LoggerFactory.getLogger(CoveoService.class);

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoveoHostConfiguration hostConfiguration;

    @Getter
    private CoveoPushClient client;

    @Getter
    private CoveoFactory factory;

    @Getter
    protected String userIdentityProvider;

    @Getter
    protected String groupIdentityProvider;

    @Activate
    public void activate(ComponentContext context) {
        CoveoEnvironment environment = CoveoEnvironment.valueOf(hostConfiguration.getEnvironment());
        factory = new CoveoFactory(environment);
        client = factory.newPushClient(hostConfiguration.getAccessToken(), hostConfiguration.getOrganizationId(), hostConfiguration.getSourceId());
        userIdentityProvider = hostConfiguration.getUserIdentityProvider();
        groupIdentityProvider = hostConfiguration.getGroupIdentityProvider();
    }

    @Deactivate
    public void deactivate(ComponentContext context) {
        if (this.factory != null) {
            try {
                this.factory.close();
            } catch (IOException ex) {
                log.warn("Could not close CoveoFactory", ex);
            }
        }
    }

}
