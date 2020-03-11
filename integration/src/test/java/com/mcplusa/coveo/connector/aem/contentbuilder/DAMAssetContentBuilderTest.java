package com.mcplusa.coveo.connector.aem.contentbuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Base64;

import com.mcplusa.coveo.connector.aem.indexing.IndexEntry;
import com.mcplusa.coveo.connector.aem.indexing.contentbuilder.DAMAssetContentBuilder;
import com.mcplusa.coveo.connector.aem.testcontext.AppAemContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import io.wcm.testing.mock.aem.junit.AemContext;

@RunWith(MockitoJUnitRunner.class)
public class DAMAssetContentBuilderTest {

    @Rule
    public AemContext context = AppAemContext.newAemContext();

    @Test
    public void testCreateVideo() {
        AppAemContext.loadVideoSampleContent(context);
        DAMAssetContentBuilder builder = new DAMAssetContentBuilder();

        IndexEntry entry = builder.create(AppAemContext.VIDEO, context.resourceResolver());
        assertNotNull(entry);
        assertEquals(AppAemContext.VIDEO, entry.getPath());
        assertEquals("asset", entry.getType());

        assertEquals("What is a Neural Network?", entry.getContent("title", String.class));
        assertEquals("A channel about animating math, in all senses of the word animate.",
                entry.getContent("description", String.class));
        assertEquals("Video", entry.getContent("documenttype", String.class));
        assertEquals("admin", entry.getContent("author", String.class));
        assertEquals(1234l, entry.getContent("duration", String.class));

        String encodedData = entry.getContent("content", String.class);
        assertNotNull(encodedData);
        String data = new String(Base64.getDecoder().decode(encodedData));
        assertEquals(
                "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-16\"><base target=\"_blank\" href=\"/content/video.mp4\"></head><body><div style=\"margin: 20px auto; width: 640px\"><div><video width=\"640\" controls><source src=\"/content/video.mp4\" type=\"video/mp4\">Your browser does not support the video tag.</video></div><div style=\"border: 1px solid #CCC; padding:10px; font-family: Helvetica\"><h3>What is a Neural Network?</h3><p style=\"text-align: justify;\">A channel about animating math, in all senses of the word animate.</p></div></div></body></html>",
                data);
    }

    @Test
    public void testCreateImage() {
        AppAemContext.loadImageSampleContent(context);
        DAMAssetContentBuilder builder = new DAMAssetContentBuilder();

        IndexEntry entry = builder.create(AppAemContext.IMAGE, context.resourceResolver());
        assertNotNull(entry);
        assertEquals(AppAemContext.IMAGE, entry.getPath());
        assertEquals("asset", entry.getType());

        assertEquals("30941863.jpg", entry.getContent("title", String.class));
        assertEquals("Image", entry.getContent("documenttype", String.class));
        assertEquals("admin", entry.getContent("author", String.class));
        assertEquals(864l, entry.getContent("width", String.class));
        assertEquals(1080l, entry.getContent("height", String.class));
    }
}