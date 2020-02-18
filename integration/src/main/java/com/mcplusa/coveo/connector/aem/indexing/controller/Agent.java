package com.mcplusa.coveo.connector.aem.indexing.controller;

import com.mcplusa.coveo.connector.aem.service.CoveoService;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import lombok.Getter;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

@Model(adaptables = {
    SlingHttpServletRequest.class, Resource.class}
)
public class Agent {

    @SlingObject
    protected Resource resource;

    @Getter
    private boolean valid;

    @ValueMapValue
    @Default(values = "false")
    private String enabled;

    @Inject
    @Optional
    private CoveoService coveoService;

    @PostConstruct
    protected void activate() {
        valid = StringUtils.equals(enabled, "true") && coveoService != null && coveoService.getClient() != null;
    }

}
