package com.mcplusa.coveo.connector.aem.contentbuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.contentbuilder.DAMAssetContentBuilder;
import com.mcplusa.coveo.connector.aem.service.CoveoService;
import com.mcplusa.coveo.connector.aem.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit.AemContext;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Base64;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DAMAssetContentBuilderTest {

  @Rule public AemContext context = AppAemContext.newAemContext();

  @Test
  public void testCreateVideo() {
    AppAemContext.loadVideoSampleContent(context);
    DAMAssetContentBuilder builder = new DAMAssetContentBuilder();
    mockReferences(builder);

    IndexEntry entry = builder.create(AppAemContext.VIDEO, context.resourceResolver(), true);
    assertNotNull(entry);
    assertEquals(AppAemContext.VIDEO, entry.getPath());
    assertEquals("asset", entry.getType());

    assertEquals("What is a Neural Network?", entry.getContent("title", String.class));
    assertEquals(
        "A channel about animating math, in all senses of the word animate.",
        entry.getContent("description", String.class));
    assertEquals("Video", entry.getContent("documenttype", String.class));
    assertEquals("admin", entry.getContent("author", String.class));
    assertEquals(1234L, entry.getContent("duration", String.class));

    String encodedData = entry.getContent("content", String.class);
    assertNotNull(encodedData);
    String data = new String(Base64.getDecoder().decode(encodedData));
    assertEquals(
        "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-16\"><base target=\"_blank\" href=\"/content/video.mp4\"></head><body><div style=\"margin: 20px auto; width: 640px\"><div><video width=\"640\" controls><source src=\"/content/video.mp4\" type=\"video/mp4\">Your browser does not support the video tag.</video></div><div style=\"border: 1px solid #CCC; padding:10px; font-family: Helvetica\"><h3>What is a Neural Network?</h3><p style=\"text-align: justify;\">A channel about animating math, in all senses of the word animate.</p></div></div></body></html>",
        data);
  }

  @Test
  public void testCreateVideoWithoutContent() {
    AppAemContext.loadVideoSampleContent(context);
    DAMAssetContentBuilder builder = new DAMAssetContentBuilder();
    mockReferences(builder);

    boolean includeContent = false;
    IndexEntry entry =
        builder.create(AppAemContext.VIDEO, context.resourceResolver(), includeContent);
    assertNotNull(entry);
    assertEquals(AppAemContext.VIDEO, entry.getPath());
    assertEquals("asset", entry.getType());
    assertEquals("/content/video.mp4", entry.getDocumentId());
    assertEquals(false, entry.getContent().containsKey("title"));
    assertEquals(false, entry.getContent().containsKey("description"));
    assertEquals(false, entry.getContent().containsKey("documenttype"));
    assertEquals(false, entry.getContent().containsKey("author"));
    assertEquals(false, entry.getContent().containsKey("duration"));
    assertEquals(false, entry.getContent().containsKey("content"));
  }

  @Test
  public void testCreateImage() {
    AppAemContext.loadImageSampleContent(context);
    DAMAssetContentBuilder builder = new DAMAssetContentBuilder();
    mockReferences(builder);

    IndexEntry entry = builder.create(AppAemContext.IMAGE, context.resourceResolver(), true);
    assertNotNull(entry);
    assertEquals(AppAemContext.IMAGE, entry.getPath());
    assertEquals("asset", entry.getType());

    assertEquals("30941863.jpg", entry.getContent("title", String.class));
    assertEquals("Image", entry.getContent("documenttype", String.class));
    assertEquals("admin", entry.getContent("author", String.class));
    assertEquals(864L, entry.getContent("width", String.class));
    assertEquals(1080L, entry.getContent("height", String.class));
  }

  @Test
  public void testCreatePdf() {
    AppAemContext.loadPdfSampleContent(context);
    DAMAssetContentBuilder builder = new DAMAssetContentBuilder();
    mockReferences(builder);

    IndexEntry entry = builder.create(AppAemContext.PDF, context.resourceResolver(), true);
    assertNotNull(entry);
    assertEquals(AppAemContext.PDF, entry.getPath());
    assertEquals("asset", entry.getType());

    assertEquals("The Docker Book", entry.getContent("title", String.class));
    assertEquals("admin", entry.getContent("author", String.class));

    assertEquals(false, entry.getContent().containsKey("notexist"));
    assertEquals(false, entry.getContent().containsKey("nullprop"));
  }

  @Test
  public void testCreateWithDuplicatedKeys() {
    AppAemContext.loadImageSampleContent(context);
    DAMAssetContentBuilder builder =
        new DAMAssetContentBuilder() {
          @Override
          public String[] getIndexRules(String primaryType) {
            return new String[] {"dc:title", "jcr:lastModified", "jcr:lastModified"};
          }
        };
    mockReferences(builder);

    IndexEntry entry = builder.create(AppAemContext.IMAGE, context.resourceResolver(), true);
    assertNotNull(entry);
    assertEquals(AppAemContext.IMAGE, entry.getPath());
    assertEquals("asset", entry.getType());
  }

  private void mockReferences(DAMAssetContentBuilder instance) {
    try {
      Field resolverFactoryField = DAMAssetContentBuilder.class.getDeclaredField("resolverFactory");
      resolverFactoryField.setAccessible(true);
      resolverFactoryField.set(instance, Mockito.mock(ResourceResolverFactory.class));

      Field coveoServiceField = DAMAssetContentBuilder.class.getDeclaredField("coveoService");
      coveoServiceField.setAccessible(true);
      coveoServiceField.set(instance, Mockito.mock(CoveoService.class));
    } catch (NoSuchFieldException
        | SecurityException
        | IllegalArgumentException
        | IllegalAccessException ex) {
      Logger.getLogger(DAMAssetContentBuilder.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
}
