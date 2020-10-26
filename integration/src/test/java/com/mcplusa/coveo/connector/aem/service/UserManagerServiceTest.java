package com.mcplusa.coveo.connector.aem.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import com.day.cq.search.QueryBuilder;
import com.mcplusa.coveo.connector.aem.testcontext.AppAemContext;
import com.mcplusa.coveo.sdk.pushapi.model.BatchIdentity;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.testing.mock.jcr.MockQueryResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.wcm.testing.mock.aem.junit.AemContext;

@RunWith(MockitoJUnitRunner.class)
public class UserManagerServiceTest {

  @Mock
  private ResourceResolverFactory resolverFactory;

  @Mock
  private QueryBuilder builder;

  @InjectMocks
  private UserManagerService service;

  @Rule
  public AemContext context = AppAemContext.newAemContext();

  @Test
  public void testBatch() throws LoginException, RepositoryException {
    Session mockSession = mockQuery();
    Group group = createGroup("group-1");
    UserManager mockUserManager = mock(UserManager.class);
    Authorizable auth = createUser("user-1", Arrays.asList(group));
    when(mockUserManager.getAuthorizableByPath(anyString())).thenReturn(auth);

    context.registerAdapter(ResourceResolver.class, Session.class, mockSession);
    context.registerAdapter(ResourceResolver.class, UserManager.class, mockUserManager);
    when(resolverFactory.getAdministrativeResourceResolver(any())).thenReturn(context.resourceResolver());
    BatchIdentity batch = service.getIdentityList(null);

    assertNotNull(batch);

    // Contains both group-1 and user-1
    assertEquals(2, batch.getMembers().size());
  }

  @Test
  public void testFilterGroups() throws LoginException, RepositoryException {
    Session mockSession = mockQuery();
    Group group = createGroup("group-1");
    Group group2 = createGroup("group-2");
    Group group3 = createGroup("another-group");
    UserManager mockUserManager = mock(UserManager.class);
    Authorizable auth = createUser("user-1", Arrays.asList(group, group2, group3));
    when(mockUserManager.getAuthorizableByPath(anyString())).thenReturn(auth);

    context.registerAdapter(ResourceResolver.class, Session.class, mockSession);
    context.registerAdapter(ResourceResolver.class, UserManager.class, mockUserManager);
    when(resolverFactory.getAdministrativeResourceResolver(any())).thenReturn(context.resourceResolver());
    BatchIdentity batch = service.getIdentityList("(group-2)");

    assertNotNull(batch);

    // Only group-2 and user-1
    assertEquals(2, batch.getMembers().size());
  }

  private User createUser(String name, List<Group> groups) throws RepositoryException {
    User user = mock(User.class);
    Principal principal = mock(Principal.class);

    when(principal.getName()).thenReturn(name);
    when(user.getPrincipal()).thenReturn(principal);

    when(user.memberOf()).thenReturn(groups.iterator());

    return user;
  }

  private Group createGroup(String name) throws RepositoryException {
    Group group = mock(Group.class);
    Principal principal = mock(Principal.class);

    when(principal.getName()).thenReturn(name);
    when(group.getPrincipal()).thenReturn(principal);


    return group;
  }

  private Node newNode(String path) throws RepositoryException {
    Node node = mock(Node.class);
    when(node.getPath()).thenReturn(path);

    return node;
  }

  private Session mockQuery() throws RepositoryException {
    Session mockSession = mock(Session.class);
    Workspace mockWorkspace = mock(Workspace.class);
    QueryManager mockQueryMgr = mock(QueryManager.class);
    Query mockQuery = mock(Query.class);

    Node userDirectory1 = newNode("/1");
    Node userDirectory2 = newNode("/2");
    List<Node> paths = Arrays.asList(userDirectory1, userDirectory2);
    MockQueryResult result = new MockQueryResult(paths);

    Node user1 = newNode("/1/admin");
    List<Node> pathsu = Arrays.asList(user1);
    MockQueryResult result2 = new MockQueryResult(pathsu);
    MockQueryResult result3 = new MockQueryResult(new ArrayList<>());
    
    when(mockQuery.execute()).thenReturn(result, result2, result3);
    
    when(mockQueryMgr.createQuery(anyString(), anyString())).thenReturn(mockQuery);

    when(mockWorkspace.getQueryManager()).thenReturn(mockQueryMgr);
    when(mockSession.getWorkspace()).thenReturn(mockWorkspace);

    return mockSession;
  }
}
