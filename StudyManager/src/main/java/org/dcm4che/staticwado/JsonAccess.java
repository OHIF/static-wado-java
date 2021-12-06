package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;

public class JsonAccess {
  private static final Logger log = LoggerFactory.getLogger(JsonAccess.class);

  private static boolean encodeAsNumber = true;
  private static boolean pretty = false;

  public static JsonGenerator createGenerator(OutputStream out) {
    Map<String, ?> conf = new HashMap<String, Object>(2);
    if (pretty) {
      conf.put(JsonGenerator.PRETTY_PRINTING, null);
    }
    return Json.createGeneratorFactory(conf).createGenerator(out);
  }

  public static org.dcm4che3.json.JSONWriter createWriter(JsonGenerator generator) {
    org.dcm4che3.json.JSONWriter jsonWriter = new org.dcm4che3.json.JSONWriter(generator);
    if (encodeAsNumber) {
      jsonWriter.setJsonType(VR.DS, JsonValue.ValueType.NUMBER);
      jsonWriter.setJsonType(VR.IS, JsonValue.ValueType.NUMBER);
      jsonWriter.setJsonType(VR.SV, JsonValue.ValueType.NUMBER);
      jsonWriter.setJsonType(VR.UV, JsonValue.ValueType.NUMBER);
    }
    return jsonWriter;
  }

  /**
   * Writes JSON representation of the given attributes to the destination file.
   *
   * @param dest       is a file name to write to
   * @param attributes is an array of objects to write to the given location
   */
  public static void write(FileHandler handler, String dir, String dest, Attributes... attributes) {
    log.debug("Writing {} instances to {}", attributes.length, dest);
    try (OutputStream fos = handler.openForWrite(dir, dest, true); JsonGenerator generator = createGenerator(fos)) {
      generator.writeStartArray();
      for (Attributes attr : attributes) {
        org.dcm4che3.json.JSONWriter writer = createWriter(generator);
        writer.write(attr);
        log.debug("Wrote {} tags", attr.size());
        generator.flush();
        fos.write('\n');
      }
      generator.writeEnd();
    } catch (IOException e) {
      log.warn("Unable to write file {}", dest, e);
    }
    log.debug("Wrote to {} / {}", dir, dest);
  }

  public static void writeSingle(FileHandler handler, String dir, String dest, Attributes data) {
    try (OutputStream fos = handler.openForWrite(dir, dest, true); JsonGenerator generator = createGenerator(fos)) {
      org.dcm4che3.json.JSONWriter writer = createWriter(generator);
      writer.write(data);
      log.debug("Wrote {} tags", data.size());
      generator.flush();
      fos.write('\n');
    } catch (IOException e) {
      log.warn("Unable to write file {}", dest, e);
    }
    log.debug("Wrote single to {} / {}", dir, dest);
  }

  public static List<Attributes> read(FileHandler handler, String dir, String name) throws IOException {
    List<Attributes> ret = new ArrayList<>();
    try (InputStream is = handler.read(dir, name); GZIPInputStream gzip = new GZIPInputStream(is)) {
      JsonParser parser = Json.createParser(gzip);
      new JSONReader(parser).readDatasets((fmi, attr) -> {
        ret.add(attr);
      });
      return ret;
    }
  }

  public void setPretty(boolean b) {
    pretty = b;
  }
}
