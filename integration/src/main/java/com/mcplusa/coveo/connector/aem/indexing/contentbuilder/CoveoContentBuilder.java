package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import javax.annotation.Nonnull;
import org.apache.sling.api.resource.ResourceResolver;

public interface CoveoContentBuilder {

    public IndexEntry create(String path, @Nonnull ResourceResolver resolver);

}
