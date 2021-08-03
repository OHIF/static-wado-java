package org.dcm4che.staticwado;
import org.apache.commons.cli.*;

public class StaticWado {

    private static CommandLine parseCommandLine(String[] args)
            throws ParseException {
        Options opts = new Options();
        opts.addOption(Option.builder("d")
                        .desc("Output directory")
                .hasArg()
                .argName("directory")
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
        manager.setExportDir(exportDir);
        manager.importStudies(otherArgs);
    }
}
