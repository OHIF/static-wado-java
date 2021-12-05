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
public class CompleteStudyHandler {
    private static final Logger log = LoggerFactory.getLogger(CompleteStudyHandler.class);

    File bulkTempDir;
    File exportDir;
    Map<String, Attributes> studies = new HashMap<>();
    private long lastLog;
    private List<String> addedStudies = new ArrayList<>();

    // 5 second relog
    private static final long RELOG_TIME = 1000L*1000L*1000L*5;

    private final StudyManager callbacks;

    public CompleteStudyHandler(StudyManager callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * Called when the study data is complete - causes things to be written out etc.
     * @param data
     */
    public void completeStudy(StudyData data) {
        data.writeDeduplicatedGroup(callbacks.getDeduplicatedDir(data.getStudyUid()),
            (hashValue) -> hashValue, false);
        data.writeStudyMetadata();
    }

    /** Called to create a new StudyData item - reads stuff in, as needed */
    public StudyData createStudy(SopId id) {
        StudyData ret = new StudyData(id,callbacks);
        ret.readDeduplicatedGroup();
        ret.readDeduplicatedInstances();
        return ret;
    }
}