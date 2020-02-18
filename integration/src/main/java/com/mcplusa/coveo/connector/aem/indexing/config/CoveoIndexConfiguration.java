package com.mcplusa.coveo.connector.aem.indexing.config;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = true, immediate = true, configurationFactory = true,
        label = CoveoIndexConfiguration.SERVICE_NAME, description = CoveoIndexConfiguration.SERVICE_DESCRIPTION)
@Service(CoveoIndexConfiguration.class)
@Properties({
    @Property(name = "webconsole.configurationFactory.nameHint", value = "Primary Type: {primaryType}")
})
public class CoveoIndexConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CoveoIndexConfiguration.class);

    /**
     * Filter Property for jcr:primary Type
     */
    public static final String PRIMARY_TYPE = JcrConstants.JCR_PRIMARYTYPE;

    public static final String SERVICE_NAME = "Coveo Index Configuration";
    public static final String SERVICE_DESCRIPTION = "Service to configure the Coveo Index";

    @Property(name = "primaryType",
            label = "Primary Type",
            description = "Primary Type for which this configuration is responsible. E.g. cq:page or dam:Asset")
    public static final String PROPERTY_BASE_PATH = "primaryType";

    //TODO: add support for mapping hints (e.g. jcr:title;keyword
    @Property(name = "indexRules",
            cardinality = Integer.MAX_VALUE,
            label = "Index Rules",
            description = "List with the names of all properties that should be indexed."
    )
    public static final String PROPERTY_INDEX_RULES = "indexRules";

    @Property(name = "reindex",
            label = "Reindex",
            boolValue = false,
            description = "If enabled, a reindexing will start on save")
    public static final String PROPERTY_REINDEX = "reindex";

    protected String[] indexRules;

    protected ComponentContext context;

    @Activate
    public void activate(ComponentContext context) {
        this.context = context;
        this.indexRules = PropertiesUtil.toStringArray(context.getProperties().get(CoveoIndexConfiguration.PROPERTY_INDEX_RULES));
    }

    /**
     *
     * @return array of index rules
     */
    public String[] getIndexRules() {
        return indexRules;
    }

}
