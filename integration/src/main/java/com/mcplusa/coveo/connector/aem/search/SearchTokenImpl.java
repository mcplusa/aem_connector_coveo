package com.mcplusa.coveo.connector.aem.search;

import com.google.gson.Gson;
import com.mcplusa.coveo.connector.aem.search.model.SearchTokenPayload;
import com.mcplusa.coveo.connector.aem.search.model.UserId;
import com.mcplusa.coveo.connector.aem.service.CoveoHostConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service
public class SearchTokenImpl implements SearchToken {

  private static final Logger LOG = LoggerFactory.getLogger(SearchTokenImpl.class);
  private static final String SEARCH_TOKEN_ENDPOINT = "https://platform.cloud.coveo.com/rest/search/v2/token";

  @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
  protected CoveoHostConfiguration coveoConfig;

  @Override
  public String getSearchToken() {
    try {
      String url = SEARCH_TOKEN_ENDPOINT + "?organizationId=" + coveoConfig.getOrganizationId();
      HttpClient httpClient = HttpClientBuilder.create().build();
      HttpPost request = new HttpPost(url);
      request.addHeader(HttpHeaders.ACCEPT, "application/json");
      request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
      request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + coveoConfig.getApiKey());


      UserId userId = new UserId("test@example.com", "Email Security Provider", "User");
      SearchTokenPayload payload = SearchTokenPayload.builder().userIds(Arrays.asList(userId)).build();
      request.setEntity(
          new StringEntity(new Gson().toJson(payload, SearchTokenPayload.class), ContentType.APPLICATION_JSON));

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

}