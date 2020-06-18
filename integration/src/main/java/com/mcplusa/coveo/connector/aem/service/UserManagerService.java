package com.mcplusa.coveo.connector.aem.service;

import com.mcplusa.coveo.sdk.pushapi.model.BatchIdentity;
import com.mcplusa.coveo.sdk.pushapi.model.Identity;
import com.mcplusa.coveo.sdk.pushapi.model.IdentityBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.Query;
import org.apache.jackrabbit.api.security.user.QueryBuilder;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.api.security.user.QueryBuilder.Direction;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = false)
@Service(UserManagerService.class)
public class UserManagerService {

  private static final Logger LOG = LoggerFactory.getLogger(UserManagerService.class);

  private static final String GROUP_TYPE = "VIRTUAL_GROUP";
  private static final String USER_TYPE = "USER";

  private static final long ITEMS_PER_PAGE = 10000;

  @Reference
  private ResourceResolverFactory resolverFactory;

  /**
   * Get all authorizables and return a BatchIdentity.
   *
   * @return BatchIdentity
   */
  public BatchIdentity getIdentityList() {
    LOG.info("Getting identities list...");
    BatchIdentity batchIdentity = new BatchIdentity();
    List<IdentityBody> identityList = new ArrayList<>();

    Map<String, List<String>> groupsMap = new HashMap<>();

    ResourceResolver resourceResolver = null;

    try {
      long currentPage = 0;
      resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
      UserManager userManager = resourceResolver.adaptTo(UserManager.class);
      Iterator<Authorizable> authorizables = getAuthorizables(userManager, currentPage);

      if (authorizables == null || !authorizables.hasNext()) {
        return batchIdentity;
      }

      while (authorizables != null && authorizables.hasNext()) {
        for (Iterator<Authorizable> iterator = authorizables; iterator.hasNext(); ) {
          IdentityBody idBody = new IdentityBody();
          Identity identity = new Identity();
          List<Identity> wellKnowns = new ArrayList<>();
  
          Authorizable auth = iterator.next();
  
          identity.setName(auth.getPrincipal().getName());
  
          if (!auth.isGroup()) {
            User usr = (User) auth;
            identity.setType(USER_TYPE);
            wellKnowns = getUserWellKnows(usr, groupsMap);
          }
  
          idBody.setWellKnowns(wellKnowns);
          idBody.setIdentity(identity);
          identityList.add(idBody);
        }

        // Close current session
        if (resourceResolver != null && resourceResolver.isLive()) {
          resourceResolver.close();
        }

        // Create new session
        resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
        userManager = resourceResolver.adaptTo(UserManager.class);

        // Increase page number
        currentPage++;

        // Get next page
        authorizables = getAuthorizables(userManager, currentPage);
      }

      // Append groups
      for (Map.Entry<String, List<String>> entry : groupsMap.entrySet()) {
        IdentityBody idBody = new IdentityBody();
        Identity identity = new Identity();

        identity.setName(entry.getKey());

        identity.setType(GROUP_TYPE);
        List<Identity> membersList = getGroupMemberList(entry.getValue());

        idBody.setMembers(membersList);
        idBody.setIdentity(identity);
        identityList.add(idBody);
      }

      batchIdentity.setMembers(identityList);
    } catch (RepositoryException | LoginException ex) {
      LOG.error("Error getting all identities", ex);
    } catch (Exception ex) {
      LOG.error("Unexpected error getting identities", ex);
    } finally {
      if (resourceResolver != null && resourceResolver.isLive()) {
        resourceResolver.close();
      }
    }

    LOG.info("Sending batch ({} members)...", batchIdentity.getMembers().size());

    return batchIdentity;
  }

  private Iterator<Authorizable> getAuthorizables(UserManager userManager, long page) {
    try {
      return userManager.findAuthorizables(new Query() {
        public <T> void build(QueryBuilder<T> builder) {
          builder.setSelector(User.class);
          builder.setSortOrder("@jcr:created", Direction.ASCENDING);
          builder.setLimit(ITEMS_PER_PAGE * page, ITEMS_PER_PAGE);
        }
      });
    } catch (Exception ex) {
      LOG.error("Error getting findAuthorizables", ex);
      return null;
    }
  }

  private List<Identity> getGroupMemberList(List<String> users) {
    List<Identity> membersList = new ArrayList<>();

    for (String user : users) {
      membersList.add(new Identity(user, USER_TYPE));
    }

    return membersList;
  }

  private List<Identity> getUserWellKnows(User user, Map<String, List<String>> groupsMap) {
    List<Identity> wellKnowns = new ArrayList<>();
    try {
      Iterator<Group> groups = user.memberOf();
      while (groups.hasNext()) {
        Group authorizable = groups.next();
        String groupName = authorizable.getPrincipal().getName();
        wellKnowns.add(new Identity(groupName, GROUP_TYPE));

        // Put user to the groups map
        List<String> usersList = groupsMap.getOrDefault(groupName, new ArrayList<>());
        usersList.add(user.getPrincipal().getName());
        groupsMap.put(groupName, usersList);
      }
    } catch (RepositoryException ex) {
      LOG.error("Error getting wellknows list of the user", ex);
    }

    return wellKnowns;
  }
}
