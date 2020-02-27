package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcplusa.coveo.connector.aem.indexing.Permission;
import com.mcplusa.coveo.connector.aem.indexing.config.CoveoIndexConfiguration;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.jcr.JsonItemWriter;
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

    protected JsonObject toJson(Node node) {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonItemWriter jsonWriter = new JsonItemWriter(null);
            jsonWriter.dump(node, stringWriter, -1, true);
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create(); //disableHtmlEscaping in JSON
            return gson.fromJson(stringWriter.toString(), JsonObject.class);
        } catch (RepositoryException | JSONException ex) {
            LOG.error("Error toJson", ex);
        }
        return null;
    }

    /**
     * Returns the content policy bound to the given component.
     *
     * @param policy json
     * @param userManager manager
     * @return the content policy. May be {@code nulll} in case no content
     * policy can be found.
     */
    protected List<Permission> getACLs(JsonObject policy, UserManager userManager) {
        List<Permission> acl = new ArrayList<>();

        try {
            for (String key : policy.keySet()) {
                JsonElement rep = policy.get(key);
                if (rep.isJsonObject()) {
                    String princ = rep.getAsJsonObject().get("rep:principalName").getAsString();
                    String type = rep.getAsJsonObject().get("jcr:primaryType").getAsString();
                    JsonArray priv = rep.getAsJsonObject().getAsJsonArray("rep:privileges");
                    boolean isGroup = principalIsGroup(userManager, princ);

                    if (type.equals("rep:GrantACE") && hasPrivileges(priv)) {
                        acl.add(new Permission(princ, Permission.PERMISSION_TYPE.ALLOW, isGroup));
                    } else if (type.equals("rep:DenyACE") && hasPrivileges(priv)) {
                        acl.add(new Permission(princ, Permission.PERMISSION_TYPE.DENY, isGroup));
                    }
                }
            }

        } catch (Exception ex) {
            LOG.error("error policy", ex);
        }

        return acl;
    }

    private boolean hasPrivileges(JsonArray privileges) {
        for (JsonElement priv : privileges) {
            if (priv.getAsString().equals("jcr:read") || priv.getAsString().equals("jcr:all")) {
                return true;
            }
        }

        return false;
    }

    protected boolean principalIsGroup(UserManager userManager, String principalName) {
        try {
            Iterator<Authorizable> authorizables = userManager.findAuthorizables(new Query() {
                public <T> void build(QueryBuilder<T> builder) {
                }
            });

            if (authorizables != null) {
                for (Iterator iterator = authorizables; iterator.hasNext();) {

                    Authorizable auth = (Authorizable) iterator.next();
                    if (auth.getID().equals(principalName)) {
                        return auth.isGroup();
                    }

                }
            }
        } catch (RepositoryException ex) {
            LOG.error("Error getting principal", ex);
        }

        return false;
    }

    protected <T> T getLastValue(Map<String, Object> res, String key) {
        Object value = res.get(key);
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof List<?>) {
                List<T> list = (List<T>) value;
                if (list.size() > 0) {
                    return list.get(list.size() - 1);
                }

                return null;
            } else {
                return (T) value;
            }
        } catch (ClassCastException ex) {
            return null;
        }
    }
}
