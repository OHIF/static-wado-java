# Deploy StaticWado to Amazon AWS

This is a deployment script to deploy a static-wado server to Amazon AWS.  Various levels of deployment may
exist for different deployment setups, as configured by the ~/dicomweb.json deployment script.  The various parts of the deployment are:
1. S3 bucket for DICOMweb metadata and query files
2. DocumentDB database for patient and study searches
3. S3 bucket for deduplicated data (this is optional, it may also be a local file, defaulting to ~/dicomweb/deduplicated/)
4. S3 bucket for single-instance data, defaulting to local file directory ~/dicomweb/instances/
5. API Gateway configuration to access the lambda and s3 buckets
6. Permissions and authentication to access files
7. S3 bucket, forming an input queue of files to be processed by static-wado
8. A set of lambda services to provide the STOW services
9. A set of lambda services to provide the QIDO-RS query at the study level
10. Optionally a lambda service to provide rename functionality for some access paths

Each of these is discussed below.

## Useful commands

 * `npm run build`   compile typescript to js
 * `npm run watch`   watch for changes and compile
 * `npm run test`    perform the jest unit tests
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk synth`       emits the synthesized CloudFormation template
