package org.dcm4che.staticwado;

import org.junit.jupiter.api.Test;

public class StudyManagerTest {
    @Test
    void convertStaticDicomDirTest() {
        StudyManager manager = new StudyManager();
        manager.setExportDir("c:/dicomweb/studies");
        manager.importStudies("c:/dicom/CTLymphNodes");
    }
}
