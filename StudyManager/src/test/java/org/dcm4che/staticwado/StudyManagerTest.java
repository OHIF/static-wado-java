package org.dcm4che.staticwado;

import org.junit.jupiter.api.Test;

public class StudyManagerTest {
    @Test
    void convertStaticDicomDirTest() {
        StudyManager manager = new StudyManager();
        manager.setExportDir("/dicomweb/studies");
        manager.importStudies("/dicom/2_skull_ct/DICOM/I0");
    }

    @Test
    void convertStaticDicomDirMultiframeTest() {
        StudyManager manager = new StudyManager();
        manager.setExportDir("/dicomweb/studies");
        manager.importStudies("/dicom/multiframes/US-PAL-8-10x-echo");
    }
}
