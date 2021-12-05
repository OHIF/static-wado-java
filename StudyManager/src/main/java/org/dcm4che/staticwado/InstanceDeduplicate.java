package org.dcm4che.staticwado;

import org.dcm4che3.data.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.dcm4che.staticwado.DicomAccess.*;

import java.io.*;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * The study metadata engine has the internal knowledge on how to read DICOM files and add them to a static WADO
 * study metadata tree.
 */
public class InstanceDeduplicate implements BiConsumer<SopId,Attributes> {
    private static final Logger log = LoggerFactory.getLogger(InstanceDeduplicate.class);

    static final Set<String> saveOriginalSOPClasses = new HashSet<>();
    static {
        saveOriginalSOPClasses.add(UID.EnhancedSRStorage);
    }

    private final StudyManager callbacks;

    public InstanceDeduplicate(StudyManager callbacks) {
        this.callbacks = callbacks;
    }

    private static final List<TagLists> deduplicateSelectors = new ArrayList<>(Arrays.asList(
        TagLists.PATIENT,
        TagLists.STUDY,
        TagLists.SERIES,
        TagLists.IMAGE,
        TagLists.REFERENCE
    ));


    /**
     * Creates a de-duplicated copy of the source attributes.
     * This extracts Attributes instances
     * @param srcAttr
     * @return
     */
    public void accept(SopId id,Attributes srcAttr) {
        StudyData studyData = id.getStudyData();
        Attributes dedupped = new Attributes(srcAttr);
        for(TagLists selector : deduplicateSelectors) {
            Attributes testAttr = selector.select(srcAttr);
            String hashKey = hashAttributes(testAttr);
            testAttr.setString(DEDUPPED_CREATER, DEDUPPED_HASH, VR.ST, hashKey);
            selector.addTypeTo(testAttr);
            callbacks.extractConsumer.accept(id,testAttr);
            dedupped.removeSelected(testAttr.tags());
            addToStrings(dedupped,DEDUPPED_CREATER, DEDUPPED_REF, hashKey);
        }
        dedupped.setString(DEDUPPED_CREATER,DEDUPPED_TYPE,VR.CS, INSTANCE_TYPE);
        callbacks.deduplicatedConsumer.accept(id,dedupped);
    }

}
