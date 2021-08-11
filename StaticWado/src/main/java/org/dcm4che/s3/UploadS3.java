package org.dcm4che.s3;
import org.apache.commons.cli.CommandLine;
import org.dcm4che.staticwado.FileHandler;
import org.dcm4che.staticwado.JsonWadoAccess;
import org.dcm4che3.data.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/** Handles uploading an existing set of study directories into the S3 directory.  Also updates the studies
 * instance and replaces it.
 */
public class UploadS3 {
    private static final Logger log = LoggerFactory.getLogger(UploadS3.class);

    public static final Set<String> JSON_NAMES = new HashSet<>(Arrays.asList(
            "studies", "series", "metadata", "instances", "deduplicated"
    ));

    private final String bucketName;
    private final String regionName;
    private boolean dryRun;
    /** The location of the OHIF Client to import into S3 */
    private final String clientImport;

    Map<String,Attributes> studies = new HashMap<>();
    private FileHandler handler;
    private File studiesFile;

    public UploadS3(CommandLine cl) {
        this.bucketName = cl.getOptionValue("bucket", "static-wado");
        this.regionName = cl.getOptionValue( "region", Regions.US_EAST_2.getName());
        this.dryRun = cl.hasOption("dry");
        this.clientImport = cl.getOptionValue("client");
        log.warn("Client import {}", this.clientImport);
    }

    /** Goes through all the source directories provided and uploads them to the destination.
     * Assumes that "studies" is the root studies directory, finding that in the parent or child.
     * If it isn't found, then throws an error.
     */
    public void upload(String exportDir, String... studyUids) {
        String[] sources;
        if( studyUids==null || studyUids.length==0 ) {
            sources = new String[]{exportDir};
        } else {
            sources = Arrays.stream(studyUids).map(uid -> (exportDir + "/studies/"+uid)).toArray(String[]::new);
        }
        try {
            for(String src : sources) {
                File fileSrc = new File(src).getAbsoluteFile();
                String path = fileSrc.getParentFile().getPath();
                log.debug("Looking at path {}", path);
                path = path.replace('\\', '/');
                if (path.contains("/studies")) {
                    path = "dicomweb/" + path.substring(path.indexOf("/studies") + 1);
                    if (!path.endsWith("/")) path = path + "/";
                    File childFile = fileSrc;
                    while (!childFile.getName().equals("studies")) {
                        childFile = childFile.getParentFile();
                    }
                    log.debug("Found child file {}", childFile);
                    studiesFile = new File(childFile.getParentFile(), "studies.gz");
                    log.warn("Studies file {}", studiesFile);
                    JsonWadoAccess.readStudiesDirectory(studies, studiesFile);
                } else {
                    path = null;
                }
                upload(path, new File(src));
            }
            if( studiesFile!=null ) {
                log.warn("Creating new studies file {} and uploading it", studiesFile);
                boolean wasDry = dryRun;
                dryRun = false;
                new JsonWadoAccess(new FileHandler(studiesFile.getParentFile())).writeJson("studies",studies.values().toArray(Attributes[]::new));
                uploadS3("dicomweb/", studiesFile, true);
                dryRun = wasDry;
            }
        } catch(IOException e) {
            log.warn("Unable to upload because {}", e);
            e.printStackTrace();
        }
    }

    public void upload(String path, File file) throws IOException {
        if( file.getName().equalsIgnoreCase("temp") ) return;
         if( file.isDirectory() ) {
             if( path!=null ) {
                 path = path + file.getName() + "/";
             } else if( file.getName().equals("studies") ) {
                 path = "dicomweb/studies/";
                 studiesFile = new File(file.getParentFile(),"studies.gz");
                 JsonWadoAccess.readStudiesDirectory(studies,studiesFile);
             }
             for(File subFile : file.listFiles() ) {
                 upload(path,subFile);
             }
         } else if( path!=null ) {
             if( file.getName().equalsIgnoreCase("studies.gz") ) {
                 // This call will add the study to the studies list, replacing the old one
                 // This does NOT set the studies directory again.
                 JsonWadoAccess.readStudiesDirectory(studies,file);
             }
             uploadS3(path,file,false);
         }
    }

    /** Uploads a client directory if set */
    public void uploadClient() throws IOException {
        if( clientImport==null ) return;
        log.warn("Uploading client from {}", clientImport);
        File clientDir = new File(clientImport);
        File indexFile = new File(clientDir,"index.html.gz");
        // TODO - execute gzip -r <clientDir> if the index.html exists but .gz doesn't
        // Make a copy of index.html.gz
        try(FileInputStream fis = new FileInputStream(indexFile);
            FileOutputStream fos = new FileOutputStream(new File(clientDir,"viewer.gz"))) {
            byte[] data = new byte[16384];
            int len = fis.read(data);
            while(len!=-1) {
                fos.write(data,0,len);
                len = fis.read(data);
            }
        }
        uploadAll(null,clientDir);

    }

    /** Just does an upload of everything in the file location, to the sub-string of the given path */
    public void uploadAll(String path, File file) throws IOException {
        if( file.isDirectory() ) {
            if( path==null ) {
                path = "";
            } else {
                path = path + file.getName() + "/";
            }
            for(File subFile : file.listFiles() ) {
                uploadAll(path,subFile);
            }
        } else {
            uploadS3(path,file,false);
        }
    }

    /** Uploads from the given src file, to the destination path, of the given content type.
     * The name of the uploaded file will be the name of the src file, except that it will have
     * an .gz or .br removed and the appropriate content encoding will be added (gzip or brotli).
     *
     * @param src
     * @param destPath
     * @throws IOException
     */
    public void uploadS3(String destPath, File src, boolean replace) throws IOException {
        String s3Name = s3Name(src, destPath);
        if( dryRun ) {
            log.warn("Would upload {} / {} to {}",destPath,src, s3Name);
            return;
        }
        Regions clientRegion = Regions.fromName(regionName);
        try {
            //This code expects that you have AWS credentials set up per:
            // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(clientRegion)
                    .build();

            if( !replace ) {
                try {
                    ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, s3Name);
                    Date s3Date = metadata.getLastModified();
                    Date srcDate = new Date(src.lastModified());
                    if ( srcDate.before(s3Date) ) {
                        log.warn("Skipping {} because it already exists with date {} > {}", s3Name, s3Date, srcDate);
                        return;
                    }
                    log.warn("Object exists but date is newer {} <= {}  compare {}", s3Date, srcDate, srcDate.before(s3Date));
                } catch(Exception e) {
                    // Means it doesn't exist, no-op
                }
            }
            log.warn("Uploading {} / {}",destPath,src);
            // Upload a file as a new object with ContentType and title specified.
            PutObjectRequest request = new PutObjectRequest(bucketName, s3Name, src);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(getContentType(src));
            if( isGzip(src) ) {
                metadata.setContentEncoding("gzip");
            } else if( isBrotli(src) ) {
                metadata.setContentEncoding("brotli");
            }
            request.setMetadata(metadata);
            s3Client.putObject(request);
        } catch (SdkClientException e) {
            log.error("Couldn't upload", e);
            e.printStackTrace();
            throw new Error(e);
        }
    }

    public static boolean isGzip(File f) {
        return f.getName().endsWith(".gz");
    }


    public static boolean isBrotli(File f) {
        return f.getName().endsWith(".br");
    }

    public static String getContentType(File src) {
        String name = s3Name(src);
        if( name.endsWith(".js") ) return "text/javascript";
        if( name.endsWith(".css") ) return "text/css";
        if( name.endsWith(".html") ) return "text/html";
        if( name.endsWith(".txt") ) return "text/plain";
        if( name.endsWith(".xml") ) return "text/xml";
        if( name.endsWith(".png") ) return "image/png";
        if( name.endsWith(".svg") ) return "image/svg";
        if( name.endsWith("viewer") ) return "text/html";
        if( JSON_NAMES.contains(name) ) return "application/json";
        return "application/octet-stream";
    }

    public static String s3Name(File src) {
        String ret = src.getName();
        if( isGzip(src) || isBrotli(src) ) {
            ret = ret.substring(0,ret.length()-3);
        }
        return ret;
    }

    /** Returns the s3 name of something */
    public static String s3Name(File src, String destPath) {
        return destPath + s3Name(src);
    }
}
