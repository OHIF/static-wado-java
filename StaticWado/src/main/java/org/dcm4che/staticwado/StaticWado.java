package org.dcm4che.staticwado;
import org.apache.commons.cli.*;
import org.dcm4che3.data.UID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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


    public static void addStudyManagerArgs(Options opts) {
        opts.addOption(new Option("d","dicomweb", true, "DICOMweb directory"));
        opts.addOption(new Option( StudyManager.DEDUPLICATE, false, "Generate single instance deduplicate data"));
        opts.addOption(Option.builder( StudyManager.DEDUPLICATE_GROUP)
            .desc("Group single instance deduplicate data into group files")
            .build());
        opts.addOption(Option.builder( StudyManager.STUDY_METADATA)
            .desc("Generate study metadata")
            .build());
        opts.addOption(Option.builder( StudyManager.INSTANCE_ONLY)
            .desc("Only generate instance level metadata/bulkdata")
            .build());
        opts.addOption(Option.builder("contentType")
            .hasArg()
            .desc("Sets the transfer syntax appropriately for one of: jll,jls,jpeg,j2k,orig.  Will not recompress.  Default is jls.")
            .build());
        opts.addOption(Option.builder("recompress")
            .hasArg()
            .desc("Recompress already compressed files of the specified types (defaults to j2k,lei but can include jls, jll, jpeg)")
            .build());
        opts.addOption(Option.builder("tsuid")
            .hasArg()
            .desc("Sets the transfer syntax directly")
            .build());
        opts.addOption(Option.builder("h").desc("Show help").build());
    }

    private static CommandLine parseCommandLine(String[] args)
            throws ParseException {
        Options opts = new Options();
        addStudyManagerArgs(opts);
        return parseCommandLine(opts, args);
    }

    public static CommandLine parseCommandLine(Options opts, String[] args) throws ParseException {
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

    public static StudyManager createStudyManager(CommandLine cl) {
        boolean deduplicate = cl.hasOption(StudyManager.DEDUPLICATE);
        boolean deduplicateGroup = cl.hasOption(StudyManager.DEDUPLICATE_GROUP);
        boolean studyMetadata = cl.hasOption(StudyManager.STUDY_METADATA);
        boolean instanceOnly = cl.hasOption(StudyManager.INSTANCE_ONLY);
        boolean completeStudy = !(instanceOnly || studyMetadata || deduplicateGroup || deduplicate);

        StudyManager manager = new StudyManager();
        manager.setCompleteStudy(completeStudy);
        manager.setDeduplicate(deduplicate);
        manager.setStudyMetadata(studyMetadata);
        manager.setInstanceMetadata(instanceOnly);
        manager.setStudyMetadata(studyMetadata);
        manager.setDeduplicateGroup(deduplicateGroup);
        manager.setDicomWebDir(cl.getOptionValue('d'));

        return manager;
    }

    public static void main(String[] args) throws Exception {
        CommandLine cl = parseCommandLine(args);
        StudyManager manager = createStudyManager(cl);

        String[] otherArgs = cl.getArgs();

        boolean server = false;

        manager.scanDicom(otherArgs);
    }
}
