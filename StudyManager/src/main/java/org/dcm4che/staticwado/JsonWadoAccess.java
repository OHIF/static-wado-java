package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

public class JsonWadoAccess {
    private static final Logger log = LoggerFactory.getLogger(JsonWadoAccess.class);
    private final File studyDir;

    private boolean encodeAsNumber = true;
    private boolean gzip = false;
    private boolean pretty = false;

    public JsonWadoAccess(File outputDir, String studyUid) {
        this.studyDir = new File(outputDir,studyUid);
        studyDir.mkdirs();
    }

    public JsonWadoAccess(File outputDir) {
        this.studyDir = outputDir;
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

    public OutputStream openForWrite(String dest) throws IOException {
        if( gzip ) {
            return new GZIPOutputStream(new FileOutputStream(new File(studyDir,dest+".gz")));
        } else {
            return new FileOutputStream(new File(studyDir,dest));
        }
    }

    /**
     * Writes JSON representation of the given attributes to the destination file.
     * @param dest is a file name to write to
     * @param attributes is an array of objects to write to the given location
     */
    public void writeJson(String dest, Attributes... attributes) {
        try(OutputStream fos = openForWrite(dest); JsonGenerator generator = createGenerator(fos)) {
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
        log.warn("Wrote to {} / {}", studyDir, dest);
    }

    public void setGzip(boolean b) {
        gzip = b;
    }

    public void setPretty(boolean b) {
        pretty = b;
    }
}
