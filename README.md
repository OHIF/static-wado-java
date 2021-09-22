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


# Serverless Function Design
This is still preliminary, but the basic idea is to have a few phases for the
serverless design.  

1. Convert single instances to instance level metadata and bulkdata
2. Gather up new instance level metadata into grouped hash keyed data
3. Gather the grouped data into study level references and write the DICOMweb query files

## Gathering single instances
This process is triggered by a DICOM instance being added/received. 
It is possible to trigger this via a lambda function, or for this process
to be done outside the cloud as the starting point.

1. Extract large bulk data, storing to the hash directory structure
2. Replace it with a reference by hash, with offset/length information
   1. Alternate: replace with an offset reference to the DICOM file location
   2. In the DICOM file reference, still include the hashed value
3. Store the images to the frames and/or pixeldata.  
   1. Include the image hash in the reference URL (TODO - how)
   2. Optionally compress the image
4. Write the remaining metadata file at the instance level

Note how this is thread safe provided a single DICOM instance is only added once,
as it only writes things relative to the instance.  Dealing with receiving
the same instance a second time can be managed by checking for existance of
the metadata file already, and writing a new copy.  That part itself isn't
thread safe, but there isn't a workflow where it is likely to receive different
versions of the same data at the same time.

## Creation of hash/grouped files
Triggered by creation of one of more instance level metadata files.  Should
be delayed by some amount of time, and grouped by study UID to avoid executing
too many instances of this.  This can also be done within the DICOM receive
process, as the data is already available at that point.

The hash/grouped data files are located in the hash directory structure and
contain
* Patient data, both as-received and as-updated (a single file won't have both)
* Study data
* Groups of SOP references, in a deduplicated format
* Other bulkdata, just hashed as is

This deduplication process splits off different types of data, and allows
that to be written based a hash of the DICOM tag values.  The steps are:
2. Extract Patient "query" results and Study data into their own instance within the JSON.
3. Store the Study/Patient data into a file with a name based on the hash
4. Group instances into referenced values either by series or at some study level grouping.
   1. The series level grouping should be used when there is a lot of JSON data
   2. There will typically be a lot of JSON data when there are lots of images in a series or for enhanced multiframe.
   3. Group the remaining objects into a single group, store at the study level
5. Write an index file referencing any index files used to create the new groups
6. Schedule an update of the study metadata

## deduplicated index generation
The deduplicated index is a link to the group files and patient/study files
which comprise the current state of the study.

The thread safety of this design relies on only adding to the deduplicated
and hashed files, and using references by hash to older versions of the files,
and then a process that performs the gathering and checking, causing the process
to be re-run whenever a change is detected, so that a change will eventually
be consistent.

The index directory name is index, and it contains JSON files in a DICOMweb
like format.  Each file is named  <hash>.gz, and contains:
1. A reference to the current study and patient hash files
2. References to the series/grouped instance hash files
3. References to the prior versions of the index
4. References to deleted/moved data, plus the deletion reason

The process to check/update the grouped data is thus:
1. Read the top level deduplicated file, if present
2. Check that the top level deduplicated file's referenced hash value includes every hash value in the index directory, other than it's own hash.
3. If any index file is present but not referenced, then read all the other files in the index
   1. If any one of the other files references all the files, then replace the top level deduplicated file
   2. If there is new data in any of the sub-files, then create new hashed group files for them and add references
   3. Add references to all the read indices to the new index file
   4. Write a new index file with the name <hash>.gz for the hash code of the updated index
4. For every series and at the study level, check the hash value in the query/metadata file
   1. The hash value expected is the hash value of the group file that references that instance in the index file
   2. This avoids re-writing query/metadata files when not needed
   3. It is necessary to also check the current hash value of the study and patient data
   4. Write a new file for any instance that is different
5. If any update was performed, then schedule a unification check at time+1

### Thread Safety for creation of index files
Suppose multiple processes write index files, referencing a mixture of the
same and updated data.  These files might contain:
1. Exactly the same content, in which case the hash will be identical.  
   1. At least one will succeed
   2. Remaining ones can succeed or fail.
2. References to shared data, plus new content.
   1. The fact that references to shared data exist allows the shared data to be assumed by an updated process
   2. A unification run of the update will occur in step #5 above
      1. The unification run will merge two of more index files
      2. The unification run MAY write new group files based on the new data
      3. This run will then write a new index, referencing both original files

### Thread Safety for creation of metadata and query files
The unification run will compare the hash value of the group/study/patient
files with that of the query/metadata file, and will update them when the
index file has changed.  If any of these are out of date, another unification
run is scheduled to ensure that the files are eventually in sync.

### Thread Safety of fetch
It is possible for retrieval of study data to be out of sync, by up to the
cache time for these files, plus the unification run time.  Given that, the
query and metadata files should have a relatively short cache time, while the
bulkdata and deduplicated files can have a very long cache time.

# QC Operations on Static Files
It is possible for the static wado files to be updated with various quality
control operations, such as patient updates, study verification, and 
split/segment/delete operations.  These are performed as creation of new
group files, plus updates to the index files.

## Availability of linked data files
If a soft link between files is available, then it should be used to 
"copy" data files from one location to another.  Such a soft link could
be implemented by a database table mapping hash key to location.  This table
would have to be consulted for every instance lookup except in the case of
looking items up already by hash key.  Doing this would prevent duplicate
data definitions, and would make operations such as split/segment and
study UID update very fast.

One option for the linked data files is to:
* Have the "cloudfront" version of the file reference the final file name location
* Use a memory cache database for the mapping
* When a file location mapping is absent, read the group file location to get the final file location
* Add the mappings to the memory cache when they are read in initially

## Patient Updates and Study Verification
Excluding updating the study instance UID, patient and study updates are
performed by writing new patient and study group files to the updated hash
names, and then adding a new index file containing references to the new 
patient or study group file.  If two index files have incompatible changes,
then a conflict has occurred, and some sort of admin notification is required
to resolve the conflict.  Otherwise, the latest version of the patient data
can be created.

## Study Instance UID updates and split/segment
If the study instance UID has been updated or instances moved to a new study,
then there are a couple of options. Currently, the only one being considered
is to create an entirely new study directory, copying all the data from the
original study, and creating a new index file referencing the updated study
directory.  

Future alternatives would allow a reference to the original study directory
location, however, that may cause other types of problems.

New incoming study updates in the unification data may be required to write
the new data to the new study location.

## Delete and Split/Segment operations
For a delete operation, as well as the original locations for any
split or segmented data items, the deleted item should be marked as deleted
in the index file, along with a reason for the change.

On subsequent updates to indices, the deleted files will be noted as being
referenced, but other updates to them may be ignored.

A delete for resend may be implemented by removing references AND removing
instances in the cache, such that they are re-written on subsequent calls.
Alternatively, the original values can be referenced, and the files moved
out of the way, but still referenced.

An instance already deleted, but received again should be rejected.

## Split/Segment operations
A split/segment can be done by writing new group files, referencing the
old group files, and either copying data files or referencing the hash
value of the old files.  This may require a database symlink.