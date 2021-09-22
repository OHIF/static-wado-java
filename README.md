# static-wado
A command line tool to generate static WADO metadata and bulkdata files.  
The input for the tool is the locations of DICOM files.
The output is a DICOMweb static directory structure.
There are three types of operations which can be performed, and all three can be done at once.
The first, converting single DICOM instances into single-instance bulkdata and metadata files just writes
the instance level instance and metadata JSON files, as well as the deplicated data set referencing the written files.
The second, generates the series and study level metadata responses from the deuplicated file dataset.
The final one, uploads the JSON and bulkdata files to a cloud provider.

When run all together, these changes create a static structure that can be served as a DICOMweb service.

## Simple Output Structure
The base or simple output structure starts with a top level directory studies/STUDY_UID/
* File study.gz containing the study level JSON query response
* File deduplicated-index.gz containing references to the current state of the study
* metadata.gz containing the JSON metadata for the entire study information
* File series.gz containing a complete series query for the study
* instance.gz containing an instance query for the study
* Directories series/SERIES_UID/
  * File metadata.gz containing the metadata response for the series
  * Directories instances/OBJECT_UID/
      * File ../index.gz containing an instance level query for the series
      * File metadata.gz containing the JSON metadata information for the series
      * Directory frames containing files 1..n raw directory frames (optional)
      * File pixeldata, containing a direct retrieve of the frame data (video only for now)

## Hash Bulkdata Indexed Structure
By default, the bulkdata associated with an instance will be stored in a directory structure indexed by hash values.  
The basic structure is  HASH1/HASH2/HASH3.ext  where HASH1, 2 are the first
part of the hash, HASH3 is the remainder of the hash.  The hash value is
generated on the specified key/value pairs for JSON like structures, and on 
the decompressed pixel data for images.  

The file extensions are:
* .raw for octet-stream/unencoded
* .dcm for raw DICOM files

# Overall Design
The basic design for Static WADO is an application at the top level to parse command line options, and then a library 
with a few components in it to handle the scanning and output generation.

## Client
The first client is a simple app setting up the initial setup directories and options, and then calling the study manager to search for and convert files.  Other clients may appear over time for things like DIMSE dicom receive.

## Managers
In this implementation, only a study manager will exist.  It has the funcitonality to scan for studies, open up a study engine for each study as it comes in, and add instances to the study engine, calling the finalize/complete operation on the study engine once the next study is started.

## Engines
The study engine will have the pieces necessary to manage
the bulkdata extraction, image conversion and de-duplication.

TODO: Consider whether having an engine for each of bulkdata, image conversion and de-duplication is a good idea.
Those are fairly independent components, so having one for each is
probably a good idea.

## Data Services
A data service for query results (JSON), metadata results and bulkdata will 
all be required. These might have two layers to them, one for File I/O
and a second set for cloud I/O to separate the format concerns from the
location concerns.

# Running Static Wado

## Build
The build system is gradle, with JDK 11 or later required.  Libraries are
provided for Mac OSX, Windows and Linux,  all 64 bits.

gradlew installDist

will build and install the program in
StaticWado/build/install/StaticWado
with the command line tools in the bin directory.  The remaining examples
assume that the bin directory is in the path.  

## Converting a set of files and serving them locally
Add build/install/StaticWado/bin to your path.
For DICOM files located in /dicom/study1 and /dicom/study2, with the 
output directory /dicomweb, and covnerting images to JPEG-LS, run:

StaticWado -d /dicomweb -contentType jls /dicom/study1 /dicom/study2

## Serving up a local filesystem as DICOMweb
Assuming you have the JavaScript npm manager installed, change your directory
to the DICOMweb output directory, and run:

npx http-server -p 5000 --cors -g

## Accessing your local filesystem in OHIF
Use the pre-configured local_static.js file, and then run:

APP_CONFIG=config/local_static.js yarn start

Also, you can edit local_static.js to point to the full host name of the
system hosting the files instead of http://localhost:5000

## Setting up AWS S3/Cloudfront for serving data
TODO

## Uploading to an S3 bucket
Ensure that you have local credentials setup for AWS in the standard location,
with access to your S3 bucket.  Run

StaticWado -s3 -bucket s3-bucket-name -region us-east-2 -contentType jls -d /dicomweb /dicom/study1

You can also use the -study <UID> to specify an already converted study.

## Uploading to sub-directories for content type testing
There is an option -type that will generate sub-directories of /dicomweb
and upload them to the given sub-type name, where the type generated is the
given type.  For example:

StaticWado -type jls /dicom/study1

will generate /dicomweb/jls/studies/... all encoded in JLS format.  That allows
use of a sub-directory to determine the response type.  Video data will still
be extracted as pixeldata in a streaming format.


# TODO list

* Split operations up into separate phases appropriate for lambda conversion
* Add AWS setup guide
* Support Google Cloud and Azure
* Write a simple server for local use
* Write a DICOM endpoint
* Add storage of study data indices to FHIR
* Support creation of QC DICOM operations, and apply them locally
* Support patient/study data updates
* Support HL7 updates

