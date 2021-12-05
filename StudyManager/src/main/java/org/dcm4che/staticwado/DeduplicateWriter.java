package org.dcm4che.staticwado;

import org.dcm4che3.data.Attributes;

import java.util.function.BiConsumer;

public class DeduplicateWriter implements BiConsumer<SopId, Attributes> {
    public DeduplicateWriter(StudyManager studyManager) {
    }

    @Override
    public void accept(SopId sopId, Attributes attributes) {

    }
}
