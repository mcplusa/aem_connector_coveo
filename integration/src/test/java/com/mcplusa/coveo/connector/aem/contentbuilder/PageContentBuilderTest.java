package com.mcplusa.coveo.connector.aem.contentbuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.contentbuilder.PageContentBuilder;
import com.mcplusa.coveo.connector.aem.service.CoveoService;
import com.mcplusa.coveo.connector.aem.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit.AemContext;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PageContentBuilderTest {

  @Rule public AemContext context = AppAemContext.newAemContext();

  @Before
  public void setUp() {
    AppAemContext.loadPageSampleContent(context);
  }

  @Test
  public void testCreate() {
    PageContentBuilder builder =
        new PageContentBuilder() {
          @Override
          public String[] getIndexRules(String primaryType) {
            return new String[] {
              "foobar", "childFoo", "boolean", "multi", "cascade", "double", "ipsum", "cq:template"
            };
          }
        };
    mockReferences(builder);

    boolean includeContent = true;
    IndexEntry entry =
        builder.create(AppAemContext.PAGE, context.resourceResolver(), includeContent);
    assertNotNull(entry);
    assertEquals(AppAemContext.PAGE, entry.getPath());
    assertEquals("page", entry.getType());
    assertEquals("/foo/template", entry.getContent().get("cq:template"));
    assertEquals("foobarValue", entry.getContent().get("foobar"));
    assertEquals("childValue", entry.getContent().get("childFoo"));

    assertTrue(entry.getContent("boolean", Boolean.class));

    String[] multi = entry.getContent("multi", String[].class);
    assertEquals(2, multi.length);
    assertEquals("foobar", multi[0]);
    assertEquals("ipsum", multi[1]);

    Object[] cascade = entry.getContent("cascade", Object[].class);
    assertEquals(3, cascade.length);
    assertEquals(String.class, cascade[0].getClass());
    assertEquals(String.class, cascade[1].getClass());
    assertEquals(String.class, cascade[2].getClass());

    assertEquals(false, entry.getContent().containsKey("notexist"));
    assertEquals(false, entry.getContent().containsKey("nullprop"));
  }

  @Test
  public void testCreateWithoutContent() {
    PageContentBuilder builder =
        new PageContentBuilder() {
          @Override
          public String[] getIndexRules(String primaryType) {
            return new String[] {
              "foobar", "childFoo", "boolean", "multi", "cascade", "double", "ipsum", "cq:template"
            };
          }
        };
    mockReferences(builder);
    boolean includeContent = false;
    IndexEntry entry =
        builder.create(AppAemContext.PAGE, context.resourceResolver(), includeContent);
    assertNotNull(entry);
    assertEquals(AppAemContext.PAGE, entry.getPath());
    assertEquals("page", entry.getType());
    assertEquals("/content/foobar", entry.getDocumentId());
    assertEquals(false, entry.getContent().containsKey("cq:template"));
    assertEquals(false, entry.getContent().containsKey("foobar"));
    assertEquals(false, entry.getContent().containsKey("childFoo"));
    assertEquals(false, entry.getContent().containsKey("notexist"));
    assertEquals(false, entry.getContent().containsKey("nullprop"));
  }

  private void mockReferences(PageContentBuilder instance) {
    try {
      Field field = PageContentBuilder.class.getDeclaredField("requestResponseFactory");
      field.setAccessible(true);
      field.set(instance, Mockito.mock(RequestResponseFactory.class));

      Field field2 = PageContentBuilder.class.getDeclaredField("requestProcessor");
      field2.setAccessible(true);
      field2.set(instance, Mockito.mock(SlingRequestProcessor.class));

      Field field3 = PageContentBuilder.class.getDeclaredField("resolverFactory");
      field3.setAccessible(true);
      field3.set(instance, Mockito.mock(ResourceResolverFactory.class));

      Field coveoServiceField = PageContentBuilder.class.getDeclaredField("coveoService");
      coveoServiceField.setAccessible(true);
      coveoServiceField.set(instance, Mockito.mock(CoveoService.class));
    } catch (NoSuchFieldException
        | SecurityException
        | IllegalArgumentException
        | IllegalAccessException ex) {
      Logger.getLogger(PageContentBuilderTest.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
}
