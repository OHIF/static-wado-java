# static-wado
A command line tool to generate static WADO metadata and bulkdata files.  
The input for the tool is a pair of input and output directories.  The input is searched recursively for any DICOM files, ignoring other files.  Files are grouped by Study Instance UID, and a directory having the study instance UID as the name will be created for every unique/valid study instance UID found.  The files will contain data in the output directory in the format required for the study, series and instances search, as well as the bulkdata and metadata files.

## Simple Ouput Structure
The base or simple output structure starts with a top level directroy studies/STUDY_UID/
* File STUDY_UID.json containing the study level query response for this study
* metadata.json containing the JSON metadata information
* Directories series/SERIES_UID/
  * File ../index.json containing a complete series response for the study
  * File SERIES_UID.json containing the series level query response for this series
  * File metadata.json containg the metadata response for the series
  * Directories instances/OBJECT_UID/
      * File ../index.json containing a complete instance level response for the series
      * File metadata.json containing the JSON metadata information for the series
      * File OBJECT_UID.json containing the instance level query response for the object
      * Directory frames containing files 1..n raw directory frames (optional)

## Hash Bulkdata Indexed Structure
By default, the bulkdata associated with an instance will be stored in a directory structure indexed by hash values.  The basic structure is  HASH1/HASH2/HASH3.ext  where HASH1, 2 are the first part of the hash, HASH3 is the remainder of the hash.  The hash value is generated on the specified key/value pairs for JSON like structures, and on the decompressed pixel data for images.  

The file extensions are:
* .raw for octet-stream/unencoded
* .jls for jpeg LS encoded images
* .htk for high throughput J2K
* .json for JSON files
* .dcm for raw DICOM files
* .EXT.gz for already gzipped files

# Overall Design
The basic design for Static WADO is an application at the top level to parse command line options, and then a library with a few components in it to handle the scanning and output generation.

## Client
The first client is a simple app setting up the initial setup directories and options, and then calling the study manager to search for and convert files.  Other clients may appear over time for things like DIMSE dicom receive.

## Managers
In this implementation, only a study manager will exist.  It has the funcitonality to scan for studies, open up a study engine for each study as it comes in, and add instances to the study engine, calling the finalize/complete operation on the study engine once the next study is started.

## Engines
The study engine will have the pieces necessary to manage the bulkdata extraction, image conversion and de-duplication.

TODO: Consider whether having an engine for each of bulkdata, image conversion and de-duplication is a good idea.  Those are fairly independent components, so having one for each is probably a good idea.

## Data Services
A data service for query results (JSON), metadata results and bulkdata will all be required.  These might have two layers to them, one for File I/O and a second set for cloud I/O to separate the format concerns from the location concerns.


