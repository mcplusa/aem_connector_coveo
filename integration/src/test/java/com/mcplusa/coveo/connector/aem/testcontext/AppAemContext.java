package com.mcplusa.coveo.connector.aem.testcontext;

import com.day.cq.commons.Externalizer;
import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.day.cq.dam.api.Asset;
import com.day.cq.wcm.api.Page;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextCallback;
import java.io.IOException;
import javax.jcr.Session;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.engine.SlingRequestProcessor;

import static org.junit.Assert.assertNotNull;

public final class AppAemContext {

  public static final String CONTENT_ROOT = "/content";
  public static final String PAGE = CONTENT_ROOT + "/foobar";
  public static final String VIDEO = CONTENT_ROOT + "/video.mp4";
  public static final String IMAGE = CONTENT_ROOT + "/30941863.jpg";


  private AppAemContext() {
    // static methods only
  }

  public static AemContext newAemContext() {
    return new AemContext(new SetUpCallback(null));
  }

  public static AemContext newAemContext(AemContextCallback callback) {
    return new AemContext(new SetUpCallback(callback));
  }


  public static void loadPageSampleContent(AemContext context) {
    context.load().json("/content.json", AppAemContext.CONTENT_ROOT);
    context.registerService(Externalizer.class);
    context.registerService(RequestResponseFactory.class);
    context.registerService(SlingRequestProcessor.class);
    context.registerService(Session.class);
    Page currentPage = context.pageManager().getPage(AppAemContext.PAGE);
    assertNotNull(currentPage);
    context.currentPage(currentPage);
  }

  public static void loadVideoSampleContent(AemContext context) {
    context.load().json("/video.json", AppAemContext.CONTENT_ROOT);
    context.registerService(Externalizer.class);
    context.registerService(Asset.class);
    context.registerService(Session.class);

    Resource resource = context.resourceResolver().getResource(AppAemContext.VIDEO);
    assertNotNull(resource);
    context.currentResource(resource);
  }

  public static void loadImageSampleContent(AemContext context) {
    context.load().json("/image.json", AppAemContext.CONTENT_ROOT);
    context.registerService(Externalizer.class);
    context.registerService(Asset.class);
    context.registerService(Session.class);

    Resource resource = context.resourceResolver().getResource(AppAemContext.IMAGE);
    assertNotNull(resource);
    context.currentResource(resource);
  }

  /**
   * Custom set up rules required in all unit tests.
   */
  private static final class SetUpCallback implements AemContextCallback {

    private final AemContextCallback testCallback;

    public SetUpCallback(AemContextCallback testCallback) {
      this.testCallback = testCallback;
    }

    @Override
    public void execute(AemContext context) throws PersistenceException, IOException {

      // call test-specific callback first
      if (testCallback != null) {
        testCallback.execute(context);
      }

    }
  }

}
