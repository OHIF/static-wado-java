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
    private boolean gzip = false;

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

    public OutputStream openForWrite(String dest) throws IOException {
        mkdirs(dest);
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

    /** Moves the given file to the destination location */
    public void move(File file, String dest) {
        mkdirs(dest);
        file.renameTo(new File(studyDir,dest));
        file.delete();
    }

    /** Generates a hash of a given path, generation levels sub-directories for it */
    public String hashOf(File file) {
        try (InputStream is = new FileInputStream(file)){
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            byte[] data = new byte[16384];
            int len = 0;
            while(len!=-1) {
                digest.update(data,0,len);
                len = is.read(data);
            }
            String sha1 = String.format("%040x", new BigInteger(1, digest.digest()));
            return sha1.substring(0,4)+"/"+sha1.substring(4,8) + "/"+sha1.substring(8);
        } catch(NoSuchAlgorithmException e) {
            throw new Error(e);
        } catch (IOException e) {
            e.printStackTrace();
            return file.getName();
        }
    }
}
