package org.dcm4che.staticwado;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
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

    File bulkTempDir = new File("c:/dicomweb/bulkdata/temp");
    File exportDir;
    List<Attributes> studies = new ArrayList<>();
    StudyMetadataEngine engine = new StudyMetadataEngine();
        
    /**
     * Imports a set of studies from the given directory input, and writes the data to the directory out.
     */
    public List<Attributes> importStudies(String importDir) {
        File dir = new File(importDir);
        bulkTempDir = new File(exportDir,"bulkdata/"+Math.random());
        bulkTempDir.mkdirs();
        importFile(dir);
        engine.finalizeStudy();
        FileHandler handler = new FileHandler(exportDir);
        JsonWadoAccess json = new JsonWadoAccess(handler);
        json.setPretty(true);
        json.writeJson("studies.json", studies.toArray(Attributes[]::new));
        handler.setGzip(true);
        json.writeJson("studies.json", studies.toArray(Attributes[]::new));
        return studies;
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
        } catch(IOException e) {
            log.warn("Caught exception:"+e);
        }
    }

    void importDicom(File file) throws IOException {
        Attributes attr = DicomAccess.readFile(file.getPath(), bulkTempDir);
        String studyUID = attr.getString(Tag.StudyInstanceUID);
        if( studyUID==null ) {
            log.warn("Null studyUID on {}", file);
            return;
        }
        if( engine.isNewStudy(studyUID) ) {
            log.warn("Adding a new study UID {}", studyUID);
            engine.finalizeStudy();
            Attributes studyAttr = engine.openNewStudy(attr, exportDir);
            studies.add(studyAttr);
        }
        engine.addObject(attr);
    }

    public void setExportDir(String name) {
        this.exportDir = new File(name);
    }

}