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

    try {
      ResourceResolver resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
      UserManager userManager = resourceResolver.adaptTo(UserManager.class);
      Iterator<Authorizable> authorizables = userManager.findAuthorizables(new Query() {
        public <T> void build(QueryBuilder<T> builder) {
          // Get all authorizables
        }
      });

      if (authorizables != null) {
        for (Iterator iterator = authorizables; iterator.hasNext();) {
          IdentityBody idBody = new IdentityBody();
          Identity identity = new Identity();
          List<Identity> membersList = new ArrayList<>();
          List<Identity> wellKnowns = new ArrayList<>();

          Authorizable auth = (Authorizable) iterator.next();

          identity.setName(auth.getPrincipal().getName());

          if (auth.isGroup()) {
            Group group = (Group) auth;
            identity.setType(GROUP_TYPE);

            Iterator<Authorizable> auths = group.getMembers();
            for (Iterator<Authorizable> i = auths; auths.hasNext();) {
              Authorizable authorizable = i.next();
              if (!authorizable.isGroup()) {
                membersList.add(new Identity(authorizable.getPrincipal().getName(), USER_TYPE));
              }
            }
          } else {
            User usr = (User) auth;
            identity.setType(USER_TYPE);

            Iterator<Group> groups = usr.memberOf();
            for (Iterator<Group> i = groups; groups.hasNext();) {
              Group authorizable = i.next();
              String type = authorizable.isGroup() ? GROUP_TYPE : USER_TYPE;
              wellKnowns.add(new Identity(authorizable.getPrincipal().getName(), type));
            }
          }

          idBody.setWellKnowns(wellKnowns);
          idBody.setMembers(membersList);
          idBody.setIdentity(identity);
          identityList.add(idBody);
          batchIdentity.setMembers(identityList);
        }
      }
    } catch (RepositoryException | LoginException ex) {
      LOG.error("Error getting all identities", ex);
    }

    return batchIdentity;
  }
}