package com.mcplusa.coveo.connector.aem.service;

import com.mcplusa.coveo.sdk.pushapi.model.BatchIdentity;
import com.mcplusa.coveo.sdk.pushapi.model.Identity;
import com.mcplusa.coveo.sdk.pushapi.model.IdentityBody;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = false, immediate = true)
@Service(UserManagerService.class)
public class UserManagerService {

  private static final Logger LOG = LoggerFactory.getLogger(UserManagerService.class);

  private static final String GROUP_TYPE = "VIRTUAL_GROUP";
  private static final String USER_TYPE = "USER";

  private static final long ITEMS_PER_PAGE = 15000;

  @Reference
  private ResourceResolverFactory resolverFactory;

  /**
   * Get all authorizables and return a BatchIdentity.
   *
   * @return BatchIdentity
   */
  public BatchIdentity getIdentityList() {
    BatchIdentity batchIdentity = new BatchIdentity();
    List<IdentityBody> identityList = new ArrayList<>();

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
          List<Identity> wellKnowns = new ArrayList<>();
  
          Authorizable auth = iterator.next();
  
          identity.setName(auth.getPrincipal().getName());
  
          if (auth.isGroup()) {
            Group group = (Group) auth;
            identity.setType(GROUP_TYPE);
            membersList = getGroupMemberList(group);
          } else {
            User usr = (User) auth;
            identity.setType(USER_TYPE);
            wellKnowns = getUserWellKnows(usr);
          }
  
          idBody.setWellKnowns(wellKnowns);
          idBody.setMembers(membersList);
          idBody.setIdentity(identity);
          identityList.add(idBody);
          batchIdentity.setMembers(identityList);
        }

        // Increase page number
        currentPage++;

        // Get next page
        authorizables = getAuthorizables(userManager, currentPage);
      }
    } catch (RepositoryException | LoginException ex) {
      LOG.error("Error getting all identities", ex);
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
      LOG.debug("Getting page {}...", page + 1);
      return userManager.findAuthorizables(new Query() {
        public <T> void build(QueryBuilder<T> builder) {
          builder.setLimit(ITEMS_PER_PAGE * page, ITEMS_PER_PAGE);
        }
      });
    } catch (Exception ex) {
      LOG.error("Error getting findAuthorizables", ex);
      return null;
    }
  }

  private List<Identity> getGroupMemberList(Group group) {
    List<Identity> membersList = new ArrayList<>();

    try {
      Iterator<Authorizable> auths = group.getMembers();
      while (auths.hasNext()) {
        Authorizable authorizable = auths.next();
        if (!authorizable.isGroup()) {
          membersList.add(new Identity(authorizable.getPrincipal().getName(), USER_TYPE));
        }
      }
    } catch (RepositoryException ex) {
      LOG.error("Error getting member list of the group", ex);
    }

    return membersList;
  }

  private List<Identity> getUserWellKnows(User user) {
    List<Identity> wellKnowns = new ArrayList<>();
    try {
      Iterator<Group> groups = user.memberOf();
      while (groups.hasNext()) {
        Group authorizable = groups.next();
        String type = authorizable.isGroup() ? GROUP_TYPE : USER_TYPE;
        wellKnowns.add(new Identity(authorizable.getPrincipal().getName(), type));
      }
    } catch (RepositoryException ex) {
      LOG.error("Error getting wellknows list of the user", ex);
    }

    return wellKnowns;
  }
}
