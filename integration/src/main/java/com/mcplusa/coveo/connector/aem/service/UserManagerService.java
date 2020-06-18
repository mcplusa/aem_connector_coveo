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
import org.apache.jackrabbit.api.security.user.QueryBuilder.Direction;
import org.apache.jackrabbit.api.security.user.UserManager;
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

  private static final long ITEMS_PER_PAGE = 5;

  @Reference
  private ResourceResolverFactory resolverFactory;

  /**
   * Get all authorizables and return a BatchIdentity.
   *
   * @return BatchIdentity
   */
  public BatchIdentity getIdentityList() {
    LOG.debug("Getting identities list...");
    BatchIdentity batchIdentity = new BatchIdentity();
    List<IdentityBody> identityList = new ArrayList<>();

    Map<String, List<String>> usersMap = new HashMap<>();

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
          List<Identity> membersList = new ArrayList<>();
  
          Authorizable auth = iterator.next();

          if (auth.isGroup()) {
            identity.setName(auth.getPrincipal().getName());

            Group group = (Group) auth;
            identity.setType(GROUP_TYPE);
            membersList = getGroupMemberList(group, usersMap);

            idBody.setMembers(membersList);
            idBody.setIdentity(identity);
            identityList.add(idBody);
          }
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

      // Append users
      LOG.debug("Appending users from map....");
      for (Map.Entry<String, List<String>> entry : usersMap.entrySet()) {
        IdentityBody idBody = new IdentityBody();
        Identity identity = new Identity();

        identity.setName(entry.getKey());

        identity.setType(USER_TYPE);
        List<Identity> wellKnowns = getUserWellKnows(entry.getValue());

        idBody.setWellKnowns(wellKnowns);
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

    LOG.debug("Sending batch ({} members)...", batchIdentity.getMembers().size());

    return batchIdentity;
  }

  private Iterator<Authorizable> getAuthorizables(UserManager userManager, long page) {
    try {
      return userManager.findAuthorizables(new Query() {
        public <T> void build(QueryBuilder<T> builder) {
          builder.setSelector(Group.class);
          builder.setSortOrder("@jcr:created", Direction.ASCENDING);
          builder.setLimit(ITEMS_PER_PAGE * page, ITEMS_PER_PAGE);
        }
      });
    } catch (Exception ex) {
      LOG.error("Error getting findAuthorizables", ex);
      return null;
    }
  }

  private List<Identity> getGroupMemberList(Group group, Map<String, List<String>> usersMap) {
    List<Identity> membersList = new ArrayList<>();

    try {
      Iterator<Authorizable> auths = group.getMembers();
      while (auths.hasNext()) {
        Authorizable authorizable = auths.next();
        if (!authorizable.isGroup()) {
          String userName = authorizable.getPrincipal().getName();
          membersList.add(new Identity(userName, USER_TYPE));

          // Put group to the users map
          List<String> groupsList = usersMap.getOrDefault(userName, new ArrayList<>());
          groupsList.add(group.getPrincipal().getName());
          usersMap.put(userName, groupsList);
        }
      }
    } catch (RepositoryException ex) {
      LOG.error("Error getting wellknows list of the user", ex);
    }

    return membersList;
  }

  private List<Identity> getUserWellKnows(List<String> groups) {
    List<Identity> wellKnowns = new ArrayList<>();
    
    for (String group : groups) {
      wellKnowns.add(new Identity(group, GROUP_TYPE));
    }

    return wellKnowns;
  }
}
