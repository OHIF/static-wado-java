package org.dcm4che.staticwado;
import org.apache.commons.cli.*;
import org.dcm4che.s3.UploadS3;

public class StaticWado {

    private static CommandLine parseCommandLine(String[] args)
            throws ParseException {
        Options opts = new Options();
        opts.addOption(Option.builder("d")
                        .desc("Output directory")
                .hasArg()
                .argName("directory")
                .build());
        opts.addOption(Option.builder("s3")
                .desc("Upload to S3")
                .build());
        opts.addOption(Option.builder("dry")
                .desc("Dry run of upload (create updates studies.gz and list files to upload)")
                .build());
        opts.addOption(Option.builder( "bucket")
                .desc("Bucket name to upload to")
                .build());
        opts.addOption(Option.builder( "region")
                .desc("Bucket name to upload to")
                .build());
        CommandLineParser parser = new DefaultParser();
        return parser.parse(opts, args);
    }

    public static void main(String[] args) throws Exception {
        CommandLine cl = parseCommandLine(args);
        StudyManager manager = new StudyManager();
        String[] otherArgs = cl.getArgs();
        if( otherArgs==null || otherArgs.length==0 ) otherArgs = new String[]{"c:/dicom"};
        String exportDir = cl.getOptionValue('d', "c:/dicomweb");
        if( cl.hasOption("s3") ) {
            UploadS3 uploadS3 = new UploadS3(cl);
            uploadS3.upload(otherArgs);
        } else {
            manager.setExportDir(exportDir);
            manager.importStudies(otherArgs);
        }
    }
}
