package com.mcplusa.coveo.connector.aem.indexing.contentbuilder;

/** Utility class to get file extension and type based on mimetype. */
public final class MimeTypes {
  public enum Application {
    ATOM_XML("application/atom+xml"),
    ATOMCAT_XML("application/atomcat+xml"),
    ECMASCRIPT("application/ecmascript"),
    JAVA_ARCHIVE("application/java-archive"),
    JAVASCRIPT("application/javascript"),
    JSON("application/json"),
    MP4("application/mp4"),
    OCTET_STREAM("application/octet-stream"),
    PKCS_10("application/pkcs10"),
    PKCS_7_MIME("application/pkcs7-mime"),
    PKCS_7_SIGNATURE("application/pkcs7-signature"),
    PKCS_8("application/pkcs8"),
    POSTSCRIPT("application/postscript"),
    RDF_XML("application/rdf+xml"),
    RSS_XML("application/rss+xml"),
    RTF("application/rtf"),
    SMIL_XML("application/smil+xml"),
    X_FONT_OTF("application/x-font-otf"),
    X_FONT_TTF("application/x-font-ttf"),
    X_FONT_WOFF("application/x-font-woff"),
    X_PKCS_12("application/x-pkcs12"),
    X_SHOCKWAVE_FLASH("application/x-shockwave-flash"),
    X_SILVERLIGHT_APP("application/x-silverlight-app"),
    XHTML_XML("application/xhtml+xml"),
    XML("application/xml"),
    XML_DTD("application/xml-dtd"),
    XSLT_XML("application/xslt+xml"),
    ZIP("application/zip"),
    IMAGE_SET("Multipart/Related; type=application/x-ImageSet");

    public final String type;

    private Application(String type) {
      this.type = type;
    }

    /** 
     * Check if value match with enum labels.
     * @param label label value 
     * @return Enum with all values
     */
    public static Application valueOfLabel(String label) {
      for (Application e : values()) {
        if (e.type.equals(label)) {
          return e;
        }
      }
      return null;
    }

    /**
     * Check if mimetype is of type Application.
     *
     * @param mimeType value.
     * @return Application enum if true, otherwise will be null.
     */
    public static Application isApp(String mimeType) {
      if (Application.valueOfLabel(mimeType) != null) {
        return Application.valueOfLabel(mimeType);
      }

      return null;
    }
  }

  public enum Audio {
    MIDI("audio/midi"),
    MP4("audio/mp4"),
    MPEG("audio/mpeg"),
    OGG("audio/ogg"),
    WEBM("audio/webm"),
    X_AAC("audio/x-aac"),
    X_AIFF("audio/x-aiff"),
    X_MPEGURL("audio/x-mpegurl"),
    X_MS_WMA("audio/x-ms-wma"),
    X_WAV("audio/x-wav");

    public final String type;

    private Audio(String type) {
      this.type = type;
    }

    /** 
     * Check if value match with enum labels.
     * @param label label value 
     * @return Enum with all values
     */
    public static Audio valueOfLabel(String label) {
      for (Audio e : values()) {
        if (e.type.equals(label)) {
          return e;
        }
      }
      return null;
    }

    /**
     * Check if mimetype is of type Audio.
     *
     * @param mimeType value.
     * @return Audio enum if true, otherwise will be null.
     */
    public static Audio isAudio(String mimeType) {
      if (Audio.valueOfLabel(mimeType) != null) {
        return Audio.valueOfLabel(mimeType);
      }

      return null;
    }
  }

  public enum Image {
    BMP("image/bmp"),
    GIF("image/gif"),
    JPEG("image/jpeg"),
    PNG("image/png"),
    SVG_XML("image/svg+xml"),
    TIFF("image/tiff"),
    WEBP("image/webp");

    public final String type;

    private Image(String type) {
      this.type = type;
    }

    /** 
     * Check if value match with enum labels.
     * @param label label value 
     * @return Enum with all values
     */
    public static Image valueOfLabel(String label) {
      for (Image e : values()) {
        if (e.type.equals(label)) {
          return e;
        }
      }
      return null;
    }

    /**
     * Check if mimetype is of type Image.
     *
     * @param mimeType value.
     * @return Image enum if true, otherwise will be null.
     */
    public static Image isImage(String mimeType) {
      if (Image.valueOfLabel(mimeType) != null) {
        return Image.valueOfLabel(mimeType);
      }

      return null;
    }
  }

  public enum Text {
    CSS("text/css"),
    CSV("text/csv"),
    HTML("text/html"),
    PLAIN("text/plain"),
    RICH_TEXT("text/richtext"),
    SGML("text/sgml"),
    YAML("text/yaml");

    public final String type;

    private Text(String type) {
      this.type = type;
    }

    /** 
     * Check if value match with enum labels.
     * @param label label value 
     * @return Enum with all values
     */
    public static Text valueOfLabel(String label) {
      for (Text e : values()) {
        if (e.type.equals(label)) {
          return e;
        }
      }
      return null;
    }

    /**
     * Check if mimetype is of type Image.
     *
     * @param mimeType value.
     * @return Image enum if true, otherwise will be null.
     */
    public static Text isText(String mimeType) {
      if (Text.valueOfLabel(mimeType) != null) {
        return Text.valueOfLabel(mimeType);
      }

      return null;
    }
  }

  public enum Video {
    THREEGPP("video/3gpp"),
    H264("video/h264"),
    MP4("video/mp4"),
    MPEG("video/mpeg"),
    OGG("video/ogg"),
    QUICKTIME("video/quicktime"),
    WEBM("video/webm");

    public final String type;

    private Video(String type) {
      this.type = type;
    }

    /** 
     * Check if value match with enum labels.
     * @param label label value 
     * @return Enum with all values
     */
    public static Video valueOfLabel(String label) {
      for (Video e : values()) {
        if (e.type.equals(label)) {
          return e;
        }
      }
      return null;
    }

    /**
     * Check if mimetype is of type Image.
     *
     * @param mimeType value.
     * @return Image enum if true, otherwise will be null.
     */
    public static Video isVideo(String mimeType) {
      if (Video.valueOfLabel(mimeType) != null) {
        return Video.valueOfLabel(mimeType);
      }

      return null;
    }
  }

  /**
   * Get file type based on mime.
   *
   * @param mimeType mime of the file.
   * @return File type
   */
  public static String getType(String mimeType) {
    if (MimeTypes.Application.isApp(mimeType) != null) {
      return "Application";
    } else if (MimeTypes.Audio.isAudio(mimeType) != null) {
      return "Audio";
    } else if (MimeTypes.Image.isImage(mimeType) != null) {
      return "Image";
    } else if (MimeTypes.Text.isText(mimeType) != null) {
      return "Text";
    } else if (MimeTypes.Video.isVideo(mimeType) != null) {
      return "Video";
    } else {
      return "";
    }
  }
}
