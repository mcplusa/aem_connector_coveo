package com.mcplusa.coveo.connector.aem.service;

import com.day.cq.search.QueryBuilder;
import com.mcplusa.coveo.sdk.pushapi.model.BatchIdentity;
import com.mcplusa.coveo.sdk.pushapi.model.Identity;
import com.mcplusa.coveo.sdk.pushapi.model.IdentityBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.apache.commons.collections.IteratorUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
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

  private static final long ITEMS_PER_PAGE = 10000;

  @Reference
  private ResourceResolverFactory resolverFactory;

  @Reference
  private QueryBuilder builder;

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
    Session session = null;

    try {
      long currentPage = 0;
      resourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
      UserManager userManager = resourceResolver.adaptTo(UserManager.class);
      session = resourceResolver.adaptTo(Session.class);

      List<String> pathList = getUsersPath(session);

      // iterate over each path
      for (String path : pathList) {
        // get users list from the path
        RowIterator users = getUsers(session, path, 0, ITEMS_PER_PAGE);

        while (users != null && users.hasNext()) {
          // iterate users
          while (users.hasNext()) {
            // Get row info
            Row row = users.nextRow();
            User auth = (User) userManager.getAuthorizableByPath(row.getPath());
            String username = auth.getPrincipal().getName();
            List<Group> groups = IteratorUtils.toList(auth.memberOf());

            // Build Identity
            IdentityBody idUserBody = new IdentityBody();
            Identity identity = new Identity();

            identity.setName(username);

            identity.setType(USER_TYPE);
            List<Identity> wellKnowns = new ArrayList<>();

            for (Group group : groups) {
              String groupId = group.getPrincipal().getName();

              // push to wellKnowns identity
              wellKnowns.add(new Identity(groupId, GROUP_TYPE));

              // append user to the group
              List<String> groupsList = groupsMap.getOrDefault(groupId, new ArrayList<>());
              groupsList.add(username);
              groupsMap.put(groupId, groupsList);
            }

            idUserBody.setWellKnowns(wellKnowns);
            idUserBody.setIdentity(identity);
            identityList.add(idUserBody);
          }

          currentPage++;
          users = getUsers(session, path, currentPage * ITEMS_PER_PAGE, ITEMS_PER_PAGE);
        }
      }

      // Append groups
      LOG.debug("Appending groups from map....");
      for (Map.Entry<String, List<String>> entry : groupsMap.entrySet()) {
        IdentityBody idBody = new IdentityBody();
        Identity identity = new Identity();

        identity.setName(entry.getKey());

        identity.setType(GROUP_TYPE);
        List<Identity> membersList = new ArrayList<>();
        for (String user : entry.getValue()) {
          membersList.add(new Identity(user, USER_TYPE));
        }

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

      if (session != null && session.isLive()) {
        session.logout();
      }
    }

    LOG.info("Sending batch ({} members)...", batchIdentity.getMembers().size());

    return batchIdentity;
  }

  /**
   * Get users list of an specific node path.
   * 
   * @param session  current session.
   * @param nodePath specific path of the lower-level parent node. (eg:
   *                 /home/users/h/)
   * @param offset   offset value.
   * @param limit    amount of users to be fetched.
   * @return rows of each user.
   */
  public RowIterator getUsers(Session session, String nodePath, long offset, long limit) throws RepositoryException {
    LOG.info("Getting users from {} : {} {}", nodePath, offset, limit);
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    String querys = "select [rep:principalName] from [rep:User] as a where [rep:principalName] is not null and isdescendantnode(a, '"
        + nodePath + "') order by [id] desc";
    Query query = queryManager.createQuery(querys, Query.JCR_SQL2);

    // Set Offset
    if (offset >= 0) {
      query.setOffset(offset);
    }
    // Set Limit
    if (limit != 0) {
      query.setLimit(limit);
    }

    QueryResult result = query.execute();

    if (result.getRows().getSize() == 0) {
      return null;
    }

    return result.getRows();
  }

  /**
   * Get children nodes path from /home/users/.
   * 
   * @param session current session.
   * @return List of paths
   */
  public List<String> getUsersPath(Session session) throws RepositoryException {
    LOG.info("Getting Users path list");
    List<String> paths = new ArrayList<>();
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    String querys = "SELECT node.* FROM [nt:base] AS node WHERE ISCHILDNODE(node, [/home/users/])";
    Query query = queryManager.createQuery(querys, Query.JCR_SQL2);

    QueryResult result = query.execute();

    if (result.getRows().getSize() == 0) {
      return paths;
    }

    RowIterator rows = result.getRows();
    while (rows.hasNext()) {
      Row row = rows.nextRow();
      String path = row.getPath();
      if (!path.equals("/home/users/rep:policy") || !path.equals("/home/users/rep:CugPolicy")) {
        paths.add(row.getPath());
      }
    }

    LOG.debug("{} paths found", paths.size());

    return paths;
  }
}
