package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCoveoContentBuilder implements CoveoContentBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCoveoContentBuilder.class);

    private BundleContext context;

    @Activate
    public void activate(BundleContext context) {
        this.context = context;
    }

    /**
     * @param primaryType document type
     * @return List with all properties to index
     */
    protected String[] getIndexRules(String primaryType) {
        CoveoIndexConfiguration config = getIndexConfig(primaryType);
        if (config != null) {
            return ArrayUtils.addAll(config.getIndexRules(), getFixedRules());
        }
        return getFixedRules();
    }

    /**
     *
     * @return Array with hardcoded rules
     */
    protected String[] getFixedRules() {
        return new String[0];
    }

    private CoveoIndexConfiguration getIndexConfig(String primaryType) {
        try {
            ServiceReference[] serviceReferences = context.getServiceReferences(CoveoIndexConfiguration.class.getName(),
                    "(" + CoveoIndexConfiguration.PROPERTY_BASE_PATH + "=" + primaryType + ")");
            if (serviceReferences != null && serviceReferences.length > 0) {
                return (CoveoIndexConfiguration) context.getService(serviceReferences[0]);
            }
        } catch (InvalidSyntaxException | NullPointerException ex) {
            LOG.error("Exception during service lookup", ex);
        }
        LOG.info("Could not load a CoveoConfiguration for primaryType " + primaryType);
        return null;
    }

    /**
     * Recursively searches all child-resources for the given resources and
     * returns a map with all of them
     *
     * @param res Resource document
     * @param properties properties to map
     * @return Map with all properties
     */
    protected Map<String, Object> getProperties(Resource res, String[] properties) {
        ValueMap vm = res.getValueMap();
        Map<String, Object> ret = Arrays.stream(properties)
                .filter(property -> vm.containsKey(property))
                .collect(Collectors.toMap(Function.identity(), property -> vm.get(property)));

        for (Resource child : res.getChildren()) {
            Map<String, Object> props = getProperties(child, properties);
            // merge properties
            props.entrySet().forEach(entry -> {
                String key = entry.getKey();
                if (!ret.containsKey(key)) {
                    ret.put(key, entry.getValue());
                } else {
                    ret.put(key, mergeProperties(ret.get(entry.getKey()), entry.getValue()));
                }
            });
        }
        return ret;
    }

    private Object[] mergeProperties(Object obj1, Object obj2) {
        List<Object> tmp = new ArrayList<>();
        addProperty(tmp, obj1);
        addProperty(tmp, obj2);
        return tmp.toArray(new Object[tmp.size()]);
    }

    private void addProperty(List<Object> list, Object property) {
        if (property.getClass().isArray()) {
            list.addAll(Arrays.asList((Object[]) property));
        } else {
            list.add(property);
        }
    }
}
