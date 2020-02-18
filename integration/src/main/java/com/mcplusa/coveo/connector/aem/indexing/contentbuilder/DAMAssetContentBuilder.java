package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.day.cq.commons.Externalizer;
import com.day.cq.dam.api.Asset;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Content Builder for DAM Assets
 */
@Component(immediate = true)
@Service(CoveoContentBuilder.class)
@Property(name = CoveoIndexConfiguration.PRIMARY_TYPE, value = DAMAssetContentBuilder.PRIMARY_TYPE_VALUE)
public class DAMAssetContentBuilder extends AbstractCoveoContentBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DAMAssetContentBuilder.class);
    public static final String PRIMARY_TYPE_VALUE = "dam:Asset";

    private static final String[] FIXED_RULES = {"dc:title", "dc:description", "jcr:lastModified"};

    @Override
    public IndexEntry create(String path, @Nonnull ResourceResolver resolver) {
        String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);
        if (ArrayUtils.isNotEmpty(indexRules)) {
            Externalizer externalizer = resolver.adaptTo(Externalizer.class);
            Resource res = resolver.getResource(path);
            if (res != null) {
                Asset asset = res.adaptTo(Asset.class);
                if (asset != null) {
                    String documentId = path;
                    if (externalizer != null) {
                        documentId = externalizer.publishLink(resolver, path);
                    }
                    IndexEntry ret = new IndexEntry("idx", "asset", path);
                    ret.addContent(getProperties(res, indexRules));
                    ret.setDocumentId(documentId);
                    return ret;
                }
                LOG.error("Could not adapt asset");
            }
        }
        return null;
    }

    @Override
    protected String[] getFixedRules() {
        return FIXED_RULES;
    }
}
