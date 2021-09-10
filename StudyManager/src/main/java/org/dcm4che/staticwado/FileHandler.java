package org.dcm4che.staticwado;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPOutputStream;

/** The file handler knows about how to write to a given DICOMweb location tree.
 * It is designed to allow other output mechanisms to be used in place of the straight file operations
 */
public class FileHandler {

    private final File exportDir;
    private final File studyDir;
    private boolean gzip = true;

    public FileHandler(File exportDir, String studyUID) {
        this.exportDir = exportDir;
        this.studyDir = new File(exportDir,studyUID);
    }
    
    public FileHandler(File exportDir) {
        this.exportDir = exportDir;
        this.studyDir = exportDir;
    }

    public void mkdirs(String dest) {
        if( dest.contains("/") ) {
            File destDir = new File(studyDir,dest).getParentFile();
            destDir.mkdirs();
        } else {
            studyDir.mkdirs();
        }
    }

    /** Opens the given destination file for writing, as either gzip or non-gzip, AND deletes any older version of the wrong type (gzip or non-gzip). */
    public OutputStream openForWrite(String dest) throws IOException {
        mkdirs(dest);
        if( gzip ) {
            new File(studyDir,dest).delete();
            return new GZIPOutputStream(new FileOutputStream(new File(studyDir,dest+".gz")));
        } else {
            new File(studyDir,dest+".gz").delete();
            return new FileOutputStream(new File(studyDir,dest));
        }
    }

    public void setGzip(boolean b) {
        gzip = b;
    }

    public File getStudyDir() {
        return studyDir;
    }

    /** Generates a hash of a given path, generation levels sub-directories for it */
    public String hashOf(File file, long offset, long length) {
        try (InputStream is = new FileInputStream(file)){
            is.skip(offset);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            byte[] data = new byte[16384];
            long cumulativeLength = 0;
            int len = 0;
            while(len!=-1 && cumulativeLength < length) {
                digest.update(data,0,len);
                int readLen = (int) Math.min(length-cumulativeLength, data.length);
                len = is.read(data,0,readLen);
            }
            String sha1 = new BigInteger(1, digest.digest()).toString(32);
            return sha1.substring(0,2)+"/"+sha1.substring(2);
        } catch(NoSuchAlgorithmException e) {
            throw new Error(e);
        } catch (IOException e) {
            e.printStackTrace();
            return file.getName();
        }
    }
}
