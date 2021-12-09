package org.dcm4che.staticwado;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/** A simple HTTP server for static wado. */
public class StaticWadoHttp {
  public StaticWadoHttp(CommandLine cl) {
  }

  public static void addOptions(Options opts) {
    opts.addOption(new Option("p","httpPort",true,"Define the http port"));
  }

  public void start() {

  }
}
