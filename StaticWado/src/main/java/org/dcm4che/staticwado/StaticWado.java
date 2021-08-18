package org.dcm4che.staticwado;
import org.apache.commons.cli.*;
import org.dcm4che.s3.UploadS3;
import org.dcm4che3.data.UID;

import java.util.HashMap;
import java.util.Map;

public class StaticWado {
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
                .desc("Bucket name to upload to")
                .build());
        opts.addOption(Option.builder( "region")
                .desc("Bucket name to upload to")
                .build());
        opts.addOption(Option.builder("client")
                .hasArg()
                .desc("Client to upload/replace")
                .build());
        opts.addOption(Option.builder("contentType")
                .hasArg()
                .desc("Sets the transfer syntax appropriately for one of: jll,jls,jpeg,j2k")
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
        String[] studies = cl.getOptionValues("study");
        String exportDir = cl.getOptionValue('d', "/dicomweb");
        if( otherArgs!=null && otherArgs.length>0 ) {
            manager.setExportDir(exportDir);
            String tsuid = cl.getOptionValue("tsuid");
            String contentType = cl.getOptionValue("contentType","lei");
            if( contentType!=null && tsuid==null ) {
                tsuid = TS_BY_TYPE.get(contentType);
            }
            manager.setTransferSyntaxUid(tsuid);
            studies = manager.importStudies(otherArgs);
        }
        if( cl.hasOption("s3") ) {
            UploadS3 uploadS3 = new UploadS3(cl);
            uploadS3.uploadClient();
            uploadS3.upload(exportDir,studies);
        }
    }
}
