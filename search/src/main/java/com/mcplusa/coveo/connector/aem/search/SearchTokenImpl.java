package com.mcplusa.coveo.connector.aem.search;

import com.google.gson.Gson;
import com.mcplusa.coveo.connector.aem.search.model.SearchTokenPayload;
import com.mcplusa.coveo.connector.aem.search.model.UserId;
import com.mcplusa.coveo.connector.aem.search.service.CoveoSearchConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service
public class SearchTokenImpl implements SearchToken {

  private static final Logger LOG = LoggerFactory.getLogger(SearchTokenImpl.class);
  private static final String SEARCH_TOKEN_PATH = "/rest/search/v2/token";

  @Reference
  ResourceResolverFactory resolverFactory;

  @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
  protected CoveoSearchConfiguration coveoConfig;

  @Override
  public String getSearchToken() {
    try {
      String baseUrl = getHost(this.coveoConfig.getEnvironment()) + SEARCH_TOKEN_PATH;
      String url = baseUrl + "?organizationId=" + coveoConfig.getOrganizationId();
      HttpClient httpClient = HttpClientBuilder.create().build();
      HttpPost request = new HttpPost(url);
      request.addHeader(HttpHeaders.ACCEPT, "application/json");
      request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
      request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + coveoConfig.getApiKey());

      // Get user ids list
      List<UserId> userIds = getUserIds();

      SearchTokenPayload searchToken = SearchTokenPayload.builder().userIds(userIds).build();
      String payload = new Gson().toJson(searchToken, SearchTokenPayload.class);
      request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

      HttpResponse response = httpClient.execute(request);

      InputStreamReader is = new InputStreamReader((response.getEntity().getContent()));
      BufferedReader br = new BufferedReader(is);

      String output;
      StringBuilder json = new StringBuilder();
      while ((output = br.readLine()) != null) {
        json.append(output);
      }

      return json.toString();
    } catch (Exception e) {
      LOG.error("Error trying to get the search token", e);
    }

    return null;
  }

  /**
   * Get the session of the current user and get the userId and the groups of the
   * user.
   * 
   * @return a list of UserId that contains the userId and groups.
   * @throws RepositoryException if error occurs.
   */
  private List<UserId> getUserIds() throws RepositoryException {
    List<UserId> userIds = new ArrayList<>();

    ResourceResolver resourceResolver = resolverFactory.getThreadResourceResolver();
    Session session = resourceResolver.adaptTo(Session.class);

    userIds.add(new UserId(session.getUserID(), coveoConfig.getUserIdentityProvider(), "User"));

    UserManager userManager = resourceResolver.adaptTo(UserManager.class);
    Authorizable auth = userManager.getAuthorizable(session.getUserID());

    Iterator<Group> groups = auth.memberOf();

    for (Iterator<Group> i = groups; groups.hasNext();) {
      String groupName = i.next().getPrincipal().getName();
      userIds.add(new UserId(groupName, coveoConfig.getGroupIdentityProvider(), "Group"));
      userIds.add(new UserId(groupName, coveoConfig.getGroupIdentityProvider(), "VirtualGroup"));
    }

    return userIds;
  }

  /**
   * Get the host based on the environment.
   * 
   * @return coveo host
   */
  private String getHost(String environment) {
    switch (environment.toUpperCase()) {
      case "PRODUCTION":
        return "https://platform.cloud.coveo.com";
      case "HIPAA":
        return "https://platformhipaa.cloud.coveo.com";
      case "DEVELOPMENT":
        return "https://platformdev.cloud.coveo.com";
      case "STAGING":
        return "https://platformqa.cloud.coveo.com";
      default:
        return "https://platform.cloud.coveo.com";
    }
  }
}