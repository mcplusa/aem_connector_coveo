package com.mcplusa.coveo.connector.aem.search.model;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * SearchTokenPayload.
 */
@Getter
@Setter
@Builder
public class SearchTokenPayload {

  /**
   * The list of RestUserId to associate to the search token. The RestUserId model
   * exposes the following attributes. -name (string, required) Specifies the name
   * of the user ID (e.g., john_doe@some-domain.com). -provider (string, required)
   * Specifies the security provider of the user ID (e.g., Email Security
   * Provider). -type (string, optional) Specifies the type of user ID. eg: User
   */
  private List<UserId> userIds;

  /**
   * Specifies the userGroups to pass when logging usage analytics search events.
   * This information is leveraged in the Analytics section of the Coveo Cloud
   * administration console. User groups can also be used in query pipeline
   * condition statements (e.g., when $groups contains \"Employees\").
   */
  private List<String> userGroups; // optional

  /**
   * Specifies the display name of the user to associate this search token with.
   */
  private String userDisplayName; // optional

  /**
   * The search hub is an important parameter for the analytics service, the
   * Coveo™ Machine Learning service and the query pipeline.
   */
  private String searchHub; // optional

  /**
   * Specifies the query pipeline to use when performing queries using this search
   * token. If you specify a value for this parameter, the query pipeline you
   * specify takes precedence over the possible output of all other query pipeline
   * routing mechanisms when using this search token.
   */
  private String pipeline; // optional

  /**
   * Specifies the query expression filter to add to queries when performing
   * queries using the search token. You can use this value to generate tokens
   * that can only query a subset of the items in an index
   */
  private String filter; // optional

  /**
   * Specifies the number of milliseconds the search token will remain valid for
   * once it has been created. Minimum value is 900000 (i.e., 15 minutes).
   * Maximum/default value is 86400000 (i.e., 24 hours).
   */
  private Integer validFor; // optional
}
