<%@include file="/libs/foundation/global.jsp"%>
<%@ page import="org.apache.sling.commons.json.io.*,org.w3c.dom.*" %>
<%
com.mcplusa.coveo.connector.aem.search.SearchToken searchToken = sling.getService(com.mcplusa.coveo.connector.aem.search.SearchToken.class);
String myJSON = searchToken.getSearchToken() ; 
    
//Send the data back to the client 
JSONWriter writer = new JSONWriter(response.getWriter());
writer.object();
writer.key("json");
writer.value(myJSON);
   
writer.endObject();
%>