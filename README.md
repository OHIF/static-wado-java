# static-wado
The canonical location of this is now [OHIF static-wado](https://github.com/OHIF/static-wado.git)

A command line tool to generate static WADO metadata and bulkdata files.  
The input for the tool is the locations of DICOM files.
The output is a DICOMweb static directory structure.
There are three types of operations which can be performed, and all three can be
done at once.  The first, converting single DICOM instances into bulkdata/pixeldata
and deduplicated metadata.  The second groups the deduplicated metadata into sets
that identify the information for the study.  The third actually writes out the
study level metadata/query in the standard DICOMweb format.

When run all together, these changes create a static structure that can be 
served as a DICOMweb service.  The fact that these are each independent stages 
allows study metadata to safely be created in a fully distributed system.  There
are timing requirements on writing the updates, and there are some retries that
ensure eventual consistency, but those updates can be done on any or multiple of the
distributed systems.



## Simple Output Structure
See the [Archive Format](docs/archive-format.md) for details on
how the output is organized.

## Hash Bulkdata Indexed Structure
By default, the bulkdata associated with an instance will be stored in a directory structure indexed by hash values.  
The basic structure is bulkdata/{hash0-3}/{hash3-5}/{hash5-} where the hash is
a hash value of the object.  This is the same structure used for partial hash contents.

# Overall Design
The basic design for Static WADO is an application at the top level to parse 
command line options, and then a library StudyManager that is basically a set of
callbacks which are basically notifications on various events/data sets such as
add single deduplicated object, handle study completion events etc.

## Client
The first client is a simple app setting up the initial setup directories
and options, and then calling the study manager to search for and convert files.  
Other clients may appear over time for things like DIMSE dicom receive, or writing 
directly to cloud storage such as AWS S3.

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
output directory ~/dicomweb the following command can be run:

StaticWado /dicom/study1 /dicom/study2

## Serving up a local filesystem as DICOMweb
Assuming you have the JavaScript npm manager installed, change your directory
to the DICOMweb output directory, and run:

cd ~/dicomweb
npx http-server -p 5000 --cors -g

There is also a custom web server supporting STOW and serving the right content
types being developed.

## Accessing your local filesystem in OHIF
Use the pre-configured local_static.js file, and then run:

APP_CONFIG=config/local_static.js yarn start

Also, you can edit local_static.js to point to the full host name of the
system hosting the files instead of http://localhost:5000

## Setting up AWS S3/Cloudfront for serving data
TODO - being worked on

# TODO list

* Add AWS setup guide
* Support Google Cloud and Azure
* Write a simple server for local use
* Write a DICOM endpoint
* Add storage of study data indices to FHIR or ...
* Support creation of QC DICOM operations, and apply them locally
* Support patient/study data updates
* Support HL7 updates

# Serverless Function Design
The idea for the serverless design is to have three lambda functions which
correspond to the three phases in the StaticWado command that can currently be run.

1. Convert single instances to deduplicated metadata and bulkdata/pixeldata
2. Group deduplicated single instances into sets of deduplicated data
3. Write study metadata/query information from the deduplicated data.
4. If any file was updated in step #3, then reschedule step #3 for later

## Gathering single instances
This process is triggered by a DICOM instance being added/received. 
It is possible to trigger this via a lambda function, or for this process
to be done outside the cloud as the starting point.

1. Extract large bulk data, storing to the hash directory structure
2. Replace it with a reference by hash, with offset/length information
   1. Alternate: replace with an offset reference to the DICOM file location
   2. In the DICOM file reference, still include the hashed value
3. Store the images to the frames and/or pixeldata area 
4. Extract duplicate information from the instance metadata, storing in bulkdata
5. Store the single instance deduplicated item, by hash

Note how this is thread safe provided a single DICOM instance is only added once,
as it only writes things relative to the instance.  Dealing with receiving
the same instance a second time can be managed by checking for existence of
the hash value - if it is present, it was already received.  Updating the SOP instance
can be detected at the same time, as a full list of received SOP instances is
available, and this can also be handled later.

## Creation of hash/grouped files
Triggered by creation of one of more instance level deduplicated files.  
The file are simply a set of all the deduplicated instance data, sorted and
hashed as a group and then written out as a JSON list.

This phase will eventually need to deal with thing such as QC and
various data update operations, including receiving the same SOP with different
data more than once.

The thread safety of this phase is handled by:
* Writing the deduplicated files named by hash value
* If any new deduplicated files were written, then re-scheduling this operation for time+T
* If the cluster operations are split, then scheduling a check for this study after recombining the cluster

## Study Metadata Generation
The deduplicated group files contain all the information required to re-create
the DICOMweb standard metadata files.  This is a simple recombination of the 
deduplicated files into their original JSON full metadata file, on a per-file
basis, and then writing the data out.  The hash value deduplicated data 
source should be stored along with the deduplicated instance data in order to
provide for concurrent updates.  The process is:
1. For each metadata or query file being written, read the current hash value of the deduplicated group it was generated from.
2. If the hash value is different, then write a new copy of the deduplicated file, AND schedule another study metadata check
3. If NO files were updated in this stage, then this stage is done, otherwise schedule another check for time +T

# Thread Safety of Retrieves
The retrieves are current-value retrieves for all data.  That is, there is no
guarantee that the data is up to date, as the guarantee is eventual consistency.
The entire file is read from storage, with storage level locking being used to
control availability of the file.  In the case where a file is being updated,
and has just been removed, it may be necessary on some file systems to attempt
a second retrieve after a short interval.

It is also possible to integrate the update checks from the study metadata
generation into the initial retrieves.  That would incur a delay while study
metadata files are written or updated, but that should be relatively short as
the deduplication provides the data to generate the study metadata in an easily 
accessible format.

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