package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;

public class JsonWadoAccess {
    private static final Logger log = LoggerFactory.getLogger(JsonWadoAccess.class);
    private final FileHandler handler;

    private boolean encodeAsNumber = true;
    private boolean pretty = false;

    public JsonWadoAccess(FileHandler handler) {
        this.handler = handler;
    }

    public JsonGenerator createGenerator(OutputStream out) {
        Map<String, ?> conf = new HashMap<String, Object>(2);
        if( pretty ) {
            conf.put(JsonGenerator.PRETTY_PRINTING, null);
        }
        return Json.createGeneratorFactory(conf).createGenerator(out);
    }

    public JSONWriter createWriter(JsonGenerator generator) {
        JSONWriter jsonWriter = new JSONWriter(generator);
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
     * @param dest is a file name to write to
     * @param attributes is an array of objects to write to the given location
     */
    public void writeJson(String dest, Attributes... attributes) {
        try(OutputStream fos = handler.openForWrite(dest); JsonGenerator generator = createGenerator(fos)) {
            generator.writeStartArray();
            for(Attributes attr : attributes) {
                JSONWriter writer = createWriter(generator);
                writer.write(attr);
                generator.flush();
                fos.write('\n');
            }
            generator.writeEnd();
        } catch(IOException e) {
            log.warn("Unable to write file {}", dest, e);
        }
        log.debug("Wrote to {} / {}", handler.getStudyDir(), dest);
    }

    public static List<Attributes> read(File location) throws IOException {
        List<Attributes> ret = new ArrayList<>();
        try(InputStream is = new FileInputStream(location); GZIPInputStream gzip = new GZIPInputStream(is)) {
            JsonParser parser = Json.createParser(gzip);
            new JSONReader(parser).readDatasets((fmi,attr) -> {
                ret.add(attr);
            });
        } catch(FileNotFoundException e) {
            log.warn("Studies list not found, starting fresh");
            return Collections.emptyList();
        }
        return ret;
    }

    public static void readStudiesDirectory(Map<String,Attributes> studies, File file) {
        try {
            List<Attributes> studiesArr = JsonWadoAccess.read(file);
            for (Attributes attr : studiesArr) {
                String studyUID = attr.getString(Tag.StudyInstanceUID);
                studies.put(studyUID, attr);
            }
        } catch(IOException e) {
            log.warn("No studies directory read for {}", file);
        }
    }



    public void setPretty(boolean b) {
        pretty = b;
    }
}
