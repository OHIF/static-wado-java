package org.dcm4che.staticwado;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The study manager handles DICOM studies in the static WADO tree.  
 * It uses various components to handle the generation.
 * <ul>
 *   <li>StudyMetadataEngine to generate the basic result tree</li>
 * </ul>
 */
public class StudyManager {
    private static final Logger log = LoggerFactory.getLogger(StudyManager.class);

    File bulkTempDir;
    File exportDir;
    Map<String, Attributes> studies = new HashMap<>();
    StudyMetadataEngine engine = new StudyMetadataEngine();
    private long lastLog;
    private List<String> addedStudies = new ArrayList<>();

    // 5 second relog
    private static final long RELOG_TIME = 1000L*1000L*1000L*5;

    /**
     * Imports a set of studies from the given directory input, and writes the data to the directory out.
     */
    public String[] importStudies(String... importDirs) {
        bulkTempDir = new File(exportDir,"temp/"+Math.random());
        bulkTempDir.mkdirs();
        for(String importDir : importDirs) {
            importFile(new File(importDir));
        }
        engine.finalizeStudy();
        FileHandler handler = new FileHandler(exportDir);
        JsonWadoAccess json = new JsonWadoAccess(handler);
        json.setPretty(true);
        json.writeJson("../studies.json", studies.values().toArray(Attributes[]::new));
        handler.setGzip(true);
        json.writeJson("../studies", studies.values().toArray(Attributes[]::new));
        return addedStudies.toArray(String[]::new);
    }

    private void importFile(File file) {
        if( file.isDirectory() ) {
            log.debug("Directory {} being recursed into", file);
            for(File subFile : file.listFiles()) {
                importFile(subFile);
            }
        } else {
            log.debug("File {} being examined", file);
            tryImportDicom(file);
        }
    }

    private void tryImportDicom(File file) {
        try {
            importDicom(file);
        } catch(DicomStreamException dse) {
            log.debug("Skipping non-dicom {}", file);
        } catch(IOException e) {
            log.warn("Caught exception:"+e);
        }
    }

    void importDicom(File file) throws IOException {
        Attributes attr = DicomAccess.readFile(file.getPath(), bulkTempDir);
        String studyUID = attr.getString(Tag.StudyInstanceUID);
        if( studyUID==null ) {
            if( file.getName().contains("DICOMDIR") ) return;
            log.warn("Null studyUID on {}", file);
            return;
        }
        if( engine.isNewStudy(studyUID) ) {
            engine.finalizeStudy();
            log.warn("Adding a new study UID {}", studyUID);
            Attributes studyAttr = engine.openNewStudy(attr, exportDir);
            studies.put(studyUID, studyAttr);
            addedStudies.add(studyUID);
            lastLog = System.nanoTime();
        } else if( System.nanoTime()-lastLog > RELOG_TIME ) {
            lastLog = System.nanoTime();
            log.warn("Continuing study {} on sop {}", studyUID, attr.getString(Tag.SOPInstanceUID));
        }
        engine.addObject(file, attr);
    }

    public void setExportDir(String name) {
        this.exportDir = new File(name+"/studies");
        JsonWadoAccess.readStudiesDirectory(studies,new File(name+"/studies.gz"));
    }

    public String getTransferSyntaxUid() {
        return engine.getTransferSyntaxUid();
    }

    public void setTransferSyntaxUid(String imageContentType) {
        engine.setTransferSyntaxUid(imageContentType);
    }

    public void setIncludeInstances(boolean val) {
        engine.setIncludeInstances(val);
    }

    public void setIncludeDeduplicated(boolean val) {
        engine.setIncludeDeduplicated(val);
    }
}