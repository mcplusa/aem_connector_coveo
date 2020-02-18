package com.mcplusa.coveo.connector.aem.indexing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data object holding all information required to index/deindex an entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexEntry {

    private static final Logger LOG = LoggerFactory.getLogger(IndexEntry.class);
    /**
     * Index for the entity
     */
    @Getter
    @Setter
    private String index;
    /**
     * Type of the entity (e.g. page or damasset)
     */
    @Getter
    @Setter
    private String type;
    /*
   * ID of the entity
     */
    @Getter
    @Setter
    private String id;
    /**
     * Content to index
     */
    @Setter
    private Map<String, Object> content = new HashMap<>();

    public IndexEntry() {
    }

    /**
     *
     * @param index
     * @param type
     * @param path
     */
    public IndexEntry(String index, String type, String path) {
        this.index = index;
        this.type = type;
        setPath(path);
    }

    public void addContent(String key, Object value) {
        content.put(key, value);
    }

    public void setPath(String path) {
        this.content.put("path", path);
        this.id = DigestUtils.md5Hex(path);
    }

    public String getPath() {
        return getContent("path", String.class);
    }

    public void setDocumentId(String documentId) {
        this.content.put("documentId", documentId);
    }

    public String getDocumentId() {
        return getContent("documentId", String.class);
    }

    public void addContent(Map<String, Object> properties) {
        content.putAll(properties);
    }

    /**
     *
     * @return unmodifiable map with the content
     */
    public Map<String, Object> getContent() {
        return Collections.unmodifiableMap(content);
    }

    /**
     * Returns the content for the given key or null if cast fails
     *
     * @param <T>
     * @param key
     * @param type
     * @return
     */
    public <T> T getContent(final String key, final Class<T> type) {
        try {
            return (T) content.get(key);
        } catch (ClassCastException cce) {
            LOG.warn("Could not cast " + key + " to " + type, cce);
        }
        return null;
    }

}
