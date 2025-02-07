package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;

import java.util.function.BiConsumer;

public class DeduplicateWriter implements BiConsumer<SopId, Attributes> {
    private final StudyManager callbacks;

    public DeduplicateWriter(StudyManager callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void accept(SopId sopId, Attributes attributes) {
        String hashValue = DicomAccess.getHash(attributes);
        JsonAccess.write(callbacks.fileHandler,
            callbacks.getDeduplicatedInstancesDir(sopId.getStudyInstanceUid()),
            callbacks.getDeduplicatedName(hashValue), false, attributes);
        callbacks.studyStats.add("WriteInstanceDeduplicate", 1000,
            "Write to {} single instance deduplicate for {}",
            hashValue, sopId.getStudyInstanceUid());
        StudyData studyData = sopId.getStudyData();
        callbacks.studyStats.add("ImportDeduplicate", 250, "Import deduplicate instance");
        studyData.addDeduplicated(attributes);
    }
}
