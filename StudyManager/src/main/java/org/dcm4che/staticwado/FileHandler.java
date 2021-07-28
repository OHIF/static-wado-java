package org.dcm4che.staticwado;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/** The file handler knows about how to write to a given DICOMweb location tree.
 * It is designed to allow other output mechanisms to be used in place of the straight file operations
 */
public class FileHandler {

    private final File exportDir;
    private final File studyDir;
    private boolean gzip = false;

    public FileHandler(File exportDir, String studyUID) {
        this.exportDir = exportDir;
        this.studyDir = new File(exportDir,studyUID);
    }
    
    public FileHandler(File exportDir) {
        this.exportDir = exportDir;
        this.studyDir = exportDir;
    }


    public OutputStream openForWrite(String dest) throws IOException {
        if( dest.contains("/") ) {
            File destDir = new File(studyDir,dest).getParentFile();
            destDir.mkdirs();
        }
        if( gzip ) {
            return new GZIPOutputStream(new FileOutputStream(new File(studyDir,dest+".gz")));
        } else {
            return new FileOutputStream(new File(studyDir,dest));
        }
    }

    public void setGzip(boolean b) {
        gzip = b;
    }

    public File getStudyDir() {
        return studyDir;
    }
}
