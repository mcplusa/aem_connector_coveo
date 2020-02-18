package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.day.cq.commons.Externalizer;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
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

@Component(immediate = true)
@Service(CoveoContentBuilder.class)
@Property(name = CoveoIndexConfiguration.PRIMARY_TYPE, value = PageContentBuilder.PRIMARY_TYPE_VALUE)
public class PageContentBuilder extends AbstractCoveoContentBuilder {

    public static final String PRIMARY_TYPE_VALUE = "cq:Page";

    private static final Logger LOG = LoggerFactory.getLogger(PageContentBuilder.class);
    private static final String[] FIXED_RULES = {"cq:lastModified", "cq:template", "jcr:description", "jcr:title"};

    @Override
    public IndexEntry create(String path, @Nonnull ResourceResolver resolver) {
        String[] indexRules = getIndexRules(PRIMARY_TYPE_VALUE);
        if (ArrayUtils.isNotEmpty(indexRules)) {
            Externalizer externalizer = resolver.adaptTo(Externalizer.class);
            PageManager pageManager = resolver.adaptTo(PageManager.class);
            if (pageManager != null) {
                Page page = pageManager.getPage(path);
                if (page != null) {
                    String documentId = path;
                    if (externalizer != null) {
                        documentId = externalizer.publishLink(resolver, path) + ".html";
                    }
                    Resource res = page.getContentResource();
                    IndexEntry ret = new IndexEntry("idx", "page", path);
                    ret.addContent(getProperties(res, indexRules));
                    ret.setDocumentId(documentId);
                    return ret;
                }
            }
        } else {
            LOG.warn("Could not load indexRules for " + PRIMARY_TYPE_VALUE);
        }

        return null;
    }

    @Override
    protected String[] getFixedRules() {
        return FIXED_RULES;
    }

}
