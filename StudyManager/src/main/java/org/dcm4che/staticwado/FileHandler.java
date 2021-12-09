package org.dcm4che.staticwado;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The file handler knows about how to write to a given DICOMweb location tree.
 * It is designed to allow other output mechanisms to be used in place of the straight file operations
 */
public class FileHandler {
  private static Logger log = LoggerFactory.getLogger(FileHandler.class);

  StudyManager callbacks;

  public FileHandler(StudyManager callbacks) {
    this.callbacks = callbacks;
  }

  /**
   * Opens the given destination file for writing, as either gzip or non-gzip, AND deletes any older version of the wrong type (gzip or non-gzip).
   */
  public OutputStream openForWrite(String dir, String name, boolean gzip, boolean overwrite) throws IOException {
    if (name.endsWith(".gz")) {
      name = name.substring(0, name.length() - 3);
      gzip = true;
    }
    File fullName = new File(dir, name).getCanonicalFile();
    File gzipName = new File(dir, name + ".gz");
    fullName.getParentFile().mkdirs();
    File finalName = gzip ? gzipName : fullName;
    (gzip ? fullName : gzipName).delete();
    if (!overwrite && finalName.canRead()) {
      throw new FileAlreadyExistsException("File " + finalName + " already exists");
    }
    File tempFile = new File(fullName.getParentFile(), "temp-" + Math.random());
    FileOutputStream fos = new FileOutputStream(tempFile);
    OutputStream os = gzip ? new GZIPOutputStream(fos) : fos;
    return new FilterOutputStream(os) {
      boolean closed = false;

      @Override
      public void close() throws IOException {
        if (closed) {
          return;
        }
        super.close();
        safeClose(os);
        safeClose(fos);
        closed = true;
        renameTo(tempFile, finalName, overwrite);
      }
    };
  }

  /**
   * Generates a hash of a given path, generation levels sub-directories for it
   */
  public String hashOf(File file, long offset, long length) {
    try (InputStream is = new FileInputStream(file)) {
      is.skip(offset);
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.reset();
      byte[] data = new byte[16384];
      long cumulativeLength = 0;
      int len = 0;
      while (len != -1 && cumulativeLength < length) {
        digest.update(data, 0, len);
        int readLen = (int) Math.min(length - cumulativeLength, data.length);
        len = is.read(data, 0, readLen);
      }
      String sha1 = new BigInteger(1, digest.digest()).toString(32);
      return sha1.substring(0, 2) + "/" + sha1.substring(2);
    } catch (NoSuchAlgorithmException e) {
      throw new Error(e);
    } catch (IOException e) {
      e.printStackTrace();
      return file.getName();
    }
  }

  /**
   * Opens a file stream to write to the study directory.  Replaces the existing file if it exists.
   *
   * @param id
   * @param dest
   * @param gzip
   * @return
   */
  public OutputStream writeStudyDir(SopId id, String dest, boolean gzip) throws IOException {
    return openForWrite(callbacks.getStudiesDir(id), dest, gzip, true);
  }

  public InputStream read(String dir, String name) throws IOException {
    FileInputStream fis = new FileInputStream(new File(dir, name));
    if (name.endsWith(".gz")) return new GZIPInputStream(fis);
    return fis;
  }

  /**
   * List the file names for the given directory with an increasing age (newest first)
   *
   * @param dir to find the list for
   * @return list of items
   */
  public List<String> listContentsIncreasingAge(String dir) {
    File dirFile = new File(dir);
    String[] items = dirFile.list();
    if (items == null || items.length == 0) return Collections.emptyList();
    var files = new ArrayList<>(Arrays.asList(items));
    var ages = new HashMap<String, Long>();
    files.forEach(name -> ages.put(name, new File(dir, name).lastModified()));

    files.sort((a, b) -> {
      return (int) Math.signum(ages.get(a) - ages.get(b));
    });
    return files;
  }

  public static void rmdir(File tempDir) {
    if (!tempDir.isDirectory()) {
      tempDir.delete();
    } else {
      Arrays.stream(tempDir.listFiles()).forEach(FileHandler::rmdir);
      tempDir.delete();
    }
  }

  public static void safeClose(OutputStream os) {
    try {
      if (os != null) os.close();
    } catch (IOException e) {
      log.warn("Caught exception on close", e);
    }
  }

  public static void renameTo(File src, File dest, boolean overwrite) {
    if (overwrite && dest.exists()) {
      try {
        Files.delete(dest.toPath());
      } catch (IOException e) {
        log.error("Can't delete", e);
      }
    }
    try {
      Files.move(src.toPath(), dest.toPath());
      return;
    } catch (Exception e) {
      if (!overwrite) {
        src.delete();
        return;
      }
      log.error("Can't move", e);
    }
    log.warn("Unable to replace {} with {}", dest, src);
  }

}
