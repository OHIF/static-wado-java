package org.dcm4che.staticwado;
import org.apache.commons.cli.*;
import org.dcm4che.s3.UploadS3;
import org.dcm4che3.data.UID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class StaticWado {
    private static final Logger log = LoggerFactory.getLogger(StaticWado.class);

    public static final Map<String,String> TS_BY_TYPE = new HashMap<>();
    static {
        TS_BY_TYPE.put("jpll", UID.JPEGLosslessSV1);
        TS_BY_TYPE.put("jll", UID.JPEGLosslessSV1);
        TS_BY_TYPE.put("jls", UID.JPEGLSLossless);
        TS_BY_TYPE.put("x-jls", UID.JPEGLSLossless);
        TS_BY_TYPE.put("jp2", UID.JPEG2000Lossless);
        TS_BY_TYPE.put("j2k", UID.JPEG2000Lossless);
        TS_BY_TYPE.put("jpeg", UID.JPEGBaseline8Bit);
        TS_BY_TYPE.put("lei", UID.ImplicitVRLittleEndian);
        TS_BY_TYPE.put("lee", UID.ExplicitVRLittleEndian);
        TS_BY_TYPE.put("raw", null);
    }

    private static CommandLine parseCommandLine(String[] args)
            throws ParseException {
        Options opts = new Options();
        opts.addOption(Option.builder("d")
                        .desc("Output directory")
                .hasArg()
                .argName("directory")
                .build());
        opts.addOption(Option.builder("study")
                .desc("Study UID to export")
                .hasArgs()
                .argName("Study Instance UID")
                .build());
        opts.addOption(Option.builder("s3")
                .desc("Upload to S3")
                .build());
        opts.addOption(Option.builder("dry")
                .desc("Dry run of upload (list files to upload, and then actually uploads studies.gz)")
                .build());
        opts.addOption(Option.builder( "bucket")
                .hasArg()
                .desc("Bucket name to upload to")
                .build());
        opts.addOption(Option.builder( "dicomdir")
                .hasArg()
                .desc("DICOMDir output name (default dicomweb)")
                .build());
        opts.addOption(Option.builder( "region")
                .hasArg()
                .desc("Bucket name to upload to")
                .build());
        opts.addOption(Option.builder( "deduplicated")
                .desc("Generate deduplicated data set")
                .build());
        opts.addOption(Option.builder( "instances")
                .desc("Generate instances query response")
                .build());
        opts.addOption(Option.builder("type")
                .hasArg()
                .desc("Sets the type name to use for dicomdir, contentType, output directory (lei,jls,jp2,jll)")
                .build());
        opts.addOption(Option.builder("client")
                .hasArg()
                .desc("Client to upload/replace")
                .build());
        opts.addOption(Option.builder("contentType")
                .hasArg()
                .desc("Sets the transfer syntax appropriately for one of: jll,jls,jpeg,j2k,orig.  Will not recompress.  Default is jls.")
                .build());
        opts.addOption(Option.builder("recompress")
                .hasArg()
                .desc("Recompress already compressed files of the specified types (defaults to j2k, but can include jls, jll, jpeg)")
                .build());
        opts.addOption(Option.builder("tsuid")
                .hasArg()
                .desc("Sets the transfer syntax directly")
                .build());
        opts.addOption(Option.builder("h").desc("Show help").build());

        CommandLineParser parser = new DefaultParser();
        CommandLine cl = parser.parse(opts, args);
        if (cl.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(
                    "StaticWado <options> <input-directories>",
                    "Generates a DICOMweb static directory structure for the specified DICOM inputs", opts,
                    "StaticWado -s3 -client /OHIF/platform/viewer/dist /dicomSrcDir");
            System.exit(0);
        }
        return cl;
    }

    public static void main(String[] args) throws Exception {
        CommandLine cl = parseCommandLine(args);
        StudyManager manager = new StudyManager();
        String[] otherArgs = cl.getArgs();
        String type = cl.getOptionValue("type");
        String[] studies = cl.getOptionValues("study");
        String exportDir = cl.getOptionValue('d', "/dicomweb"+(type!=null ? ("/"+type) : ""));
        log.debug("Export dir {}", exportDir);
        manager.setIncludeDeduplicated(cl.hasOption("deduplicated"));
        manager.setIncludeInstances(cl.hasOption("instances"));
        if( otherArgs!=null && otherArgs.length>0 ) {
            manager.setExportDir(exportDir);
            String tsuid = cl.getOptionValue("tsuid");
            String contentType = cl.getOptionValue("contentType",type==null ? "jls" : type);
            if( contentType!=null && tsuid==null ) {
                tsuid = TS_BY_TYPE.get(contentType);
            }
            String recompress = cl.getOptionValue("recompress",type!=null ? "lei,j2k,jls,jll,jxl" : "lei,j2k");
            manager.setTransferSyntaxUid(tsuid);
            manager.setRecompress(recompress);
            studies = manager.importStudies(otherArgs);
        }
        if( cl.hasOption("s3") ) {
            UploadS3 uploadS3 = new UploadS3(cl,type);
            uploadS3.uploadClient();
            uploadS3.upload(exportDir,studies);
        }
    }
}
